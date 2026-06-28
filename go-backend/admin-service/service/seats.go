package service

import (
	"errors"
	"time"

	"github.com/google/uuid"
	"library/admin-service/model"
	"library/admin-service/repository"
)

type SeatAdminService struct {
	repo  *repository.Repo
	notif *NotifClient
}

func NewSeatAdminService(repo *repository.Repo, notif *NotifClient) *SeatAdminService {
	return &SeatAdminService{repo: repo, notif: notif}
}

func (s *SeatAdminService) GetSeatMap(shiftParam, dateParam string) (*model.SeatMapDTO, error) {
	shift := resolveShift(shiftParam)
	date := time.Now().Truncate(24 * time.Hour)
	if dateParam != "" {
		if d, err := time.Parse("2006-01-02", dateParam); err == nil {
			date = d
		}
	}
	dateStr := date.Format("2006-01-02")

	allSeats, err := s.repo.FindAllSeats()
	if err != nil {
		return nil, err
	}

	bookings, err := s.repo.FindActiveBookingsWithUser(shift, date)
	if err != nil {
		return nil, err
	}

	// Build seat-number → booking map
	type bookingInfo struct {
		studentName   string
		studentMobile *string
		studentGender *string
		shift         string
		endDate       time.Time
	}
	bookedMap := map[string]bookingInfo{}
	for _, b := range bookings {
		endDate := b.EndDate
		bookedMap[b.SeatNumber] = bookingInfo{
			studentName:   b.UserName,
			studentMobile: b.UserMobile,
			studentGender: b.UserGender,
			shift:         b.Shift,
			endDate:       endDate,
		}
	}

	seatsByRow := map[string][]model.SeatInfoDTO{
		"A": {}, "B": {}, "C": {}, "D": {},
	}

	occupied := 0
	for _, seat := range allSeats {
		info, booked := bookedMap[seat.SeatNumber]
		dto := model.SeatInfoDTO{
			SeatNumber: seat.SeatNumber,
			IsOccupied: booked,
		}
		if booked {
			occupied++
			dto.StudentName = &info.studentName
			dto.StudentMobile = info.studentMobile
			dto.StudentGender = info.studentGender
			sh := info.shift
			dto.Shift = &sh
			e := info.endDate.Format("2006-01-02")
			dto.MembershipEnd = &e
		}
		seatsByRow[seat.RowLabel] = append(seatsByRow[seat.RowLabel], dto)
	}

	total := len(allSeats)
	return &model.SeatMapDTO{
		Shift:          shift,
		Date:           dateStr,
		TotalSeats:     total,
		OccupiedSeats:  occupied,
		AvailableSeats: total - occupied,
		SeatsByRow:     seatsByRow,
	}, nil
}

func resolveShift(shift string) string {
	switch shift {
	case "MORNING", "EVENING":
		return shift
	default:
		return "FULL_DAY"
	}
}

