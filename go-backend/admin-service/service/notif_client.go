package service

import (
	"bytes"
	"encoding/json"
	"log"
	"net/http"
	"os"
	"time"
)

type NotifClient struct {
	baseURL string
	http    *http.Client
}

func NewNotifClient() *NotifClient {
	url := os.Getenv("NOTIFICATION_SERVICE_URL")
	if url == "" {
		url = "http://localhost:8085"
	}
	return &NotifClient{baseURL: url, http: &http.Client{Timeout: 5 * time.Second}}
}

func (c *NotifClient) post(path string, payload interface{}) {
	b, err := json.Marshal(payload)
	if err != nil {
		log.Printf("[warn] notif marshal: %v", err)
		return
	}
	resp, err := c.http.Post(c.baseURL+path, "application/json", bytes.NewReader(b))
	if err != nil {
		log.Printf("[warn] notif POST %s: %v", path, err)
		return
	}
	resp.Body.Close()
}

type renewalPayload struct {
	UserID        string   `json:"userId"`
	MembershipID  string   `json:"membershipId"`
	UserName      string   `json:"userName"`
	UserMobile    string   `json:"userMobile"`
	UserEmail     string   `json:"userEmail"`
	SeatNumber    string   `json:"seatNumber"`
	ExpiryDate    string   `json:"expiryDate"`
	DaysRemaining int      `json:"daysRemaining"`
	PendingAmount *float64 `json:"pendingAmount"`
	EventType     string   `json:"eventType"`
}

type broadcastPayload struct {
	UserID  string `json:"userId"`
	Mobile  string `json:"mobile"`
	Message string `json:"message"`
	IsFirst bool   `json:"isFirst"`
}

type bookingPayload struct {
	UserID       string  `json:"userId"`
	MembershipID string  `json:"membershipId"`
	UserName     string  `json:"userName"`
	UserMobile   string  `json:"userMobile"`
	UserEmail    string  `json:"userEmail"`
	PlanName     string  `json:"planName"`
	PlanType     string  `json:"planType"`
	SeatNumber   string  `json:"seatNumber"`
	Shift        string  `json:"shift"`
	StartDate    string  `json:"startDate"`
	EndDate      string  `json:"endDate"`
	AmountPaid   float64 `json:"amountPaid"`
	EventType    string  `json:"eventType"`
}

func (c *NotifClient) RenewalReminder(userID, membershipID, userName, mobile, email, seat, expiry string, days int, eventType string, pendingAmount *float64) {
	c.post("/internal/notify/renewal-reminder", renewalPayload{
		UserID: userID, MembershipID: membershipID, UserName: userName,
		UserMobile: mobile, UserEmail: email, SeatNumber: seat,
		ExpiryDate: expiry, DaysRemaining: days, EventType: eventType,
		PendingAmount: pendingAmount,
	})
}

func (c *NotifClient) Broadcast(userID, mobile, message string, isFirst bool) {
	c.post("/internal/notify/broadcast", broadcastPayload{
		UserID: userID, Mobile: mobile, Message: message, IsFirst: isFirst,
	})
}

func (c *NotifClient) BookingConfirmed(userID, membershipID, userName, mobile, email, planName, planType, seat, shift, start, end string, amount float64) {
	c.post("/internal/notify/booking-confirmed", bookingPayload{
		UserID: userID, MembershipID: membershipID, UserName: userName,
		UserMobile: mobile, UserEmail: email, PlanName: planName, PlanType: planType,
		SeatNumber: seat, Shift: shift, StartDate: start, EndDate: end,
		AmountPaid: amount, EventType: "BOOKING_CONFIRMED",
	})
}
