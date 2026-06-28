package model

type UserDTO struct {
	ID          string  `json:"id"`
	Name        string  `json:"name"`
	FatherName  *string `json:"fatherName"`
	Mobile      *string `json:"mobile"`
	Email       *string `json:"email"`
	Address     *string `json:"address"`
	PhotoURL    *string `json:"photoUrl"`
	AadhaarURL  *string `json:"aadhaarUrl"`
	DateOfBirth *string `json:"dateOfBirth"`
	Gender      *string `json:"gender"`
	Role        string  `json:"role"`
	IsActive    bool    `json:"isActive"`
	CreatedAt   string  `json:"createdAt"`
}

type AdminContactDTO struct {
	Name   string  `json:"name"`
	Mobile *string `json:"mobile"`
	Email  *string `json:"email"`
}

type UpdateProfileRequest struct {
	Name        *string `json:"name"`
	FatherName  *string `json:"fatherName"`
	Address     *string `json:"address"`
	Gender      *string `json:"gender"`
	DateOfBirth *string `json:"dateOfBirth"`
	Email       *string `json:"email"`
}

type PhotoUploadResponse struct {
	PhotoURL string `json:"photoUrl"`
	Message  string `json:"message"`
}

type CreateFeedbackRequest struct {
	Type        string `json:"type" validate:"required"`
	Subject     string `json:"subject" validate:"required,max=255"`
	Description string `json:"description" validate:"required,max=5000"`
}

type FeedbackDTO struct {
	ID          string  `json:"id"`
	Type        string  `json:"type"`
	Subject     string  `json:"subject"`
	Description string  `json:"description"`
	Status      string  `json:"status"`
	AdminNotes  *string `json:"adminNotes"`
	CreatedAt   string  `json:"createdAt"`
	UpdatedAt   string  `json:"updatedAt"`
}

type GalleryPhotoDTO struct {
	ID         string  `json:"id"`
	URL        string  `json:"url"`
	Caption    *string `json:"caption"`
	UploadedAt string  `json:"uploadedAt"`
}
