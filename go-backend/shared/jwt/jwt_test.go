package jwt

import (
	"os"
	"testing"
	"time"
)

func TestRoundTrip(t *testing.T) {
	os.Setenv("JWT_SECRET", "test-secret")
	token, err := GenerateToken("user-123", "STUDENT", "Alice", "alice@x.com", "+919876543210")
	if err != nil {
		t.Fatalf("GenerateToken: %v", err)
	}

	claims, err := ParseToken(token)
	if err != nil {
		t.Fatalf("ParseToken: %v", err)
	}
	if claims.Sub != "user-123" {
		t.Errorf("Sub: got %q, want %q", claims.Sub, "user-123")
	}
	if claims.Role != "STUDENT" {
		t.Errorf("Role: got %q, want %q", claims.Role, "STUDENT")
	}
	if claims.Name != "Alice" {
		t.Errorf("Name: got %q, want %q", claims.Name, "Alice")
	}
	if claims.Email != "alice@x.com" {
		t.Errorf("Email: got %q, want %q", claims.Email, "alice@x.com")
	}
	if claims.Mobile != "+919876543210" {
		t.Errorf("Mobile: got %q, want %q", claims.Mobile, "+919876543210")
	}
}

func TestExpiry(t *testing.T) {
	os.Setenv("JWT_SECRET", "test-secret")
	token, _ := GenerateToken("x", "STUDENT", "X", "", "")

	// Token should be valid now
	_, err := ParseToken(token)
	if err != nil {
		t.Errorf("valid token rejected: %v", err)
	}
}

func TestInvalidSecret(t *testing.T) {
	os.Setenv("JWT_SECRET", "secret-a")
	token, _ := GenerateToken("x", "STUDENT", "X", "", "")

	os.Setenv("JWT_SECRET", "secret-b")
	_, err := ParseToken(token)
	if err == nil {
		t.Error("token signed with different secret should be rejected")
	}
}

func TestAdminRole(t *testing.T) {
	os.Setenv("JWT_SECRET", "test-secret")
	token, err := GenerateToken("admin-1", "ADMIN", "Boss", "", "")
	if err != nil {
		t.Fatalf("GenerateToken: %v", err)
	}
	claims, err := ParseToken(token)
	if err != nil {
		t.Fatalf("ParseToken: %v", err)
	}
	if claims.Role != "ADMIN" {
		t.Errorf("Role: got %q, want ADMIN", claims.Role)
	}
}

func TestExpiryIsInFuture(t *testing.T) {
	os.Setenv("JWT_SECRET", "test-secret")
	token, _ := GenerateToken("u", "STUDENT", "U", "", "")
	claims, _ := ParseToken(token)

	exp := time.Unix(claims.ExpiresAt.Unix(), 0)
	if !exp.After(time.Now()) {
		t.Errorf("token already expired at %v", exp)
	}
	if exp.Before(time.Now().Add(23 * time.Hour)) {
		t.Errorf("expiry %v is less than 23h from now", exp)
	}
}
