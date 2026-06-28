package service

import (
	"os"
	"testing"
)

func TestFormatMobile(t *testing.T) {
	cases := []struct {
		in   string
		want string
	}{
		{"9876543210", "+919876543210"},
		{"+919876543210", "+919876543210"},
		{"919876543210", "+919876543210"}, // 12-digit no + → "+" + digits
		{"98765 43210", "+919876543210"},
		{"+1-800-555-0100", "+18005550100"},
	}
	for _, c := range cases {
		got := formatMobile(c.in)
		if got != c.want {
			t.Errorf("formatMobile(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestIsLiveConfigured_False(t *testing.T) {
	os.Unsetenv("META_WHATSAPP_TOKEN")
	os.Unsetenv("APITXT_AUTH_KEY")
	os.Unsetenv("TWILIO_ACCOUNT_SID")
	os.Unsetenv("SENDGRID_API_KEY")
	svc := &OtpService{}
	if svc.IsLiveConfigured() {
		t.Error("expected IsLiveConfigured=false when no credentials set")
	}
}

func TestIsLiveConfigured_Meta(t *testing.T) {
	svc := &OtpService{metaToken: "some-token"}
	if !svc.IsLiveConfigured() {
		t.Error("expected IsLiveConfigured=true when metaToken set")
	}
}

func TestIsLiveConfigured_Apitxt(t *testing.T) {
	svc := &OtpService{apitxtKey: "key123"}
	if !svc.IsLiveConfigured() {
		t.Error("expected IsLiveConfigured=true when apitxtKey set")
	}
}
