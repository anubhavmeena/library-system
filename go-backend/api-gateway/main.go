package main

import (
	"net/http"
	"os"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"library/api-gateway/middleware"
	"library/api-gateway/proxy"
)

func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func main() {
	authURL         := getenv("AUTH_SERVICE_URL",         "http://localhost:8081")
	userURL         := getenv("USER_SERVICE_URL",         "http://localhost:8082")
	membershipURL   := getenv("MEMBERSHIP_SERVICE_URL",   "http://localhost:8083")
	seatURL         := getenv("SEAT_SERVICE_URL",         "http://localhost:8084")
	adminURL        := getenv("ADMIN_SERVICE_URL",        "http://localhost:8086")

	r := gin.New()
	r.Use(gin.Logger(), gin.Recovery())

	corsConfig := cors.DefaultConfig()
	corsConfig.AllowOrigins = []string{
		"http://localhost:3000",
		"http://frontend:3000",
		"https://targetzone.co.in",
	}
	corsConfig.AllowHeaders = []string{"Origin", "Content-Type", "Authorization", "X-User-Id", "X-User-Role"}
	corsConfig.AllowMethods = []string{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"}
	r.Use(cors.New(corsConfig))

	jwtMw   := middleware.GatewayJWT()
	adminMw := middleware.AdminRole()

	r.GET("/actuator/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "UP"})
	})

	// Public: all auth routes (JWT filter has internal bypass logic)
	r.Any("/api/auth/*path",   jwtMw, proxy.To(authURL))

	// Public: plan listing, visitor tracking, static uploads
	r.GET("/api/plans",            jwtMw, proxy.To(membershipURL))
	r.POST("/api/visitor/track",   proxy.To(adminURL))
	r.GET("/uploads/*path",        proxy.To(userURL))

	// JWT-protected service routes
	protected := r.Group("/", jwtMw)
	protected.Any("/api/users/*path",        proxy.To(userURL))
	protected.Any("/api/gallery/*path",      proxy.To(userURL))
	protected.Any("/api/memberships/*path",  proxy.To(membershipURL))
	protected.Any("/api/payments/*path",     proxy.To(membershipURL))
	protected.Any("/api/seats/*path",        proxy.To(seatURL))

	// Admin-only routes
	adminGroup := r.Group("/api/admin", jwtMw, adminMw)
	adminGroup.Any("/*path", proxy.To(adminURL))

	port := getenv("PORT", "8080")
	r.Run(":" + port)
}
