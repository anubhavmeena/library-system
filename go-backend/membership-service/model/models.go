package model

import (
	"time"

	"github.com/google/uuid"
)

type Plan struct {
	ID           uuid.UUID `db:"id"`
	Name         string    `db:"name"`
	PlanType     string    `db:"plan_type"` // HALF_DAY | FULL_DAY
	Price        float64   `db:"price"`
	DurationDays int       `db:"duration_days"`
	Description  *string   `db:"description"`
	IsActive     bool      `db:"is_active"`
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
	Status       string     `db:"status"` // PENDING | ACTIVE | QUEUED | EXPIRED | CANCELLED
	ReminderSent bool       `db:"reminder_sent"`
	CreatedAt    time.Time  `db:"created_at"`
}

type Payment struct {
	ID               uuid.UUID `db:"id"`
	MembershipID     uuid.UUID `db:"membership_id"`
	UserID           uuid.UUID `db:"user_id"`
	Amount           float64   `db:"amount"`
	PendingAmount    float64   `db:"pending_amount"`
	PaymentGateway   string    `db:"payment_gateway"`
	GatewayOrderID   string    `db:"gateway_order_id"`
	GatewayPaymentID *string   `db:"gateway_payment_id"`
	Status           string    `db:"status"` // PENDING | SUCCESS | FAILED | REFUNDED
	CreatedAt        time.Time `db:"created_at"`
	UpdatedAt        time.Time `db:"updated_at"`
}
