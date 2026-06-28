package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"library/seat-service/model"
	"library/seat-service/service"
	"library/shared/middleware"
	"library/shared/response"
)

type Handler struct {
	svc *service.SeatService
}

func New(svc *service.SeatService) *Handler {
	return &Handler{svc: svc}
}

func (h *Handler) GetAvailability(c *gin.Context) {
	data, err := h.svc.GetAvailability(c.Query("shift"), c.Query("date"))
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) BookSeat(c *gin.Context) {
	var req model.BookSeatRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	data, err := h.svc.BookSeat(middleware.GetUserID(c), req)
	if err != nil {
		c.JSON(http.StatusConflict, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) ReleaseSeat(c *gin.Context) {
	if err := h.svc.ReleaseSeat(c.Param("membershipId")); err != nil {
		c.JSON(http.StatusNotFound, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("seat released"))
}

func (h *Handler) GetMyBookings(c *gin.Context) {
	data, err := h.svc.GetMyBookings(middleware.GetUserID(c))
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) GetAdminBookings(c *gin.Context) {
	// Admin view — same as availability but returns list of bookings
	data, err := h.svc.GetAvailability(c.Query("shift"), c.Query("date"))
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}
