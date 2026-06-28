package model

import (
	"time"

	"github.com/google/uuid"
)

// DB models (shared tables)

type User struct {
	ID          uuid.UUID  `db:"id"`
	Mobile      *string    `db:"mobile"`
	Email       *string    `db:"email"`
	Name        string     `db:"name"`
	Address     *string    `db:"address"`
	FatherName  *string    `db:"father_name"`
	PhotoURL    *string    `db:"photo_url"`
	AadhaarURL  *string    `db:"aadhaar_url"`
	DateOfBirth *time.Time `db:"date_of_birth"`
	Gender      *string    `db:"gender"`
	IsActive    bool       `db:"is_active"`
	Role        string     `db:"role"`
	CreatedAt   *time.Time `db:"created_at"`
	UpdatedAt   *time.Time `db:"updated_at"`
}

type Membership struct {
	ID           uuid.UUID  `db:"id"`
	UserID       uuid.UUID  `db:"user_id"`
	PlanID       uuid.UUID  `db:"plan_id"`
	SeatID       *uuid.UUID `db:"seat_id"`
	SeatNumber   *string    `db:"seat_number"`
	Shift        *string    `db:"shift"`
	StartDate    time.Time  `db:"start_date"`
	EndDate      time.Time  `db:"end_date"`
	Status       string     `db:"status"`
	ReminderSent bool       `db:"reminder_sent"`
	CreatedAt    *time.Time `db:"created_at"`
}

type Payment struct {
	ID               uuid.UUID  `db:"id"`
	MembershipID     uuid.UUID  `db:"membership_id"`
	UserID           uuid.UUID  `db:"user_id"`
	Amount           float64    `db:"amount"`
	PendingAmount    float64    `db:"pending_amount"`
	PaymentGateway   *string    `db:"payment_gateway"`
	GatewayOrderID   *string    `db:"gateway_order_id"`
	GatewayPaymentID *string    `db:"gateway_payment_id"`
	Status           string     `db:"status"`
	CreatedAt        *time.Time `db:"created_at"`
	UpdatedAt        *time.Time `db:"updated_at"`
}

type Plan struct {
	ID           uuid.UUID `db:"id"`
	Name         string    `db:"name"`
	PlanType     string    `db:"plan_type"`
	Price        float64   `db:"price"`
	DurationDays int       `db:"duration_days"`
	IsActive     bool      `db:"is_active"`
}

type Seat struct {
	ID         uuid.UUID `db:"id"`
	SeatNumber string    `db:"seat_number"`
	RowLabel   string    `db:"row_label"`
	SeatIndex  int       `db:"seat_index"`
	IsActive   bool      `db:"is_active"`
}

type SeatBooking struct {
	ID           uuid.UUID `db:"id"`
	SeatID       uuid.UUID `db:"seat_id"`
	UserID       uuid.UUID `db:"user_id"`
	MembershipID uuid.UUID `db:"membership_id"`
	Shift        string    `db:"shift"`
	BookingDate  time.Time `db:"booking_date"`
	EndDate      time.Time `db:"end_date"`
	Status       string    `db:"status"`
	CreatedAt    time.Time `db:"created_at"`
}

type Feedback struct {
	ID          uuid.UUID `db:"id"`
	UserID      uuid.UUID `db:"user_id"`
	Type        string    `db:"type"`
	Subject     string    `db:"subject"`
	Description string    `db:"description"`
	Status      string    `db:"status"`
	AdminNotes  *string    `db:"admin_notes"`
	CreatedAt   *time.Time `db:"created_at"`
	UpdatedAt   *time.Time `db:"updated_at"`
}

type MonthlyExpense struct {
	ID                uuid.UUID `db:"id"`
	Year              int       `db:"year"`
	Month             int       `db:"month"`
	WaterTankerQty    int       `db:"water_tanker_qty"`
	WaterTankerPrice  float64   `db:"water_tanker_price"`
	ElectricityBill   float64   `db:"electricity_bill"`
	InternetBill      float64   `db:"internet_bill"`
	Miscellaneous     float64   `db:"miscellaneous"`
	CreatedAt         time.Time `db:"created_at"`
	UpdatedAt         time.Time `db:"updated_at"`
}

type MiscExpenseItem struct {
	ID               uuid.UUID `db:"id"`
	MonthlyExpenseID uuid.UUID `db:"monthly_expense_id"`
	Description      string    `db:"description"`
	Amount           float64   `db:"amount"`
	SortOrder        int       `db:"sort_order"`
}

type BroadcastMessage struct {
	ID             uuid.UUID `db:"id"`
	Message        string    `db:"message"`
	RecipientCount int       `db:"recipient_count"`
	SentAt         time.Time `db:"sent_at"`
}

type VisitorEvent struct {
	ID        int64     `db:"id"`
	Page      string    `db:"page"`
	CreatedAt time.Time `db:"created_at"`
}
