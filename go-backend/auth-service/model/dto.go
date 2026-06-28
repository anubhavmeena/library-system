package model

type SendOTPRequest struct {
	Contact     string `json:"contact" validate:"required"`
	ContactType string `json:"contactType"`
}

type VerifyOTPRequest struct {
	Contact string `json:"contact" validate:"required"`
	OTP     string `json:"otp" validate:"required"`
}

type OTPVerifyResponse struct {
	Verified     bool   `json:"verified"`
	SessionToken string `json:"sessionToken"`
	IsNewUser    bool   `json:"newUser"`
}

type RegisterRequest struct {
	SessionToken string `json:"sessionToken" validate:"required"`
	Name         string `json:"name" validate:"required"`
	Address      string `json:"address"`
	Gender       string `json:"gender"`
	DateOfBirth  string `json:"dateOfBirth"`
	Email        string `json:"email"`
	Mobile       string `json:"mobile"`
}

type LoginRequest struct {
	SessionToken string `json:"sessionToken"`
}

type AdminLoginRequest struct {
	Contact string `json:"contact" validate:"required"`
}

type AuthResponse struct {
	AccessToken string      `json:"accessToken"`
	TokenType   string      `json:"tokenType"`
	ExpiresIn   int64       `json:"expiresIn"`
	User        UserInfoDTO `json:"user"`
}

type UserInfoDTO struct {
	ID       string  `json:"id"`
	Name     string  `json:"name"`
	Mobile   *string `json:"mobile"`
	Email    *string `json:"email"`
	Role     string  `json:"role"`
	PhotoURL *string `json:"photoUrl"`
}
