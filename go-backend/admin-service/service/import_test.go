package service

import (
	"testing"
)

func TestNormalizePhone(t *testing.T) {
	cases := []struct {
		in   string
		want string
	}{
		{"9876543210", "+919876543210"},   // 10-digit → +91 prefix
		{"919876543210", "+919876543210"}, // 12-digit starting with 91 → + prefix
		{"+919876543210", "+919876543210"}, // already has +, strips to 12-digit 91... → +91...
		{"98765 43210", "+919876543210"},  // spaces stripped → 10-digit
		{"9876-543-210", "+919876543210"}, // dashes stripped → 10-digit
		{"09876543210", "09876543210"},    // 11-digit → returned unchanged (no match)
	}
	for _, c := range cases {
		got := normalizePhone(c.in)
		if got != c.want {
			t.Errorf("normalizePhone(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestSafe(t *testing.T) {
	row := []string{"Alice", "  9876543210  ", ""}
	if got := safe(row, 0); got != "Alice" {
		t.Errorf("safe(0): got %q", got)
	}
	if got := safe(row, 1); got != "9876543210" {
		t.Errorf("safe(1): got %q (should be trimmed)", got)
	}
	if got := safe(row, 2); got != "" {
		t.Errorf("safe(2): got %q, want empty", got)
	}
	if got := safe(row, 99); got != "" {
		t.Errorf("safe(99): out of bounds should return empty, got %q", got)
	}
}

func TestParseDate(t *testing.T) {
	cases := []struct {
		in      string
		wantNil bool
	}{
		{"2000-06-15", false},
		{"15/06/2000", false},
		{"June 15, 2000", false},
		{"not-a-date", true},
		{"", true},
	}
	for _, c := range cases {
		got := parseDate(c.in)
		if c.wantNil && got != nil {
			t.Errorf("parseDate(%q) = %v, want nil", c.in, got)
		}
		if !c.wantNil && got == nil {
			t.Errorf("parseDate(%q) = nil, want non-nil", c.in)
		}
	}
}
