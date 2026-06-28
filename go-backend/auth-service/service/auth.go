package service

import (
	"context"
	"errors"
	"fmt"
	"math/rand"
	"os"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"
	"library/auth-service/model"
	"library/auth-service/repository"
	jwtpkg "library/shared/jwt"
)

type AuthService struct {
	userRepo    *repository.UserRepo
	redis       *redis.Client
	otpSvc      *OtpService
	adminPhones map[string]bool
}

func NewAuthService(repo *repository.UserRepo, rdb *redis.Client, otpSvc *OtpService) *AuthService {
	phones := make(map[string]bool)
	for _, p := range strings.Split(os.Getenv("ADMIN_PHONES"), ",") {
		p = strings.TrimSpace(p)
		if p != "" {
			phones[p] = true
		}
	}
	return &AuthService{userRepo: repo, redis: rdb, otpSvc: otpSvc, adminPhones: phones}
}

func (s *AuthService) SendOTP(contact, contactType string) error {
	ctx := context.Background()
	cooldownKey := "otp:cooldown:" + contact

	if s.redis.Exists(ctx, cooldownKey).Val() > 0 {
		return errors.New("please wait 30 seconds before requesting another OTP")
	}

	otp := s.generateOTP()

	if err := s.redis.Set(ctx, "otp:"+contact, otp, 5*time.Minute).Err(); err != nil {
		return fmt.Errorf("failed to store OTP: %w", err)
	}
	if err := s.redis.Set(ctx, cooldownKey, "1", 30*time.Second).Err(); err != nil {
		return err
	}

	return s.otpSvc.Send(contact, contactType, otp)
}

func (s *AuthService) VerifyOTP(contact, otp string) (*model.OTPVerifyResponse, error) {
	ctx := context.Background()
	stored, err := s.redis.Get(ctx, "otp:"+contact).Result()
	if err == redis.Nil {
		return nil, errors.New("OTP expired or not found")
	}
	if err != nil {
		return nil, err
	}
	if stored != otp {
		return nil, errors.New("invalid OTP")
	}

	s.redis.Del(ctx, "otp:"+contact)

	exists, err := s.userRepo.ExistsByMobileOrEmail(contact)
	if err != nil {
		return nil, err
	}

	sessionToken := uuid.New().String()
	if err := s.redis.Set(ctx, "session:"+sessionToken, contact, 15*time.Minute).Err(); err != nil {
		return nil, err
	}

	return &model.OTPVerifyResponse{
		Verified:     true,
		SessionToken: sessionToken,
		IsNewUser:    !exists,
	}, nil
}

func (s *AuthService) Register(req model.RegisterRequest) (*model.AuthResponse, error) {
	ctx := context.Background()
	contact, err := s.consumeSession(ctx, req.SessionToken)
	if err != nil {
		return nil, err
	}

	exists, _ := s.userRepo.ExistsByMobileOrEmail(contact)
	if exists {
		return nil, errors.New("user already registered")
	}

	u := &model.User{
		Name:     req.Name,
		IsActive: true,
		Role:     "STUDENT",
	}
	if req.Address != "" {
		u.Address = &req.Address
	}
	if req.Gender != "" {
		u.Gender = &req.Gender
	}
	if req.DateOfBirth != "" {
		if dob, err := time.Parse("2006-01-02", req.DateOfBirth); err == nil {
			u.DateOfBirth = &dob
		}
	}
	if strings.Contains(contact, "@") {
		u.Email = &contact
		if req.Mobile != "" {
			u.Mobile = &req.Mobile
		}
	} else {
		u.Mobile = &contact
		if req.Email != "" {
			u.Email = &req.Email
		}
	}

	if err := s.userRepo.Save(u); err != nil {
		return nil, err
	}

	return s.buildAuthResponse(u)
}

func (s *AuthService) Login(req model.LoginRequest) (*model.AuthResponse, error) {
	ctx := context.Background()
	contact, err := s.consumeSession(ctx, req.SessionToken)
	if err != nil {
		return nil, err
	}

	u, err := s.userRepo.FindByMobileOrEmail(contact)
	if err != nil {
		return nil, err
	}
	if u == nil {
		return nil, errors.New("user not found")
	}

	return s.buildAuthResponse(u)
}

func (s *AuthService) AdminLogin(contact string) (*model.AuthResponse, error) {
	u, err := s.userRepo.FindByMobileOrEmail(contact)
	if err != nil {
		return nil, err
	}
	if u == nil {
		return nil, errors.New("admin not found")
	}

	if !s.adminPhones[contact] && u.Role != "ADMIN" {
		return nil, errors.New("access denied: not an admin")
	}

	return s.buildAuthResponse(u)
}

func (s *AuthService) consumeSession(ctx context.Context, token string) (string, error) {
	if token == "" {
		return "", errors.New("session token required")
	}
	contact, err := s.redis.Get(ctx, "session:"+token).Result()
	if err == redis.Nil {
		return "", errors.New("session expired or invalid")
	}
	if err != nil {
		return "", err
	}
	s.redis.Del(ctx, "session:"+token)
	return contact, nil
}

func (s *AuthService) buildAuthResponse(u *model.User) (*model.AuthResponse, error) {
	role := s.effectiveRole(u)
	email := ""
	if u.Email != nil {
		email = *u.Email
	}
	mobile := ""
	if u.Mobile != nil {
		mobile = *u.Mobile
	}
	contact := mobile
	if contact == "" {
		contact = email
	}

	token, err := jwtpkg.GenerateToken(u.ID.String(), role, u.Name, email, mobile)
	if err != nil {
		return nil, err
	}

	return &model.AuthResponse{
		AccessToken: token,
		TokenType:   "Bearer",
		ExpiresIn:   86400,
		User: model.UserInfoDTO{
			ID:       u.ID.String(),
			Name:     u.Name,
			Mobile:   u.Mobile,
			Email:    u.Email,
			Role:     role,
			PhotoURL: u.PhotoURL,
		},
	}, nil
}

func (s *AuthService) effectiveRole(u *model.User) string {
	if u.Mobile != nil && s.adminPhones[*u.Mobile] {
		return "ADMIN"
	}
	return u.Role
}

func (s *AuthService) generateOTP() string {
	devMode := os.Getenv("DEV_MODE") == "true"
	if devMode && !s.otpSvc.IsLiveConfigured() {
		return "123456"
	}
	return fmt.Sprintf("%06d", rand.Intn(999999))
}

func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
