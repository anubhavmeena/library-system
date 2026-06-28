package service

import (
	"testing"
)

func TestSanitizeMessage(t *testing.T) {
	// multiSpace collapses 5+ whitespace chars to 4 spaces.
	// Single/double newlines become spaces but are NOT collapsed further.
	cases := []struct {
		in   string
		want string
	}{
		{"Hello World", "Hello World"},
		{"Hello  World", "Hello  World"}, // 2 spaces: no collapse (threshold is 5)
		{"a\t b", "a  b"},               // tab → space → 2 spaces total (no collapse)
		{"a     b", "a    b"},           // 5 spaces → collapsed to 4
		{"a      b", "a    b"},          // 6 spaces → collapsed to 4
	}
	for _, c := range cases {
		got := sanitizeMessage(c.in)
		if got != c.want {
			t.Errorf("sanitizeMessage(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestFormatMobileWhatsApp(t *testing.T) {
	cases := []struct {
		in   string
		want string
	}{
		{"9876543210", "+919876543210"},   // 10-digit → +91 prefix
		{"+919876543210", "+919876543210"}, // already prefixed with +
		{"919876543210", "+919876543210"}, // 12-digit no + → + prefix added
	}
	for _, c := range cases {
		got := formatMobile(c.in)
		if got != c.want {
			t.Errorf("formatMobile(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestWhatsAppEnabled(t *testing.T) {
	svc := &WhatsAppService{enabled: false}
	if svc.enabled {
		t.Error("WhatsApp should not be enabled with enabled=false")
	}

	svc2 := &WhatsAppService{enabled: true}
	if !svc2.enabled {
		t.Error("WhatsApp should be enabled when enabled=true")
	}
}
