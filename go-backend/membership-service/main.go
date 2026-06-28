package main

import (
	"net/http"
	"os"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"library/membership-service/handler"
	"library/membership-service/repository"
	"library/membership-service/service"
	shareddb "library/shared/db"
	"library/shared/middleware"
)

func main() {
	db := shareddb.NewDB()
	repo := repository.New(db)

	// Seed plans on startup if table empty
	repo.SeedPlans()

	notifClient := service.NewNotifClient()
	paymentSvc  := service.NewPaymentService(repo, notifClient)
	idCardSvc   := service.NewIDCardService(repo)
	h           := handler.New(repo, paymentSvc, idCardSvc)

	r := gin.New()
	r.Use(gin.Logger(), gin.Recovery())
	r.Use(cors.Default())

	r.GET("/actuator/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "UP"})
	})

	// Public
	r.GET("/api/plans", h.GetPlans)

	// Authenticated
	auth := middleware.AuthRequired()
	memberships := r.Group("/api/memberships", auth)
	memberships.GET("/my", h.GetMyMembership)
	memberships.GET("/my/queued", h.GetMyQueuedMembership)
	memberships.GET("/my/all", h.GetAllMemberships)
	memberships.GET("/my/id-card", h.GetIDCard)
	memberships.POST("/my/call-admin", h.CallAdmin)

	payments := r.Group("/api/payments", auth)
	payments.GET("/my", h.GetMyPayments)
	payments.POST("/create-order", h.CreateOrder)
	payments.POST("/verify", h.VerifyPayment)

	port := os.Getenv("PORT")
	if port == "" {
		port = "8083"
	}
	r.Run(":" + port)
}
