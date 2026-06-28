package service

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/jung-kurt/gofpdf"
	qrcode "github.com/skip2/go-qrcode"
	"library/membership-service/model"
	"library/membership-service/repository"
)

type IDCardService struct {
	repo       *repository.Repo
	userSvcURL string
}

func NewIDCardService(repo *repository.Repo) *IDCardService {
	return &IDCardService{
		repo:       repo,
		userSvcURL: getenv("USER_SERVICE_URL", "http://localhost:8082"),
	}
}

func (s *IDCardService) GeneratePDF(userID string) ([]byte, error) {
	m, err := s.repo.FindActiveMembership(userID)
	if err != nil || m == nil {
		return nil, fmt.Errorf("no active membership found")
	}

	plan, _ := s.repo.FindPlanByID(m.PlanID.String())

	user := s.fetchUser(userID)
	if user == nil {
		user = &model.UserProfileDTO{ID: userID, Name: "Member"}
	}

	var photoBytes []byte
	if user.PhotoURL != nil && *user.PhotoURL != "" {
		photoBytes, _ = s.fetchPhoto(s.userSvcURL + *user.PhotoURL)
	}

	mDTO := toMembershipDTO(m, plan)
	return buildPDF(user, mDTO, photoBytes)
}

func buildPDF(user *model.UserProfileDTO, m *model.MembershipDTO, photoBytes []byte) ([]byte, error) {
	// Page: 300 × 200 pt, gofpdf top-left origin (Y increases downward)
	// iText used bottom-left — all Y values inverted: gofpdfY = 200 - iTextY
	pdf := gofpdf.NewCustom(&gofpdf.InitType{
		UnitStr: "pt",
		Size:    gofpdf.SizeType{Wd: 300, Ht: 200},
	})
	pdf.AddPage()
	pdf.SetAutoPageBreak(false, 0)

	// White background
	pdf.SetFillColor(255, 255, 255)
	pdf.Rect(0, 0, 300, 200, "F")

	// Black border
	pdf.SetDrawColor(0, 0, 0)
	pdf.SetLineWidth(0.5)
	pdf.Rect(2, 2, 296, 196, "D")

	// Black header bar (iText: y=168..196 → gofpdf: y=4..32)
	pdf.SetFillColor(0, 0, 0)
	pdf.Rect(2, 4, 296, 28, "F")

	// Header text
	pdf.SetFont("Helvetica", "B", 11)
	pdf.SetTextColor(255, 255, 255)
	pdf.SetXY(0, 4)
	pdf.CellFormat(300, 28, "TARGET ZONE LIBRARY", "", 0, "C", false, 0, "")

	// Field rows — left side, starting y=36
	pdf.SetFont("Helvetica", "", 7)
	fields := buildFields(user, m)
	y := 38.0
	labelW := 55.0
	valueW := 120.0
	for _, f := range fields {
		pdf.SetTextColor(80, 80, 80)
		pdf.SetXY(6, y)
		pdf.CellFormat(labelW, 10, f[0]+":", "", 0, "L", false, 0, "")
		pdf.SetTextColor(0, 0, 0)
		pdf.SetXY(6+labelW, y)
		pdf.CellFormat(valueW, 10, f[1], "", 0, "L", false, 0, "")
		y += 11
	}

	// Photo box: right side, x=225, y=37, w=65, h=65
	photoX, photoY := 225.0, 37.0
	photoW, photoH := 65.0, 65.0
	if len(photoBytes) > 0 {
		opts := gofpdf.ImageOptions{ImageType: "JPEG", ReadDpi: false}
		pdf.RegisterImageOptionsReader("photo", opts, bytes.NewReader(photoBytes))
		pdf.ImageOptions("photo", photoX, photoY, photoW, photoH, false, opts, 0, "")
	} else {
		pdf.SetFillColor(200, 200, 200)
		pdf.Rect(photoX, photoY, photoW, photoH, "F")
		pdf.SetTextColor(100, 100, 100)
		pdf.SetFont("Helvetica", "", 7)
		pdf.SetXY(photoX, photoY+photoH/2-5)
		pdf.CellFormat(photoW, 10, "No Photo", "", 0, "C", false, 0, "")
	}

	// QR code: below photo, x=225, y=106, w=65, h=65
	qrData := buildQRData(user, m)
	qrBytes, err := qrcode.Encode(qrData, qrcode.Medium, 256)
	if err == nil {
		opts := gofpdf.ImageOptions{ImageType: "PNG"}
		pdf.RegisterImageOptionsReader("qr", opts, bytes.NewReader(qrBytes))
		pdf.ImageOptions("qr", 225, 106, 65, 65, false, opts, 0, "")
	}

	// Footer
	pdf.SetFont("Helvetica", "I", 6)
	pdf.SetTextColor(100, 100, 100)
	pdf.SetXY(6, 185)
	pdf.CellFormat(200, 8, fmt.Sprintf("Issued: %s  |  Non-transferable", time.Now().Format("02 Jan 2006")), "", 0, "L", false, 0, "")

	var buf bytes.Buffer
	if err := pdf.Output(&buf); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

func buildFields(user *model.UserProfileDTO, m *model.MembershipDTO) [][2]string {
	father := ""
	if user.FatherName != nil {
		father = *user.FatherName
	}
	mobile := ""
	if user.Mobile != nil {
		mobile = *user.Mobile
	}
	age := ""
	if user.DateOfBirth != nil {
		if dob, err := time.Parse("2006-01-02", *user.DateOfBirth); err == nil {
			age = fmt.Sprintf("%d yrs", int(time.Since(dob).Hours()/8760))
		}
	}
	shift := ""
	if m.Shift != nil {
		shift = formatShift(*m.Shift)
	}
	seat := ""
	if m.SeatNumber != nil {
		seat = *m.SeatNumber
	}

	return [][2]string{
		{"Name", user.Name},
		{"Father", father},
		{"Age", age},
		{"Seat", seat},
		{"Shift", shift},
		{"Phone", mobile},
		{"Valid Till", m.EndDate},
	}
}

func buildQRData(user *model.UserProfileDTO, m *model.MembershipDTO) string {
	father := ""
	if user.FatherName != nil {
		father = *user.FatherName
	}
	mobile := ""
	if user.Mobile != nil {
		mobile = *user.Mobile
	}
	shift := ""
	if m.Shift != nil {
		shift = *m.Shift
	}
	seat := ""
	if m.SeatNumber != nil {
		seat = *m.SeatNumber
	}
	memberID := strings.ToUpper(m.ID[:8])
	return fmt.Sprintf("Name: %s\nFather: %s\nPhone: %s\nShift: %s\nSeat: %s\nValid Till: %s\nMember ID: %s",
		user.Name, father, mobile, shift, seat, m.EndDate, memberID)
}

func formatShift(shift string) string {
	switch shift {
	case "MORNING":
		return "Morning (6AM-2PM)"
	case "EVENING":
		return "Evening (2PM-10PM)"
	default:
		return "Full Day (6AM-10PM)"
	}
}

func (s *IDCardService) fetchUser(userID string) *model.UserProfileDTO {
	req, _ := http.NewRequest("GET", s.userSvcURL+"/api/users/"+userID, nil)
	req.Header.Set("X-User-Id", userID)
	req.Header.Set("X-User-Role", "STUDENT")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil
	}
	defer resp.Body.Close()
	var res struct {
		Data model.UserProfileDTO `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&res); err != nil {
		return nil
	}
	return &res.Data
}

func (s *IDCardService) fetchPhoto(url string) ([]byte, error) {
	resp, err := http.Get(url)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	return io.ReadAll(resp.Body)
}
