package service

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"math/rand"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/google/uuid"
	"library/membership-service/model"
	"library/membership-service/repository"
)

type PaymentService struct {
	repo         *repository.Repo
	notif        *NotifClient
	userSvcURL   string
	gateway      string
	cfAppID      string
	cfSecret     string
	cfEnv        string
	rzpKeyID     string
	rzpKeySecret string
}

func NewPaymentService(repo *repository.Repo, notif *NotifClient) *PaymentService {
	return &PaymentService{
		repo:         repo,
		notif:        notif,
		userSvcURL:   getenv("USER_SERVICE_URL", "http://localhost:8082"),
		gateway:      getenv("PAYMENT_GATEWAY", "CASHFREE"),
		cfAppID:      os.Getenv("CASHFREE_APP_ID"),
		cfSecret:     os.Getenv("CASHFREE_SECRET_KEY"),
		cfEnv:        getenv("CASHFREE_ENV", "sandbox"),
		rzpKeyID:     os.Getenv("RAZORPAY_KEY_ID"),
		rzpKeySecret: os.Getenv("RAZORPAY_KEY_SECRET"),
	}
}

func (s *PaymentService) CreateOrder(userID string, req model.CreateOrderRequest) (*model.CreateOrderResponse, error) {
	plan, err := s.repo.FindPlanByID(req.PlanID)
	if err != nil || plan == nil {
		return nil, errors.New("plan not found")
	}

	shift := req.Shift
	if plan.PlanType == "FULL_DAY" {
		shift = "FULL_DAY"
	} else {
		if shift != "MORNING" && shift != "EVENING" {
			return nil, errors.New("shift must be MORNING or EVENING for half-day plans")
		}
	}

	// Determine start date
	startDate := time.Now().Truncate(24 * time.Hour)
	active, _ := s.repo.FindActiveMembership(userID)
	if active != nil && active.EndDate.After(startDate) {
		startDate = active.EndDate.AddDate(0, 0, 1)
		// Inherit seat/shift from active
		if shift == "" && active.Shift != nil {
			shift = *active.Shift
		}
	}

	queued, _ := s.repo.FindQueuedMembership(userID)
	if queued != nil {
		return nil, errors.New("you already have a queued membership")
	}

	endDate := startDate.AddDate(0, 0, plan.DurationDays)

	// Create PENDING membership
	uid, _ := uuid.Parse(userID)
	planUID := plan.ID
	m := &model.Membership{
		UserID:    uid,
		PlanID:    planUID,
		Shift:     &shift,
		StartDate: startDate,
		EndDate:   endDate,
		Status:    "PENDING",
	}

	// Seat from request (user selected a seat on the booking page)
	if req.SeatNumber != "" {
		m.SeatNumber = &req.SeatNumber
	}
	if req.SeatID != "" {
		if sid, err := uuid.Parse(req.SeatID); err == nil {
			m.SeatID = &sid
		}
	}

	// Inherit seat from active if queuing and none specified
	if active != nil && active.SeatID != nil && m.SeatID == nil {
		m.SeatID = active.SeatID
		m.SeatNumber = active.SeatNumber
	}

	if err := s.repo.SaveMembership(m); err != nil {
		return nil, err
	}

	// Create gateway order
	var orderID, sessionID string
	if s.gateway == "RAZORPAY" {
		orderID, err = s.createRazorpayOrder(plan.Price, m.ID)
	} else {
		uc := s.repo.FindUserContact(userID)
		orderID, sessionID, err = s.createCashfreeOrder(plan.Price, m, userID, uc)
	}
	if err != nil {
		log.Printf("[warn] gateway order creation failed: %v — using dev mode", err)
		orderID = "dev_order_" + randHex(8)
	}

	// Save PENDING payment
	p := &model.Payment{
		MembershipID:   m.ID,
		UserID:         uid,
		Amount:         plan.Price,
		PaymentGateway: s.gateway,
		GatewayOrderID: orderID,
		Status:         "PENDING",
	}
	if err := s.repo.SavePayment(p); err != nil {
		return nil, err
	}

	resp := &model.CreateOrderResponse{
		OrderID:      orderID,
		MembershipID: m.ID.String(),
		Amount:       plan.Price,
		Currency:     "INR",
		Gateway:      s.gateway,
	}
	if sessionID != "" {
		resp.PaymentSessionID = &sessionID
	}
	if s.gateway == "RAZORPAY" && s.rzpKeyID != "" {
		resp.RazorpayKeyID = &s.rzpKeyID
	}
	return resp, nil
}

