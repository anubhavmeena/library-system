package main

import (
	"net/http"
	"os"

	"github.com/gin-gonic/gin"
	"library/notification-service/handler"
	"library/notification-service/repository"
	"library/notification-service/service"
	shareddb "library/shared/db"
)

func main() {
	db := shareddb.NewDB()
	logRepo := repository.NewLogRepo(db)

	waSvc    := service.NewWhatsAppService(logRepo)
	emailSvc := service.NewEmailService(logRepo)
	notifSvc := service.NewNotificationService(waSvc, emailSvc)
	h        := handler.New(notifSvc)

	r := gin.New()
	r.Use(gin.Logger(), gin.Recovery())

	r.GET("/actuator/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "UP"})
	})

	internal := r.Group("/internal/notify")
	internal.POST("/booking-confirmed", h.BookingConfirmed)
	internal.POST("/welcome",           h.Welcome)
	internal.POST("/renewal-reminder",  h.RenewalReminder)
	internal.POST("/broadcast",         h.Broadcast)
	internal.POST("/seat-assistance",   h.SeatAssistance)

	port := os.Getenv("PORT")
	if port == "" {
		port = "8085"
	}
	r.Run(":" + port)
}
