package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"library/notification-service/model"
	"library/notification-service/service"
	"library/shared/response"
)

type Handler struct {
	svc *service.NotificationService
}

func New(svc *service.NotificationService) *Handler {
	return &Handler{svc: svc}
}

func (h *Handler) BookingConfirmed(c *gin.Context) {
	var p model.BookingConfirmedPayload
	if err := c.ShouldBindJSON(&p); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	go h.svc.BookingConfirmed(p)
	c.JSON(http.StatusOK, response.SuccessMsg("queued"))
}

func (h *Handler) Welcome(c *gin.Context) {
	var p model.BookingConfirmedPayload
	if err := c.ShouldBindJSON(&p); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	go h.svc.Welcome(p)
	c.JSON(http.StatusOK, response.SuccessMsg("queued"))
}

func (h *Handler) RenewalReminder(c *gin.Context) {
	var p model.RenewalReminderPayload
	if err := c.ShouldBindJSON(&p); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	go h.svc.RenewalReminder(p)
	c.JSON(http.StatusOK, response.SuccessMsg("queued"))
}

func (h *Handler) Broadcast(c *gin.Context) {
	var p model.BroadcastPayload
	if err := c.ShouldBindJSON(&p); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	go h.svc.Broadcast(p)
	c.JSON(http.StatusOK, response.SuccessMsg("queued"))
}

func (h *Handler) SeatAssistance(c *gin.Context) {
	var p model.SeatAssistancePayload
	if err := c.ShouldBindJSON(&p); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	go h.svc.SeatAssistance(p)
	c.JSON(http.StatusOK, response.SuccessMsg("queued"))
}
