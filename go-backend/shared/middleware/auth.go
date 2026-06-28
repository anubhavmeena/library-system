package middleware

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"library/shared/response"
)

const (
	userIDKey   = "userId"
	userRoleKey = "userRole"
)

// AuthRequired trusts X-User-Id / X-User-Role headers injected by the api-gateway.
func AuthRequired() gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := c.GetHeader("X-User-Id")
		if userID == "" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, response.Fail("authentication required"))
			return
		}
		c.Set(userIDKey, userID)
		c.Set(userRoleKey, c.GetHeader("X-User-Role"))
		c.Next()
	}
}

// AdminRequired must run after AuthRequired.
func AdminRequired() gin.HandlerFunc {
	return func(c *gin.Context) {
		if GetUserRole(c) != "ADMIN" {
			c.AbortWithStatusJSON(http.StatusForbidden, response.Fail("forbidden: admin role required"))
			return
		}
		c.Next()
	}
}

func GetUserID(c *gin.Context) string {
	v, _ := c.Get(userIDKey)
	s, _ := v.(string)
	return s
}

func GetUserRole(c *gin.Context) string {
	v, _ := c.Get(userRoleKey)
	s, _ := v.(string)
	return s
}
