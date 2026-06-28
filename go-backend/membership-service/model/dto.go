package model

type PlanDTO struct {
	ID           string  `json:"id"`
	Name         string  `json:"name"`
	PlanType     string  `json:"planType"`
	Price        float64 `json:"price"`
	DurationDays int     `json:"durationDays"`
	Description  *string `json:"description"`
	IsActive     bool    `json:"isActive"`
}

type MembershipDTO struct {
	ID         string  `json:"id"`
	UserID     string  `json:"userId"`
	PlanID     string  `json:"planId"`
	PlanName   string  `json:"planName"`
	PlanType   string  `json:"planType"`
	SeatNumber *string `json:"seatNumber"`
	Shift      *string `json:"shift"`
	StartDate  string  `json:"startDate"`
	EndDate    string  `json:"endDate"`
	Status     string  `json:"status"`
	CreatedAt  string  `json:"createdAt"`
}

type PaymentDTO struct {
	ID               string  `json:"id"`
	MembershipID     string  `json:"membershipId"`
	Amount           float64 `json:"amount"`
	PendingAmount    float64 `json:"pendingAmount"`
	PaymentGateway   string  `json:"paymentGateway"`
	GatewayOrderID   string  `json:"gatewayOrderId"`
	GatewayPaymentID *string `json:"gatewayPaymentId"`
	Status           string  `json:"status"`
	CreatedAt        string  `json:"createdAt"`
}

type CreateOrderRequest struct {
	PlanID     string `json:"planId" validate:"required"`
	SeatID     string `json:"seatId"`
	SeatNumber string `json:"seatNumber"`
	Shift      string `json:"shift"`
}

type CreateOrderResponse struct {
	OrderID          string  `json:"orderId"`
	MembershipID     string  `json:"membershipId"`
	Amount           float64 `json:"amount"`
	Currency         string  `json:"currency"`
	Gateway          string  `json:"gateway"`
	PaymentSessionID *string `json:"paymentSessionId"` // Cashfree only
	RazorpayKeyID    *string `json:"razorpayKeyId"`    // Razorpay only
}

type PaymentVerifyRequest struct {
	OrderID          string `json:"gatewayOrderId"`
	PaymentID        string `json:"gatewayPaymentId"`
	Signature        string `json:"signature"`
	MembershipID     string `json:"membershipId"`
	PaymentSessionID string `json:"paymentSessionId"`
}

// UserProfileDTO mirrors user-service response for ID card generation
type UserProfileDTO struct {
	ID          string  `json:"id"`
	Name        string  `json:"name"`
	FatherName  *string `json:"fatherName"`
	Mobile      *string `json:"mobile"`
	Email       *string `json:"email"`
	PhotoURL    *string `json:"photoUrl"`
	DateOfBirth *string `json:"dateOfBirth"`
}
