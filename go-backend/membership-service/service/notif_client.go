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
	return &NotifClient{
		baseURL: url,
		http:    &http.Client{Timeout: 5 * time.Second},
	}
}

func (c *NotifClient) post(path string, payload interface{}) {
	b, err := json.Marshal(payload)
	if err != nil {
		log.Printf("[warn] notif marshal error: %v", err)
		return
	}
	resp, err := c.http.Post(c.baseURL+path, "application/json", bytes.NewReader(b))
	if err != nil {
		log.Printf("[warn] notif POST %s failed: %v", path, err)
		return
	}
	resp.Body.Close()
}

type bookingConfirmedPayload struct {
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

type seatAssistancePayload struct {
	UserID      string `json:"userId"`
	UserName    string `json:"userName"`
	SeatNumber  string `json:"seatNumber"`
	AdminMobile string `json:"adminMobile"`
}

func (c *NotifClient) BookingConfirmed(userID, membershipID, userName, mobile, email, planName, planType, seat, shift, start, end string, amount float64) {
	c.post("/internal/notify/booking-confirmed", bookingConfirmedPayload{
		UserID: userID, MembershipID: membershipID, UserName: userName,
		UserMobile: mobile, UserEmail: email, PlanName: planName, PlanType: planType,
		SeatNumber: seat, Shift: shift, StartDate: start, EndDate: end,
		AmountPaid: amount, EventType: "BOOKING_CONFIRMED",
	})
}

func (c *NotifClient) SeatAssistance(userID, userName, seatNumber, adminMobile string) {
	c.post("/internal/notify/seat-assistance", seatAssistancePayload{
		UserID: userID, UserName: userName, SeatNumber: seatNumber, AdminMobile: adminMobile,
	})
}
