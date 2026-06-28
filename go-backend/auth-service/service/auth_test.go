package service

import (
	"os"
	"testing"

	"library/auth-service/model"
)

func newTestAuthService(metaToken, apitxtKey string) *AuthService {
	otp := &OtpService{
		metaToken: metaToken,
		apitxtKey: apitxtKey,
	}
	return &AuthService{
		otpSvc:      otp,
		adminPhones: map[string]bool{"+919999999999": true},
	}
}

func TestGenerateOTP_DevMode_NoLive(t *testing.T) {
	os.Setenv("DEV_MODE", "true")
	svc := newTestAuthService("", "") // no live provider
	if got := svc.generateOTP(); got != "123456" {
		t.Errorf("DEV_MODE without live provider: got %q, want 123456", got)
	}
}

func TestGenerateOTP_DevMode_WithLive(t *testing.T) {
	os.Setenv("DEV_MODE", "true")
	svc := newTestAuthService("real-token", "") // live provider configured
	otp := svc.generateOTP()
	if len(otp) != 6 {
		t.Errorf("OTP length: got %d, want 6", len(otp))
	}
}

func TestGenerateOTP_ProductionMode(t *testing.T) {
	os.Setenv("DEV_MODE", "false")
	svc := newTestAuthService("", "")
	otp := svc.generateOTP()
	if len(otp) != 6 {
		t.Errorf("OTP length: got %d, want 6", len(otp))
	}
}

func TestEffectiveRole_Admin(t *testing.T) {
	svc := newTestAuthService("", "")
	mobile := "+919999999999"
	u := &model.User{Mobile: &mobile, Role: "STUDENT"}
	if got := svc.effectiveRole(u); got != "ADMIN" {
		t.Errorf("effectiveRole: got %q, want ADMIN for admin phone", got)
	}
}

func TestEffectiveRole_Student(t *testing.T) {
	svc := newTestAuthService("", "")
	mobile := "+910000000001"
	u := &model.User{Mobile: &mobile, Role: "STUDENT"}
	if got := svc.effectiveRole(u); got != "STUDENT" {
		t.Errorf("effectiveRole: got %q, want STUDENT for non-admin phone", got)
	}
}

func TestEffectiveRole_NilMobile(t *testing.T) {
	svc := newTestAuthService("", "")
	u := &model.User{Mobile: nil, Role: "STUDENT"}
	if got := svc.effectiveRole(u); got != "STUDENT" {
		t.Errorf("effectiveRole: got %q, want STUDENT when mobile is nil", got)
	}
}

func TestEffectiveRole_ExplicitAdmin(t *testing.T) {
	svc := newTestAuthService("", "")
	mobile := "+910000000002"
	u := &model.User{Mobile: &mobile, Role: "ADMIN"} // DB role is ADMIN
	if got := svc.effectiveRole(u); got != "ADMIN" {
		t.Errorf("effectiveRole: got %q, want ADMIN for DB role=ADMIN", got)
	}
}
