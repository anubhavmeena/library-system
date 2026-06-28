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

type Feedback struct {
	ID          uuid.UUID `db:"id"`
	UserID      uuid.UUID `db:"user_id"`
	Type        string    `db:"type"`
	Subject     string    `db:"subject"`
	Description string    `db:"description"`
	Status      string    `db:"status"`
	AdminNotes  *string   `db:"admin_notes"`
	CreatedAt   time.Time `db:"created_at"`
	UpdatedAt   time.Time `db:"updated_at"`
}

type GalleryPhoto struct {
	ID         uuid.UUID `db:"id"`
	URL        string    `db:"url"`
	Caption    *string   `db:"caption"`
	UploadedBy *string   `db:"uploaded_by"`
	UploadedAt time.Time `db:"uploaded_at"`
}
