package main

import (
	"net/http"
	"os"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"library/seat-service/handler"
	"library/seat-service/repository"
	"library/seat-service/service"
	shareddb "library/shared/db"
	sharedredis "library/shared/redis"
	"library/shared/middleware"
)

func main() {
	db  := shareddb.NewDB()
	rdb := sharedredis.NewClient()

	repo    := repository.New(db)
	repo.SeedSeats()

	seatSvc := service.NewSeatService(repo, rdb)
	h       := handler.New(seatSvc)

	r := gin.New()
	r.Use(gin.Logger(), gin.Recovery())
	r.Use(cors.Default())

	r.GET("/actuator/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "UP"})
	})

	auth := middleware.AuthRequired()
	seats := r.Group("/api/seats", auth)
	seats.GET("/availability",           h.GetAvailability)
	seats.POST("/book",                  h.BookSeat)
	seats.DELETE("/release/:membershipId", h.ReleaseSeat)
	seats.GET("/my",                     h.GetMyBookings)
	seats.GET("/admin/bookings",         h.GetAdminBookings)

	port := os.Getenv("PORT")
	if port == "" {
		port = "8084"
	}
	r.Run(":" + port)
}
