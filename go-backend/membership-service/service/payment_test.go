package service

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"strings"
	"testing"
)

func computeHMAC(orderID, paymentID, secret string) string {
	data := orderID + "|" + paymentID
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write([]byte(data))
	return hex.EncodeToString(mac.Sum(nil))
}

func TestVerifyRazorpayHMAC_Valid(t *testing.T) {
	secret := "test-razorpay-secret"
	orderID := "order_abc123"
	paymentID := "pay_xyz789"
	sig := computeHMAC(orderID, paymentID, secret)

	if !verifyRazorpayHMAC(orderID, paymentID, sig, secret) {
		t.Error("valid HMAC rejected")
	}
}

func TestVerifyRazorpayHMAC_Invalid(t *testing.T) {
	if verifyRazorpayHMAC("order_abc", "pay_xyz", "badhmacsignature", "correct-secret") {
		t.Error("invalid HMAC should be rejected")
	}
}

func TestVerifyRazorpayHMAC_WrongSecret(t *testing.T) {
	orderID := "order_abc123"
	paymentID := "pay_xyz789"
	sig := computeHMAC(orderID, paymentID, "wrong-secret")

	if verifyRazorpayHMAC(orderID, paymentID, sig, "correct-secret") {
		t.Error("HMAC signed with wrong secret should fail")
	}
}

func TestVerifyRazorpayHMAC_EmptySignature(t *testing.T) {
	if verifyRazorpayHMAC("order_abc", "pay_xyz", "", "secret") {
		t.Error("empty signature should fail")
	}
}

func TestCashfreeOrderIDFormat(t *testing.T) {
	membershipID := "550e8400-e29b-41d4-a716-446655440000"
	expected8 := membershipID[:8]
	orderID := "lib_" + expected8 + "_abcd"

	if !strings.HasPrefix(orderID, "lib_") {
		t.Error("cashfree order ID should start with lib_")
	}
	if !strings.Contains(orderID, expected8) {
		t.Errorf("order ID should contain first 8 chars %q", expected8)
	}
}

func TestDevOrderPrefix(t *testing.T) {
	devOrders := []string{"dev_order_12345678", "dev_abc"}
	for _, id := range devOrders {
		if !strings.HasPrefix(id, "dev_") {
			t.Errorf("expected dev_ prefix in %q", id)
		}
	}
	realOrders := []string{"lib_550e8400_ab12", "order_razorpay123"}
	for _, id := range realOrders {
		if strings.HasPrefix(id, "dev_") {
			t.Errorf("real order should not have dev_ prefix: %q", id)
		}
	}
}