func (s *SeatAdminService) CreateCashMembership(req model.CreateCashMembershipRequest) (*model.Membership, error) {
	student, err := s.repo.FindUserByID(req.StudentID)
	if err != nil || student == nil {
		return nil, errors.New("student not found")
	}
	plan, err := s.repo.FindPlanByID(req.PlanID)
	if err != nil || plan == nil {
		return nil, errors.New("plan not found")
	}

	seat, err := s.repo.FindSeatByNumber(req.SeatNumber)
	if err != nil || seat == nil {
		return nil, errors.New("seat not found")
	}

	start := time.Now().Truncate(24 * time.Hour)
	if req.StartDate != "" {
		if d, err := time.Parse("2006-01-02", req.StartDate); err == nil {
			start = d
		}
	}
	end := start.AddDate(0, 0, plan.DurationDays)

	shift := req.Shift
	if shift == "" {
		shift = "FULL_DAY"
	}

	conflict, err := s.repo.ConflictExists(seat.ID, shift, start, end)
	if err != nil {
		return nil, err
	}
	if conflict {
		return nil, errors.New("seat already booked for this period")
	}

	active, _ := s.repo.FindActiveMembershipByUserID(req.StudentID)
	status := "ACTIVE"
	if active != nil {
		status = "QUEUED"
	}

	seatID := seat.ID
	seatNum := seat.SeatNumber
	m := &model.Membership{
		UserID:     mustParseUUID(req.StudentID),
		PlanID:     plan.ID,
		SeatID:     &seatID,
		SeatNumber: &seatNum,
		Shift:      &shift,
		StartDate:  start,
		EndDate:    end,
		Status:     status,
	}
	if err := s.repo.SaveMembership(m); err != nil {
		return nil, err
	}

	// Record payment
	paidAmount := req.PaidAmount
	if paidAmount == 0 {
		paidAmount = plan.Price
	}
	p := &model.Payment{
		MembershipID:   m.ID,
		UserID:         mustParseUUID(req.StudentID),
		Amount:         paidAmount,
		PendingAmount:  req.PendingAmount,
		PaymentGateway: strPtr("CASH"),
		GatewayOrderID: strPtr("cash_" + m.ID.String()[:8]),
		Status:         "SUCCESS",
	}
	if err := s.repo.SavePayment(p); err != nil {
		return nil, err
	}

	// Book the seat
	if status == "ACTIVE" {
		booking := &model.SeatBooking{
			SeatID:       seat.ID,
			UserID:       mustParseUUID(req.StudentID),
			MembershipID: m.ID,
			Shift:        shift,
			BookingDate:  start,
			EndDate:      end,
			Status:       "ACTIVE",
		}
		s.repo.SaveSeatBooking(booking)
	}

	// Notify
	mobile := ""
	if student.Mobile != nil {
		mobile = *student.Mobile
	}
	email := ""
	if student.Email != nil {
		email = *student.Email
	}
	go s.notif.BookingConfirmed(
		student.ID.String(), m.ID.String(), student.Name,
		mobile, email, plan.Name, plan.PlanType,
		seatNum, shift, start.Format("2006-01-02"), end.Format("2006-01-02"),
		paidAmount,
	)

	return m, nil
}

func (s *SeatAdminService) ChangeSeat(membershipID, newSeatNumber string) error {
	m, err := s.repo.FindMembershipByID(membershipID)
	if err != nil || m == nil {
		return errors.New("membership not found")
	}

	newSeat, err := s.repo.FindSeatByNumber(newSeatNumber)
	if err != nil || newSeat == nil {
		return errors.New("seat not found")
	}

	shift := "FULL_DAY"
	if m.Shift != nil {
		shift = *m.Shift
	}

	conflict, err := s.repo.ConflictExists(newSeat.ID, shift, m.StartDate, m.EndDate)
	if err != nil {
		return err
	}
	if conflict {
		return errors.New("new seat is already booked")
	}

	// Release old booking
	s.repo.ReleaseSeatBookingsByMembership(membershipID)

	// Update membership
	newSeatID := newSeat.ID
	newSeatNum := newSeat.SeatNumber
	m.SeatID = &newSeatID
	m.SeatNumber = &newSeatNum
	s.repo.SaveMembership(m)

	// Create new booking
	booking := &model.SeatBooking{
		SeatID:       newSeat.ID,
		UserID:       m.UserID,
		MembershipID: m.ID,
		Shift:        shift,
		BookingDate:  m.StartDate,
		EndDate:      m.EndDate,
		Status:       "ACTIVE",
	}
	return s.repo.SaveSeatBooking(booking)
}

func (s *SeatAdminService) ChangeMembershipPlan(membershipID, planID string) error {
	mid, err := uuid.Parse(membershipID)
	if err != nil {
		return errors.New("invalid membership id")
	}
	pid, err := uuid.Parse(planID)
	if err != nil {
		return errors.New("invalid plan id")
	}
	plan, err := s.repo.FindPlanByID(pid.String())
	if err != nil || plan == nil {
		return errors.New("plan not found")
	}
	return s.repo.ChangeMembershipPlan(mid, pid)
}

func strPtr(s string) *string { return &s }

func mustParseUUID(s string) uuid.UUID {
	id, _ := uuid.Parse(s)
	return id
}
