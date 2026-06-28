package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"library/user-service/model"
	"library/user-service/service"
	"library/shared/middleware"
	"library/shared/response"
)

type Handler struct {
	userSvc     *service.UserService
	gallerySvc  *service.GalleryService
	feedbackSvc *service.FeedbackService
}

func New(u *service.UserService, g *service.GalleryService, f *service.FeedbackService) *Handler {
	return &Handler{userSvc: u, gallerySvc: g, feedbackSvc: f}
}

func (h *Handler) AdminContact(c *gin.Context) {
	data, err := h.userSvc.GetAdminContact()
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) GetMe(c *gin.Context) {
	data, err := h.userSvc.GetMe(middleware.GetUserID(c))
	if err != nil {
		c.JSON(http.StatusNotFound, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) GetUser(c *gin.Context) {
	data, err := h.userSvc.GetUser(c.Param("userId"))
	if err != nil {
		c.JSON(http.StatusNotFound, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) UpdateMe(c *gin.Context) {
	var req model.UpdateProfileRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	data, err := h.userSvc.UpdateMe(middleware.GetUserID(c), &req)
	if err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) UploadPhoto(c *gin.Context) {
	file, header, err := c.Request.FormFile("file")
	if err != nil {
		c.JSON(http.StatusBadRequest, response.Fail("file is required"))
		return
	}
	defer file.Close()

	url, err := h.userSvc.UploadPhoto(middleware.GetUserID(c), file, header)
	if err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(model.PhotoUploadResponse{PhotoURL: url, Message: "Photo uploaded successfully"}))
}

func (h *Handler) DeletePhoto(c *gin.Context) {
	if err := h.userSvc.DeletePhoto(middleware.GetUserID(c)); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("Photo removed successfully"))
}

func (h *Handler) UploadAadhaar(c *gin.Context) {
	file, header, err := c.Request.FormFile("file")
	if err != nil {
		c.JSON(http.StatusBadRequest, response.Fail("file is required"))
		return
	}
	defer file.Close()

	url, err := h.userSvc.UploadAadhaar(middleware.GetUserID(c), file, header)
	if err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(model.PhotoUploadResponse{PhotoURL: url, Message: "Aadhaar uploaded successfully"}))
}

func (h *Handler) DeleteAadhaar(c *gin.Context) {
	if err := h.userSvc.DeleteAadhaar(middleware.GetUserID(c)); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("Aadhaar removed successfully"))
}

func (h *Handler) CreateFeedback(c *gin.Context) {
	var req model.CreateFeedbackRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	data, err := h.feedbackSvc.Create(middleware.GetUserID(c), req)
	if err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) GetMyFeedback(c *gin.Context) {
	data, err := h.feedbackSvc.GetMy(middleware.GetUserID(c))
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) GetGallery(c *gin.Context) {
	data, err := h.gallerySvc.GetAll()
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) UploadGallery(c *gin.Context) {
	if middleware.GetUserRole(c) != "ADMIN" {
		c.JSON(http.StatusForbidden, response.Fail("forbidden"))
		return
	}
	file, header, err := c.Request.FormFile("file")
	if err != nil {
		c.JSON(http.StatusBadRequest, response.Fail("file is required"))
		return
	}
	defer file.Close()

	caption := c.PostForm("caption")
	data, err := h.gallerySvc.Upload(middleware.GetUserID(c), file, header, caption)
	if err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) DeleteGallery(c *gin.Context) {
	if middleware.GetUserRole(c) != "ADMIN" {
		c.JSON(http.StatusForbidden, response.Fail("forbidden"))
		return
	}
	if err := h.gallerySvc.Delete(c.Param("id")); err != nil {
		c.JSON(http.StatusNotFound, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("Photo deleted"))
}
