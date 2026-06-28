package service

import (
	"encoding/csv"
	"fmt"
	"io"
	"mime/multipart"
	"path/filepath"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/xuri/excelize/v2"
	"library/admin-service/model"
	"library/admin-service/repository"
)

type ImportService struct {
	repo *repository.Repo
}

func NewImportService(repo *repository.Repo) *ImportService {
	return &ImportService{repo: repo}
}

var dateFormats = []string{
	"2006-01-02", "01/02/2006", "02/01/2006", "2-1-2006", "1-2-2006",
	"January 2, 2006", "Jan 2, 2006", "2 Jan 2006", "02-Jan-2006",
	"2006/01/02", "02.01.2006", "2.1.2006",
}

func parseDate(s string) *time.Time {
	s = strings.TrimSpace(s)
	for _, f := range dateFormats {
		if t, err := time.Parse(f, s); err == nil {
			return &t
		}
	}
	return nil
}

func (s *ImportService) ImportFile(file multipart.File, filename string) (*model.ImportResultDTO, error) {
	ext := strings.ToLower(filepath.Ext(filename))
	if ext == ".csv" {
		return s.importCSV(file)
	}
	return s.importXLSX(file)
}

func (s *ImportService) importCSV(r io.Reader) (*model.ImportResultDTO, error) {
	cr := csv.NewReader(r)
	cr.TrimLeadingSpace = true
	records, err := cr.ReadAll()
	if err != nil {
		return nil, err
	}
	if len(records) < 2 {
		return &model.ImportResultDTO{}, nil
	}
	// Skip header row
	return s.processRows(records[1:])
}

func (s *ImportService) importXLSX(r io.Reader) (*model.ImportResultDTO, error) {
	f, err := excelize.OpenReader(r)
	if err != nil {
		return nil, err
	}
	rows, err := f.GetRows(f.GetSheetName(0))
	if err != nil {
		return nil, err
	}
	if len(rows) < 2 {
		return &model.ImportResultDTO{}, nil
	}
	return s.processRows(rows[1:])
}

// Expected columns: Name, Phone/Mobile, Email, Gender, DateOfBirth, Address, FatherName
func (s *ImportService) processRows(rows [][]string) (*model.ImportResultDTO, error) {
	result := &model.ImportResultDTO{TotalRows: len(rows)}
	for i, row := range rows {
		if len(row) == 0 {
			continue
		}
		name := safe(row, 0)
		phone := normalizePhone(safe(row, 1))
		if name == "" || phone == "" {
			result.Errors = append(result.Errors, model.RowError{
				Row: i + 2, Name: name, Phone: phone, Reason: "name and phone required",
			})
			result.Skipped++
			continue
		}

		existing, _ := s.repo.FindUserByMobile(phone)
		if existing != nil {
			result.Skipped++
			continue
		}

		u := &model.User{
			ID:       uuid.New(),
			Name:     name,
			IsActive: true,
			Role:     "STUDENT",
		}
		u.Mobile = &phone
		if email := safe(row, 2); email != "" {
			u.Email = &email
		}
		if gender := safe(row, 3); gender != "" {
			u.Gender = &gender
		}
		if dob := parseDate(safe(row, 4)); dob != nil {
			u.DateOfBirth = dob
		}
		if addr := safe(row, 5); addr != "" {
			u.Address = &addr
		}
		if father := safe(row, 6); father != "" {
			u.FatherName = &father
		}

		if err := s.repo.SaveUser(u); err != nil {
			result.Errors = append(result.Errors, model.RowError{
				Row: i + 2, Name: name, Phone: phone, Reason: err.Error(),
			})
			result.Skipped++
			continue
		}
		result.Imported++
	}
	return result, nil
}

func safe(row []string, i int) string {
	if i < len(row) {
		return strings.TrimSpace(row[i])
	}
	return ""
}

func (s *ImportService) ImportSingleStudent(req model.ManualStudentImportRequest) error {
	digits := strings.Map(func(r rune) rune {
		if r >= '0' && r <= '9' {
			return r
		}
		return -1
	}, req.Phone)
	if digits == "" {
		return fmt.Errorf("invalid phone number")
	}
	existing, _ := s.repo.FindUserByMobile(digits)
	if existing != nil {
		return nil // already exists — idempotent
	}
	u := &model.User{
		ID:       uuid.New(),
		Name:     strings.TrimSpace(req.Name),
		Mobile:   &digits,
		Role:     "STUDENT",
		IsActive: true,
	}
	return s.repo.SaveUser(u)
}

func normalizePhone(phone string) string {
	var digits strings.Builder
	for _, c := range phone {
		if c >= '0' && c <= '9' {
			digits.WriteRune(c)
		}
	}
	d := digits.String()
	if len(d) == 10 {
		return "+91" + d
	}
	if len(d) == 12 && strings.HasPrefix(d, "91") {
		return "+" + d
	}
	return phone
}
