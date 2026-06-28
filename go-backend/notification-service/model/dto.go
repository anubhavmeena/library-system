package model

type BookingConfirmedPayload struct {
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
	EventType    string  `json:"eventType"` // BOOKING_CONFIRMED | USER_REGISTERED
}

type RenewalReminderPayload struct {
	UserID        string   `json:"userId"`
	MembershipID  string   `json:"membershipId"`
	UserName      string   `json:"userName"`
	UserMobile    string   `json:"userMobile"`
	UserEmail     string   `json:"userEmail"`
	SeatNumber    string   `json:"seatNumber"`
	ExpiryDate    string   `json:"expiryDate"`
	DaysRemaining int      `json:"daysRemaining"`
	PendingAmount *float64 `json:"pendingAmount"`
	EventType     string   `json:"eventType"` // RENEWAL_REMINDER | SEAT_EXPIRED | PENDING_FEE_REMINDER
}

type BroadcastPayload struct {
	UserID  string `json:"userId"`
	Mobile  string `json:"mobile"`
	Message string `json:"message"`
	IsFirst bool   `json:"isFirst"`
}

type SeatAssistancePayload struct {
	UserID      string `json:"userId"`
	UserName    string `json:"userName"`
	SeatNumber  string `json:"seatNumber"`
	AdminMobile string `json:"adminMobile"`
}
