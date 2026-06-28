package service

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"strconv"
	"time"

	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"
	"library/seat-service/model"
	"library/seat-service/repository"
)

type SeatService struct {
	repo     *repository.Repo
	redis    *redis.Client
	cacheTTL time.Duration
}

func NewSeatService(repo *repository.Repo, rdb *redis.Client) *SeatService {
	ttl := 300
	if v := os.Getenv("SEAT_CACHE_TTL_SECONDS"); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			ttl = n
		}
	}
	return &SeatService{repo: repo, redis: rdb, cacheTTL: time.Duration(ttl) * time.Second}
}

func resolveShift(shift string) string {
	switch shift {
	case "MORNING", "EVENING":
		return shift
	default:
		return "FULL_DAY"
	}
}

func (s *SeatService) GetAvailability(shiftParam, dateParam string) (*model.SeatAvailabilityDTO, error) {
	shift := resolveShift(shiftParam)
	date := time.Now().Truncate(24 * time.Hour)
	if dateParam != "" {
		if d, err := time.Parse("2006-01-02", dateParam); err == nil {
			date = d
		}
	}
	dateStr := date.Format("2006-01-02")
	cacheKey := fmt.Sprintf("seats:availability:%s:%s", shift, dateStr)

	ctx := context.Background()

	// Cache hit
	if cached, err := s.redis.Get(ctx, cacheKey).Result(); err == nil {
		var dto model.SeatAvailabilityDTO
		if json.Unmarshal([]byte(cached), &dto) == nil {
			return &dto, nil
		}
	}

	// Cache miss — build from DB
	allSeats, err := s.repo.FindAllSeats()
	if err != nil {
		return nil, err
	}
	bookedIDs, err := s.repo.FindBookedSeatIDs(shift, date)
	if err != nil {
		return nil, err
	}
	bookedSet := make(map[uuid.UUID]bool)
	for _, id := range bookedIDs {
		bookedSet[id] = true
	}

	rows := map[string][]model.SeatDTO{"A": {}, "B": {}, "C": {}, "D": {}}
	var seats []model.SeatDTO
	booked := 0
	for _, seat := range allSeats {
		if !seat.IsActive {
			continue
		}
		isBooked := bookedSet[seat.ID]
		if isBooked {
			booked++
		}
		dto := model.SeatDTO{
			ID:         seat.ID.String(),
			SeatNumber: seat.SeatNumber,
			RowLabel:   seat.RowLabel,
			IsBooked:   isBooked,
		}
		rows[seat.RowLabel] = append(rows[seat.RowLabel], dto)
		seats = append(seats, dto)
	}

	total := 108 // 110 seats minus 2 blocked
	result := &model.SeatAvailabilityDTO{
		Shift:      shift,
		Date:       dateStr,
		TotalSeats: total,
		Booked:     booked,
		Available:  total - booked,
		Seats:      seats,
		Rows:       rows,
	}

	if b, err := json.Marshal(result); err == nil {
		s.redis.Set(ctx, cacheKey, string(b), s.cacheTTL)
	}
	return result, nil
}

func (s *SeatService) BookSeat(userID string, req model.BookSeatRequest) (*model.SeatBookingDTO, error) {
	seat, err := s.repo.FindSeatByNumber(req.SeatNumber)
	if err != nil {
		return nil, err
	}
	if seat == nil || !seat.IsActive {
		return nil, errors.New("seat not found or not available")
	}

	shift := resolveShift(req.Shift)

	start := time.Now().Truncate(24 * time.Hour)
	if req.StartDate != "" {
		if d, err := time.Parse("2006-01-02", req.StartDate); err == nil {
			start = d
		}
	}
	end := start.AddDate(0, 1, 0)
	if req.EndDate != "" {
		if d, err := time.Parse("2006-01-02", req.EndDate); err == nil {
			end = d
		}
	}

	conflict, err := s.repo.ConflictExists(seat.ID, shift, start, end)
	if err != nil {
		return nil, err
	}
	if conflict {
		return nil, errors.New("seat already booked for this period and shift")
	}

	membershipUID, err := uuid.Parse(req.MembershipID)
	if err != nil {
		return nil, errors.New("invalid membership ID")
	}
	userUID, _ := uuid.Parse(userID)

	booking := &model.SeatBooking{
		SeatID:       seat.ID,
		UserID:       userUID,
		MembershipID: membershipUID,
		Shift:        shift,
		BookingDate:  start,
		EndDate:      end,
		Status:       "ACTIVE",
	}
	if err := s.repo.SaveBooking(booking); err != nil {
		return nil, err
	}

	s.invalidateCache(shift, start, end)

	return &model.SeatBookingDTO{
		ID:           booking.ID.String(),
		SeatNumber:   seat.SeatNumber,
		MembershipID: req.MembershipID,
		UserID:       userID,
		Shift:        shift,
		StartDate:    start.Format("2006-01-02"),
		EndDate:      end.Format("2006-01-02"),
		Status:       "ACTIVE",
	}, nil
}

func (s *SeatService) ReleaseSeat(membershipID string) error {
	booking, err := s.repo.FindBookingByMembershipID(membershipID)
	if err != nil || booking == nil {
		return errors.New("active booking not found")
	}
	booking.Status = "RELEASED"
	if err := s.repo.SaveBooking(booking); err != nil {
		return err
	}
	s.invalidateCache(booking.Shift, booking.BookingDate, booking.EndDate)
	return nil
}

func (s *SeatService) GetMyBookings(userID string) ([]model.SeatBookingDTO, error) {
	items, err := s.repo.FindBookingsByUserID(userID)
	if err != nil {
		return nil, err
	}
	dtos := make([]model.SeatBookingDTO, len(items))
	for i, b := range items {
		dtos[i] = model.SeatBookingDTO{
			ID:           b.ID.String(),
			MembershipID: b.MembershipID.String(),
			UserID:       b.UserID.String(),
			Shift:        b.Shift,
			StartDate:    b.BookingDate.Format("2006-01-02"),
			EndDate:      b.EndDate.Format("2006-01-02"),
			Status:       b.Status,
		}
	}
	return dtos, nil
}

func (s *SeatService) invalidateCache(shift string, start, end time.Time) {
	ctx := context.Background()
	for d := start; !d.After(end); d = d.AddDate(0, 0, 1) {
		dateStr := d.Format("2006-01-02")
		keys := []string{fmt.Sprintf("seats:availability:%s:%s", shift, dateStr)}
		// FULL_DAY bust also invalidates MORNING and EVENING; MORNING/EVENING also bust FULL_DAY
		switch shift {
		case "FULL_DAY":
			keys = append(keys,
				fmt.Sprintf("seats:availability:MORNING:%s", dateStr),
				fmt.Sprintf("seats:availability:EVENING:%s", dateStr))
		default:
			keys = append(keys, fmt.Sprintf("seats:availability:FULL_DAY:%s", dateStr))
		}
		s.redis.Del(ctx, keys...)
	}
}