func (s *PaymentService) VerifyAndActivate(userID string, req model.PaymentVerifyRequest) (*model.MembershipDTO, error) {
	payment, err := s.repo.FindPaymentByOrderID(req.OrderID)
	if err != nil || payment == nil {
		return nil, errors.New("payment not found")
	}

	// Skip verification in dev mode
	isDev := strings.HasPrefix(req.OrderID, "dev_")

	if !isDev {
		if s.gateway == "RAZORPAY" && s.rzpKeySecret != "" {
			if !verifyRazorpayHMAC(req.OrderID, req.PaymentID, req.Signature, s.rzpKeySecret) {
				return nil, errors.New("payment signature verification failed")
			}
		} else if s.gateway == "CASHFREE" && s.cfSecret != "" {
			if err := s.verifyCashfreeOrder(req.OrderID); err != nil {
				return nil, fmt.Errorf("cashfree verification failed: %w", err)
			}
		}
	}

	// Update payment
	if req.PaymentID != "" {
		payment.GatewayPaymentID = &req.PaymentID
	}
	payment.Status = "SUCCESS"
	if err := s.repo.SavePayment(payment); err != nil {
		return nil, err
	}

	// Activate or queue membership
	m, err := s.repo.FindMembershipByID(payment.MembershipID.String())
	if err != nil || m == nil {
		return nil, errors.New("membership not found")
	}

	today := time.Now().Truncate(24 * time.Hour)
	newStatus := "ACTIVE"
	if m.StartDate.After(today) {
		newStatus = "QUEUED"
	}
	m.Status = newStatus
	s.repo.SaveMembership(m)

	// Fire notification (best-effort)
	plan, _ := s.repo.FindPlanByID(m.PlanID.String())
	user := s.fetchUser(userID)
	if plan != nil && user != nil {
		seat := ""
		if m.SeatNumber != nil {
			seat = *m.SeatNumber
		}
		shift := ""
		if m.Shift != nil {
			shift = *m.Shift
		}
		mobile := ""
		if user.Mobile != nil {
			mobile = *user.Mobile
		}
		email := ""
		if user.Email != nil {
			email = *user.Email
		}
		go s.notif.BookingConfirmed(
			userID, m.ID.String(), user.Name, mobile, email,
			plan.Name, plan.PlanType, seat, shift,
			m.StartDate.Format("2006-01-02"), m.EndDate.Format("2006-01-02"),
			payment.Amount,
		)
	}

	return toMembershipDTO(m, plan), nil
}

func (s *PaymentService) CallAdmin(userID string) error {
	user := s.fetchUser(userID)
	if user == nil {
		return errors.New("user not found")
	}
	m, _ := s.repo.FindActiveMembership(userID)
	seat := ""
	if m != nil && m.SeatNumber != nil {
		seat = *m.SeatNumber
	}
	go s.notif.SeatAssistance(userID, user.Name, seat, "")
	return nil
}

// Gateway helpers

func (s *PaymentService) createRazorpayOrder(amount float64, membershipID uuid.UUID) (string, error) {
	if s.rzpKeyID == "" {
		return "dev_order_" + randHex(8), nil
	}
	body := map[string]interface{}{
		"amount":   int(amount * 100),
		"currency": "INR",
		"receipt":  "lib_" + membershipID.String()[:8],
	}
	b, _ := json.Marshal(body)
	req, _ := http.NewRequest("POST", "https://api.razorpay.com/v1/orders", bytes.NewReader(b))
	req.SetBasicAuth(s.rzpKeyID, s.rzpKeySecret)
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	var res map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&res)
	if id, ok := res["id"].(string); ok {
		return id, nil
	}
	return "", fmt.Errorf("razorpay order creation failed: %v", res)
}

