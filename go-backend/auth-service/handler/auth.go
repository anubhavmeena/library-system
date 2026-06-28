package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"library/auth-service/model"
	"library/auth-service/service"
	"library/shared/response"
)

type AuthHandler struct {
	svc *service.AuthService
}

func NewAuthHandler(svc *service.AuthService) *AuthHandler {
	return &AuthHandler{svc: svc}
}

func (h *AuthHandler) SendOTP(c *gin.Context) {
	var req model.SendOTPRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	if err := h.svc.SendOTP(req.Contact, req.ContactType); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("OTP sent successfully to "+req.Contact))
}

func (h *AuthHandler) VerifyOTP(c *gin.Context) {
	var req model.VerifyOTPRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	res, err := h.svc.VerifyOTP(req.Contact, req.OTP)
	if err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(res))
}

func (h *AuthHandler) Register(c *gin.Context) {
	var req model.RegisterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	res, err := h.svc.Register(req)
	if err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(res))
}

func (h *AuthHandler) Login(c *gin.Context) {
	var req model.LoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	res, err := h.svc.Login(req)
	if err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(res))
}

func (h *AuthHandler) AdminLogin(c *gin.Context) {
	var req model.AdminLoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	res, err := h.svc.AdminLogin(req.Contact)
	if err != nil {
		c.JSON(http.StatusUnauthorized, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(res))
}
