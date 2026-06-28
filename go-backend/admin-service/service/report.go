package service

import (
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/xuri/excelize/v2"
	"library/admin-service/model"
	"library/admin-service/repository"
)

type ReportService struct {
	repo *repository.Repo
}

func NewReportService(repo *repository.Repo) *ReportService {
	return &ReportService{repo: repo}
}

func (s *ReportService) GetRevenue(fromStr, toStr string) (*model.RevenueReportDTO, error) {
	from, _ := time.Parse("2006-01-02", fromStr)
	to, _ := time.Parse("2006-01-02", toStr)
	if to.IsZero() {
		to = time.Now()
	}
	to = to.Add(24*time.Hour - time.Second)

	payments, err := s.repo.FindSuccessPayments(from, to)
	if err != nil {
		return nil, err
	}

	daily := map[string]*model.DailyRevenueDTO{}
	total := 0.0
	for _, p := range payments {
		d := timeStr(p.CreatedAt, "2006-01-02")
		if _, ok := daily[d]; !ok {
			daily[d] = &model.DailyRevenueDTO{Date: d}
		}
		daily[d].Amount += p.Amount
		daily[d].Count++
		total += p.Amount
	}

	var breakdown []model.DailyRevenueDTO
	for d := from; !d.After(to); d = d.AddDate(0, 0, 1) {
		k := d.Format("2006-01-02")
		if v, ok := daily[k]; ok {
			breakdown = append(breakdown, *v)
		}
	}

	return &model.RevenueReportDTO{
		FromDate:          from.Format("2006-01-02"),
		ToDate:            to.Format("2006-01-02"),
		TotalRevenue:      total,
		TotalTransactions: len(payments),
		DailyBreakdown:    breakdown,
	}, nil
}

func (s *ReportService) ExportStudentsExcel(rows []repository.StudentRow) ([]byte, error) {
	f := excelize.NewFile()
	sheet := "Students"
	f.SetSheetName("Sheet1", sheet)

	headers := []string{
		"Name", "Mobile", "Email", "Gender", "Seat", "Shift",
		"Plan", "Membership Start", "Membership End", "Status",
		"Days Remaining", "Pending Amount", "Joined At",
	}
	for i, h := range headers {
		col, _ := excelize.ColumnNumberToName(i + 1)
		f.SetCellValue(sheet, col+"1", h)
	}

	today := time.Now().Truncate(24 * time.Hour)
	for ri, r := range rows {
		row := ri + 2
		setCell := func(col int, val interface{}) {
			c, _ := excelize.ColumnNumberToName(col)
			f.SetCellValue(sheet, fmt.Sprintf("%s%d", c, row), val)
		}
		setCell(1, r.Name)
		if r.Mobile != nil {
			setCell(2, *r.Mobile)
		}
		if r.Email != nil {
			setCell(3, *r.Email)
		}
		if r.Gender != nil {
			setCell(4, *r.Gender)
		}
		if r.SeatNumber != nil {
			setCell(5, *r.SeatNumber)
		}
		if r.Shift != nil {
			setCell(6, *r.Shift)
		}
		if r.PlanName != nil {
			setCell(7, *r.PlanName)
		}
		if r.StartDate != nil {
			setCell(8, r.StartDate.Format("2006-01-02"))
		}
		if r.EndDate != nil {
			setCell(9, r.EndDate.Format("2006-01-02"))
			days := int(r.EndDate.Sub(today).Hours() / 24)
			setCell(11, days)
		}
		if r.MembershipStatus != nil {
			setCell(10, *r.MembershipStatus)
		}
		if r.PendingAmount != nil {
			setCell(12, *r.PendingAmount)
		}
		setCell(13, timeStr(r.CreatedAt, "2006-01-02"))
	}

	buf, err := f.WriteToBuffer()
	if err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

func (s *ReportService) GetExpense(year, month int) (*model.ExpenseDTO, error) {
	e, items, err := s.repo.FindExpense(year, month)
	if err != nil {
		return nil, err
	}
	if e == nil {
		return &model.ExpenseDTO{Year: year, Month: month}, nil
	}
	miscItems := make([]model.MiscItemDTO, len(items))
	for i, it := range items {
		miscItems[i] = model.MiscItemDTO{Description: it.Description, Amount: it.Amount}
	}
	id := e.ID.String()
	total := float64(e.WaterTankerQty)*e.WaterTankerPrice + e.ElectricityBill + e.InternetBill + e.Miscellaneous
	return &model.ExpenseDTO{
		ID:               &id,
		Year:             e.Year,
		Month:            e.Month,
		WaterTankerQty:   e.WaterTankerQty,
		WaterTankerPrice: e.WaterTankerPrice,
		ElectricityBill:  e.ElectricityBill,
		InternetBill:     e.InternetBill,
		Miscellaneous:    e.Miscellaneous,
		TotalExpense:     total,
		MiscItems:        miscItems,
	}, nil
}

func (s *ReportService) SaveExpense(req model.SaveExpenseRequest) (*model.ExpenseDTO, error) {
	existingExpense, _, _ := s.repo.FindExpense(req.Year, req.Month)

	expense := &model.MonthlyExpense{
		Year:             req.Year,
		Month:            req.Month,
		WaterTankerQty:   req.WaterTankerQty,
		WaterTankerPrice: req.WaterTankerPrice,
		ElectricityBill:  req.ElectricityBill,
		InternetBill:     req.InternetBill,
	}
	if existingExpense != nil {
		expense.ID = existingExpense.ID
	} else {
		expense.ID = uuid.New()
	}

	items := make([]model.MiscExpenseItem, len(req.MiscItems))
	for i, it := range req.MiscItems {
		items[i] = model.MiscExpenseItem{Description: it.Description, Amount: it.Amount}
	}
	if err := s.repo.SaveExpense(expense, items); err != nil {
		return nil, err
	}
	return s.GetExpense(req.Year, req.Month)
}

func (s *ReportService) GetPaymentBreakdown(fromStr, toStr string) ([]model.PaymentBreakdownDTO, error) {
	from, _ := time.Parse("2006-01-02", fromStr)
	to, _ := time.Parse("2006-01-02", toStr)
	to = to.Add(24*time.Hour - time.Second)
	return s.repo.FindPaymentBreakdown(from, to)
}

func (s *ReportService) GetDailyPayments(dateStr string) ([]model.DailyPaymentDTO, error) {
	date, _ := time.Parse("2006-01-02", dateStr)
	to := date.Add(24*time.Hour - time.Second)
	return s.repo.FindPaymentsByDate(date, to)
}

func (s *ReportService) GetPendingFee() ([]model.StudentDTO, error) {
	rows, err := s.repo.FindPendingFeeStudents()
	if err != nil {
		return nil, err
	}
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
			dto.SeatNumber = r.SeatNumber
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
