package model

import (
	"time"

	"github.com/google/uuid"
)

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
	Status       string    `db:"status"` // ACTIVE | RELEASED | EXPIRED
	CreatedAt    time.Time `db:"created_at"`
}

// DTOs

type SeatDTO struct {
	ID         string `json:"id"`
	SeatNumber string `json:"seatNumber"`
	RowLabel   string `json:"rowLabel"`
	IsBooked   bool   `json:"isBooked"`
}

type SeatAvailabilityDTO struct {
	Shift      string               `json:"shift"`
	Date       string               `json:"date"`
	TotalSeats int                  `json:"totalSeats"`
	Booked     int                  `json:"booked"`
	Available  int                  `json:"available"`
	Seats      []SeatDTO            `json:"seats"`
	Rows       map[string][]SeatDTO `json:"rows"`
}

type BookSeatRequest struct {
	SeatNumber   string `json:"seatNumber" validate:"required"`
	MembershipID string `json:"membershipId" validate:"required"`
	StartDate    string `json:"startDate"`
	EndDate      string `json:"endDate"`
	Shift        string `json:"shift"`
}

type SeatBookingDTO struct {
	ID           string `json:"id"`
	SeatNumber   string `json:"seatNumber"`
	MembershipID string `json:"membershipId"`
	UserID       string `json:"userId"`
	Shift        string `json:"shift"`
	StartDate    string `json:"startDate"`
	EndDate      string `json:"endDate"`
	Status       string `json:"status"`
}
