package main

import (
	"net/http"
	"os"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"library/user-service/handler"
	"library/user-service/repository"
	"library/user-service/service"
	shareddb "library/shared/db"
	"library/shared/middleware"
)

func main() {
	db := shareddb.NewDB()
	repo := repository.New(db)

	userSvc     := service.NewUserService(repo)
	gallerySvc  := service.NewGalleryService(repo)
	feedbackSvc := service.NewFeedbackService(repo)
	h           := handler.New(userSvc, gallerySvc, feedbackSvc)

	r := gin.New()
	r.Use(gin.Logger(), gin.Recovery())
	r.Use(cors.Default())

	r.GET("/actuator/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "UP"})
	})

	uploadDir := os.Getenv("UPLOAD_DIR")
	if uploadDir == "" {
		uploadDir = "/app/uploads"
	}
	r.Static("/uploads", uploadDir)

	auth := middleware.AuthRequired()

	users := r.Group("/api/users", auth)
	users.GET("/admin-contact", h.AdminContact)
	users.GET("/me", h.GetMe)
	users.GET("/:userId", h.GetUser)
	users.PATCH("/me", h.UpdateMe)
	users.POST("/me/photo", h.UploadPhoto)
	users.DELETE("/me/photo", h.DeletePhoto)
	users.POST("/me/aadhaar", h.UploadAadhaar)
	users.DELETE("/me/aadhaar", h.DeleteAadhaar)
	users.POST("/feedback", h.CreateFeedback)
	users.GET("/feedback/my", h.GetMyFeedback)

	gallery := r.Group("/api/gallery", auth)
	gallery.GET("", h.GetGallery)
	gallery.POST("", h.UploadGallery)
	gallery.DELETE("/:id", h.DeleteGallery)

	port := os.Getenv("PORT")
	if port == "" {
		port = "8082"
	}
	r.Run(":" + port)
}
