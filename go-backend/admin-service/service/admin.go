package service

import (
	"fmt"
	"time"

	"github.com/google/uuid"
	"library/admin-service/model"
	"library/admin-service/repository"
)

type AdminService struct {
	repo  *repository.Repo
	notif *NotifClient
}

func NewAdminService(repo *repository.Repo, notif *NotifClient) *AdminService {
	return &AdminService{repo: repo, notif: notif}
}

func (s *AdminService) GetDashboard() (*model.DashboardDTO, error) {
	total, active, _ := s.repo.CountStudents()
	activeMem, expiredMem, expiringWeek, _ := s.repo.CountMemberships()
	totalSeats, occupied, _ := s.repo.CountSeats()
	revToday := s.repo.RevenueToday()
	revMonth, paymentsMonth := s.repo.RevenueThisMonth()
	totalVis, todayVis := s.repo.CountVisitors()

	return &model.DashboardDTO{
		TotalStudents:      total,
		ActiveStudents:     active,
		ActiveMemberships:  activeMem,
		ExpiredMemberships: expiredMem,
		ExpiringThisWeek:   expiringWeek,
		TotalSeats:         totalSeats,
		OccupiedSeats:      occupied,
		AvailableSeats:     totalSeats - occupied,
		RevenueToday:       revToday,
		RevenueThisMonth:   revMonth,
		PaymentsThisMonth:  paymentsMonth,
		TotalVisitors:      totalVis,
		VisitorsToday:      todayVis,
	}, nil
}

func (s *AdminService) TrackVisitor(page string) {
	s.repo.TrackVisitor(page)
}

func (s *AdminService) ListStudents(search string, page, size int) (*model.StudentListDTO, error) {
	if page < 1 {
		page = 1
	}
	if size < 1 || size > 100 {
		size = 20
	}
	rows, total, err := s.repo.FindStudents(search, page, size)
	if err != nil {
		return nil, err
	}

	dtos := make([]model.StudentDTO, len(rows))
	today := time.Now().Truncate(24 * time.Hour)
	for i, r := range rows {
		dto := model.StudentDTO{
			ID:       r.ID.String(),
			Name:     r.Name,
			Mobile:   r.Mobile,
			Email:    r.Email,
			Address:  r.Address,
			Gender:   r.Gender,
			IsActive: r.IsActive,
			JoinedAt: timeStr(r.CreatedAt, "2006-01-02"),
			PhotoURL: r.PhotoURL,
		}
		if r.DateOfBirth != nil {
			dob := r.DateOfBirth.Format("2006-01-02")
			dto.DateOfBirth = &dob
		}
		if r.MembershipID != nil {
			mid := r.MembershipID.String()
			dto.MembershipID = &mid
			dto.PlanName = r.PlanName
			dto.SeatNumber = r.SeatNumber
			dto.Shift = r.Shift
			if r.MembershipStatus != nil {
				dto.MembershipStatus = r.MembershipStatus
			}
			if r.StartDate != nil {
				s := r.StartDate.Format("2006-01-02")
				dto.MembershipStart = &s
			}
			if r.EndDate != nil {
				e := r.EndDate.Format("2006-01-02")
				dto.MembershipEnd = &e
				days := int(r.EndDate.Sub(today).Hours() / 24)
				dto.DaysRemaining = &days
			}
			if r.PendingAmount != nil {
				dto.PendingAmount = *r.PendingAmount
			}
			dto.PaymentMode = r.PaymentGateway
		}
		dtos[i] = dto
	}

	totalPages := (total + size - 1) / size
	return &model.StudentListDTO{Students: dtos, Total: total, Page: page, Size: size, TotalPages: totalPages}, nil
}

func (s *AdminService) GetStudent(id string) (*model.StudentDTO, error) {
	rows, _, err := s.repo.FindStudents("", 1, 1000)
	if err != nil {
		return nil, err
	}
	for _, r := range rows {
		if r.ID.String() == id {
			dtos, _ := s.rowsToStudentDTOs([]repository.StudentRow{r})
			return &dtos[0], nil
		}
	}
	return nil, fmt.Errorf("student not found")
}

