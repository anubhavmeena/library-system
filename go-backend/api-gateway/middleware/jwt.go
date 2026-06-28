package middleware

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	jwtpkg "library/shared/jwt"
	"library/shared/response"
)

// publicPrefixes are passed through without JWT validation.
var publicPrefixes = []string{
	"/api/auth/send-otp",
	"/api/auth/verify-otp",
	"/api/auth/register",
	"/api/auth/login",
	"/api/auth/admin/login",
	"/api/visitor/track",
}

// publicExact are exact paths (with optional trailing slash) that bypass auth.
var publicExact = map[string]bool{
	"/api/plans":  true,
	"/api/plans/": true,
}

func GatewayJWT() gin.HandlerFunc {
	return func(c *gin.Context) {
		path := c.Request.URL.Path

		if publicExact[path] {
			c.Next()
			return
		}
		for _, prefix := range publicPrefixes {
			if path == prefix || strings.HasPrefix(path, prefix+"/") || strings.HasPrefix(path, prefix+"?") {
				c.Next()
				return
			}
		}

		authHeader := c.GetHeader("Authorization")
		if !strings.HasPrefix(authHeader, "Bearer ") {
			c.AbortWithStatusJSON(http.StatusUnauthorized, response.Fail("missing or invalid authorization header"))
			return
		}

		claims, err := jwtpkg.ParseToken(strings.TrimPrefix(authHeader, "Bearer "))
		if err != nil {
			c.AbortWithStatusJSON(http.StatusUnauthorized, response.Fail("invalid or expired token"))
			return
		}

		c.Request.Header.Set("X-User-Id", claims.Sub)
		c.Request.Header.Set("X-User-Role", claims.Role)
		c.Next()
	}
}

func AdminRole() gin.HandlerFunc {
	return func(c *gin.Context) {
		if c.Request.Header.Get("X-User-Role") != "ADMIN" {
			c.AbortWithStatusJSON(http.StatusForbidden, response.Fail("forbidden: admin role required"))
			return
		}
		c.Next()
	}
}
