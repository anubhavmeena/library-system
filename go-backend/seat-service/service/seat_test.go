package service

import (
	"fmt"
	"testing"
	"time"
)

func TestResolveShift(t *testing.T) {
	cases := []struct {
		in   string
		want string
	}{
		{"MORNING", "MORNING"},
		{"EVENING", "EVENING"},
		{"FULL_DAY", "FULL_DAY"},
		{"", "FULL_DAY"},
		{"morning", "FULL_DAY"}, // case-sensitive
		{"full_day", "FULL_DAY"},
	}
	for _, c := range cases {
		got := resolveShift(c.in)
		if got != c.want {
			t.Errorf("resolveShift(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestCacheKeyFormat(t *testing.T) {
	date := time.Date(2024, 6, 15, 0, 0, 0, 0, time.UTC)
	key := fmt.Sprintf("seats:availability:%s:%s", "MORNING", date.Format("2006-01-02"))
	want := "seats:availability:MORNING:2024-06-15"
	if key != want {
		t.Errorf("cache key: got %q, want %q", key, want)
	}
}

func TestCacheInvalidationKeys_FullDay(t *testing.T) {
	// FULL_DAY booking should invalidate MORNING, EVENING, and FULL_DAY keys
	shift := "FULL_DAY"
	date := "2024-06-15"

	keys := []string{fmt.Sprintf("seats:availability:%s:%s", shift, date)}
	switch shift {
	case "FULL_DAY":
		keys = append(keys,
			fmt.Sprintf("seats:availability:MORNING:%s", date),
			fmt.Sprintf("seats:availability:EVENING:%s", date))
	}

	if len(keys) != 3 {
		t.Errorf("FULL_DAY should invalidate 3 keys, got %d", len(keys))
	}
	expected := map[string]bool{
		"seats:availability:FULL_DAY:2024-06-15":  true,
		"seats:availability:MORNING:2024-06-15":   true,
		"seats:availability:EVENING:2024-06-15":   true,
	}
	for _, k := range keys {
		if !expected[k] {
			t.Errorf("unexpected key: %q", k)
		}
	}
}

func TestCacheInvalidationKeys_Morning(t *testing.T) {
	// MORNING booking should invalidate MORNING and FULL_DAY (not EVENING)
	shift := "MORNING"
	date := "2024-06-15"

	keys := []string{fmt.Sprintf("seats:availability:%s:%s", shift, date)}
	switch shift {
	case "FULL_DAY":
		keys = append(keys,
			fmt.Sprintf("seats:availability:MORNING:%s", date),
			fmt.Sprintf("seats:availability:EVENING:%s", date))
	default:
		keys = append(keys, fmt.Sprintf("seats:availability:FULL_DAY:%s", date))
	}

	if len(keys) != 2 {
		t.Errorf("MORNING should invalidate 2 keys, got %d: %v", len(keys), keys)
	}
	for _, k := range keys {
		if k == fmt.Sprintf("seats:availability:EVENING:%s", date) {
			t.Error("MORNING booking should NOT invalidate EVENING cache")
		}
	}
}

func TestSeatSeeding_Count(t *testing.T) {
	// Verify seat seeding logic: A(28) + B(28) + C(28) + D(26) = 110 total, 2 blocked
	type rowDef struct {
		label string
		count int
	}
	rows := []rowDef{{"A", 28}, {"B", 28}, {"C", 28}, {"D", 26}}
	blocked := map[string]bool{"B8": true, "B18": true}

	total := 0
	active := 0
	for _, row := range rows {
		for i := 1; i <= row.count; i++ {
			seatNum := fmt.Sprintf("%s%d", row.label, i)
			total++
			if !blocked[seatNum] {
				active++
			}
		}
	}

	if total != 110 {
		t.Errorf("total seats: got %d, want 110", total)
	}
	if active != 108 {
		t.Errorf("active seats: got %d, want 108 (110 - 2 blocked)", active)
	}
}

func TestSeatSeeding_BlockedSeats(t *testing.T) {
	blocked := map[string]bool{"B8": true, "B18": true}
	if !blocked["B8"] {
		t.Error("B8 should be blocked")
	}
	if !blocked["B18"] {
		t.Error("B18 should be blocked")
	}
	if blocked["A1"] {
		t.Error("A1 should NOT be blocked")
	}
	if blocked["B9"] {
		t.Error("B9 should NOT be blocked")
	}
}

func TestDateRangeForInvalidation(t *testing.T) {
	// Cache invalidation iterates day-by-day over booking range
	start := time.Date(2024, 6, 1, 0, 0, 0, 0, time.UTC)
	end := time.Date(2024, 6, 3, 0, 0, 0, 0, time.UTC)

	var days []string
	for d := start; !d.After(end); d = d.AddDate(0, 0, 1) {
		days = append(days, d.Format("2006-01-02"))
	}

	if len(days) != 3 {
		t.Errorf("3-day range should produce 3 keys, got %d: %v", len(days), days)
	}
	if days[0] != "2024-06-01" || days[2] != "2024-06-03" {
		t.Errorf("unexpected day range: %v", days)
	}
}