func (s *PaymentService) createCashfreeOrder(amount float64, m *model.Membership, userID string, uc repository.UserContact) (orderID, sessionID string, err error) {
	if s.cfAppID == "" {
		return "dev_order_" + randHex(8), "", nil
	}
	baseURL := "https://sandbox.cashfree.com"
	if s.cfEnv == "production" {
		baseURL = "https://api.cashfree.com"
	}
	phone := uc.Mobile
	if phone == "" {
		phone = "9999999999"
	}
	phone = strings.TrimPrefix(phone, "+91")
	if len(phone) > 10 {
		phone = phone[len(phone)-10:]
	}
	name := uc.Name
	if name == "" {
		name = "Library Student"
	}
	orderID = "lib_" + m.ID.String()[:8] + "_" + randHex(4)
	customerDetails := map[string]string{
		"customer_id":    userID,
		"customer_name":  name,
		"customer_phone": phone,
	}
	if uc.Email != "" {
		customerDetails["customer_email"] = uc.Email
	}
	body := map[string]interface{}{
		"order_id":         orderID,
		"order_amount":     amount,
		"order_currency":   "INR",
		"customer_details": customerDetails,
	}
	b, _ := json.Marshal(body)
	req, _ := http.NewRequest("POST", baseURL+"/pg/orders", bytes.NewReader(b))
	req.Header.Set("x-client-id", s.cfAppID)
	req.Header.Set("x-client-secret", s.cfSecret)
	req.Header.Set("x-api-version", "2025-01-01")
	req.Header.Set("Content-Type", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", "", err
	}
	defer resp.Body.Close()
	rb, _ := io.ReadAll(resp.Body)
	var res map[string]interface{}
	json.Unmarshal(rb, &res)
	sid, ok := res["payment_session_id"].(string)
	if !ok || sid == "" {
		return "", "", fmt.Errorf("cashfree order creation failed: %s", string(rb))
	}
	return orderID, sid, nil
}

func (s *PaymentService) verifyCashfreeOrder(orderID string) error {
	baseURL := "https://sandbox.cashfree.com"
	if s.cfEnv == "production" {
		baseURL = "https://api.cashfree.com"
	}
	req, _ := http.NewRequest("GET", fmt.Sprintf("%s/pg/orders/%s", baseURL, orderID), nil)
	req.Header.Set("x-client-id", s.cfAppID)
	req.Header.Set("x-client-secret", s.cfSecret)
	req.Header.Set("x-api-version", "2025-01-01")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	var res map[string]interface{}
	json.NewDecoder(resp.Body).Decode(&res)
	if status, ok := res["order_status"].(string); ok && status == "PAID" {
		return nil
	}
	return errors.New("payment not completed")
}

func verifyRazorpayHMAC(orderID, paymentID, signature, secret string) bool {
	data := orderID + "|" + paymentID
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write([]byte(data))
	expected := hex.EncodeToString(mac.Sum(nil))
	return hmac.Equal([]byte(expected), []byte(signature))
}

// User fetch

func (s *PaymentService) fetchUser(userID string) *model.UserProfileDTO {
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
	json.NewDecoder(resp.Body).Decode(&res)
	return &res.Data
}

func toMembershipDTO(m *model.Membership, plan *model.Plan) *model.MembershipDTO {
	dto := &model.MembershipDTO{
		ID:         m.ID.String(),
		UserID:     m.UserID.String(),
		PlanID:     m.PlanID.String(),
		SeatNumber: m.SeatNumber,
		Shift:      m.Shift,
		StartDate:  m.StartDate.Format("2006-01-02"),
		EndDate:    m.EndDate.Format("2006-01-02"),
		Status:     m.Status,
		CreatedAt:  m.CreatedAt.Format(time.RFC3339),
	}
	if plan != nil {
		dto.PlanName = plan.Name
		dto.PlanType = plan.PlanType
	}
	return dto
}

func randHex(n int) string {
	b := make([]byte, n/2+1)
	rand.Read(b)
	return hex.EncodeToString(b)[:n]
}

func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
