package main

import (
	"net/http"
	"os"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"library/auth-service/handler"
	"library/auth-service/repository"
	"library/auth-service/service"
	shareddb "library/shared/db"
	sharedredis "library/shared/redis"
	"library/shared/response"
)

func main() {
	db := shareddb.NewDB()
	rdb := sharedredis.NewClient()

	otpSvc := service.NewOtpService()
	userRepo := repository.NewUserRepo(db)
	authSvc := service.NewAuthService(userRepo, rdb, otpSvc)
	authHandler := handler.NewAuthHandler(authSvc)

	r := gin.New()
	r.Use(gin.Logger(), gin.Recovery())
	r.Use(cors.Default())

	r.GET("/actuator/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "UP"})
	})

	auth := r.Group("/api/auth")
	auth.POST("/send-otp", authHandler.SendOTP)
	auth.POST("/verify-otp", authHandler.VerifyOTP)
	auth.POST("/register", authHandler.Register)
	auth.POST("/login", authHandler.Login)
	auth.POST("/admin/login", authHandler.AdminLogin)

	// Unimplemented stub — matching Spring behaviour
	auth.POST("/refresh", func(c *gin.Context) {
		c.JSON(http.StatusNotImplemented, response.Fail("refresh token not supported"))
	})

	port := os.Getenv("PORT")
	if port == "" {
		port = "8081"
	}
	r.Run(":" + port)
}
