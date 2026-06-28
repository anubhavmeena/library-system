package model

import (
	"time"

	"github.com/google/uuid"
)

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
	CreatedAt   time.Time  `db:"created_at"`
	UpdatedAt   time.Time  `db:"updated_at"`
}