func (s *AdminService) rowsToStudentDTOs(rows []repository.StudentRow) ([]model.StudentDTO, error) {
	today := time.Now().Truncate(24 * time.Hour)
	dtos := make([]model.StudentDTO, len(rows))
	for i, r := range rows {
		dto := model.StudentDTO{
			ID:       r.ID.String(),
			Name:     r.Name,
			Mobile:   r.Mobile,
			Email:    r.Email,
			IsActive: r.IsActive,
			JoinedAt: timeStr(r.CreatedAt, "2006-01-02"),
		}
		if r.MembershipID != nil {
			mid := r.MembershipID.String()
			dto.MembershipID = &mid
			dto.PlanName = r.PlanName
			dto.SeatNumber = r.SeatNumber
			dto.Shift = r.Shift
			if r.EndDate != nil {
				e := r.EndDate.Format("2006-01-02")
				dto.MembershipEnd = &e
				days := int(r.EndDate.Sub(today).Hours() / 24)
				dto.DaysRemaining = &days
			}
			if r.PendingAmount != nil {
				dto.PendingAmount = *r.PendingAmount
			}
		}
		dtos[i] = dto
	}
	return dtos, nil
}

func (s *AdminService) UpdateStudentStatus(id string, active bool) error {
	return s.repo.UpdateUserActive(id, active)
}

func (s *AdminService) UpdateStudentFields(id string, req model.UpdateStudentRequest) error {
	fields := map[string]interface{}{}
	if req.Name != nil {
		fields["name"] = *req.Name
	}
	if req.Mobile != nil {
		fields["mobile"] = *req.Mobile
	}
	if req.Email != nil {
		fields["email"] = *req.Email
	}
	if req.Address != nil {
		fields["address"] = *req.Address
	}
	if req.Gender != nil {
		fields["gender"] = *req.Gender
	}
	if req.DateOfBirth != nil {
		t, err := time.Parse("2006-01-02", *req.DateOfBirth)
		if err == nil {
			fields["date_of_birth"] = t
		}
	}
	if req.JoinedAt != nil {
		t, err := time.Parse("2006-01-02", *req.JoinedAt)
		if err == nil {
			fields["created_at"] = t
		}
	}
	return s.repo.UpdateUserFields(id, fields)
}

func (s *AdminService) DeleteStudent(id string) error {
	uid, err := uuid.Parse(id)
	if err != nil {
		return fmt.Errorf("invalid id")
	}
	return s.repo.DeleteStudent(uid)
}

func (s *AdminService) GetExpiringMemberships(withinDays int) ([]model.StudentDTO, error) {
	rows, err := s.repo.FindExpiringMemberships(withinDays)
	if err != nil {
		return nil, err
	}
	return s.rowsToStudentDTOs(rows)
}

func (s *AdminService) ClearPendingFees(id string) error {
	uid, err := uuid.Parse(id)
	if err != nil {
		return fmt.Errorf("invalid id")
	}
	return s.repo.ClearPendingFees(uid)
}

func (s *AdminService) SendPendingFeeReminders(userIDs []string) (int, error) {
	var rows []repository.StudentRow
	var err error
	if len(userIDs) == 0 {
		rows, err = s.repo.FindPendingFeeStudents()
	} else {
		for _, id := range userIDs {
			r, e := s.repo.FindUserByID(id)
			if e != nil || r == nil {
				continue
			}
			rows = append(rows, repository.StudentRow{User: *r})
		}
	}
	if err != nil {
		return 0, err
	}
	sent := 0
	for _, r := range rows {
		if r.Mobile == nil {
			continue
		}
		pa := 0.0
		if r.PendingAmount != nil {
			pa = *r.PendingAmount
		}
		if pa <= 0 {
			continue
		}
		mobile := *r.Mobile
		name := r.Name
		paCopy := pa
		go s.notif.RenewalReminder(r.ID.String(), "", name, mobile, "", "", "", 0, "PENDING_FEE_REMINDER", &paCopy)
		sent++
	}
	return sent, nil
}

func (s *AdminService) SendDirectMessage(userID string, message string) error {
	u, err := s.repo.FindUserByID(userID)
	if err != nil || u == nil {
		return fmt.Errorf("student not found")
	}
	if u.Mobile == nil {
		return fmt.Errorf("student has no mobile")
	}
	go s.notif.Broadcast(u.ID.String(), *u.Mobile, message, false)
	return nil
}

func timeStr(t *time.Time, layout string) string {
	if t == nil {
		return ""
	}
	return t.Format(layout)
}

func strVal(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

func (s *AdminService) GetStudentPayments(id string) ([]model.PaymentHistoryDTO, error) {
	payments, err := s.repo.FindPaymentsByStudentID(id)
	if err != nil {
		return nil, err
	}
	dtos := make([]model.PaymentHistoryDTO, len(payments))
	for i, p := range payments {
		dtos[i] = model.PaymentHistoryDTO{
			ID:             p.ID.String(),
			Amount:         p.Amount,
			PendingAmount:  p.PendingAmount,
			PaymentGateway: strVal(p.PaymentGateway),
			Status:         p.Status,
			CreatedAt:      timeStr(p.CreatedAt, time.RFC3339),
		}
	}
	return dtos, nil
}
