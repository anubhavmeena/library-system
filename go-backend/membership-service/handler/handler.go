package handler

import (
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"library/membership-service/model"
	"library/membership-service/repository"
	"library/membership-service/service"
	"library/shared/middleware"
	"library/shared/response"
)

type Handler struct {
	repo      *repository.Repo
	paymentSvc *service.PaymentService
	idCardSvc  *service.IDCardService
}

func New(repo *repository.Repo, ps *service.PaymentService, ic *service.IDCardService) *Handler {
	return &Handler{repo: repo, paymentSvc: ps, idCardSvc: ic}
}

func (h *Handler) GetPlans(c *gin.Context) {
	plans, err := h.repo.FindAllActivePlans()
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	dtos := make([]model.PlanDTO, len(plans))
	for i, p := range plans {
		dtos[i] = model.PlanDTO{
			ID: p.ID.String(), Name: p.Name, PlanType: p.PlanType,
			Price: p.Price, DurationDays: p.DurationDays,
			Description: p.Description, IsActive: p.IsActive,
		}
	}
	c.JSON(http.StatusOK, response.Success(dtos))
}

func (h *Handler) GetMyMembership(c *gin.Context) {
	m, err := h.repo.FindActiveMembership(middleware.GetUserID(c))
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	if m == nil {
		c.JSON(http.StatusOK, response.Success[any](nil))
		return
	}
	plan, _ := h.repo.FindPlanByID(m.PlanID.String())
	c.JSON(http.StatusOK, response.Success(toDTO(m, plan)))
}

func (h *Handler) GetMyQueuedMembership(c *gin.Context) {
	m, err := h.repo.FindQueuedMembership(middleware.GetUserID(c))
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	if m == nil {
		c.JSON(http.StatusOK, response.Success[any](nil))
		return
	}
	plan, _ := h.repo.FindPlanByID(m.PlanID.String())
	c.JSON(http.StatusOK, response.Success(toDTO(m, plan)))
}

func (h *Handler) GetAllMemberships(c *gin.Context) {
	items, err := h.repo.FindAllMemberships(middleware.GetUserID(c))
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	dtos := make([]model.MembershipDTO, len(items))
	for i, m := range items {
		plan, _ := h.repo.FindPlanByID(m.PlanID.String())
		d := toDTO(&m, plan)
		dtos[i] = *d
	}
	c.JSON(http.StatusOK, response.Success(dtos))
}

func (h *Handler) GetMyPayments(c *gin.Context) {
	payments, err := h.repo.FindPaymentsByUserID(middleware.GetUserID(c))
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	dtos := make([]model.PaymentDTO, len(payments))
	for i, p := range payments {
		dtos[i] = model.PaymentDTO{
			ID: p.ID.String(), MembershipID: p.MembershipID.String(),
			Amount: p.Amount, PendingAmount: p.PendingAmount,
			PaymentGateway: p.PaymentGateway, GatewayOrderID: p.GatewayOrderID,
			GatewayPaymentID: p.GatewayPaymentID, Status: p.Status,
			CreatedAt: p.CreatedAt.Format(time.RFC3339),
		}
	}
	c.JSON(http.StatusOK, response.Success(dtos))
}

func (h *Handler) CreateOrder(c *gin.Context) {
	var req model.CreateOrderRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	res, err := h.paymentSvc.CreateOrder(middleware.GetUserID(c), req)
	if err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(res))
}

func (h *Handler) VerifyPayment(c *gin.Context) {
	var req model.PaymentVerifyRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	res, err := h.paymentSvc.VerifyAndActivate(middleware.GetUserID(c), req)
	if err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(res))
}

func (h *Handler) GetIDCard(c *gin.Context) {
	pdfBytes, err := h.idCardSvc.GeneratePDF(middleware.GetUserID(c))
	if err != nil {
		c.JSON(http.StatusNotFound, response.Fail(err.Error()))
		return
	}
	c.Header("Content-Type", "application/pdf")
	c.Header("Content-Disposition", "attachment; filename=id-card.pdf")
	c.Data(http.StatusOK, "application/pdf", pdfBytes)
}

func (h *Handler) CallAdmin(c *gin.Context) {
	if err := h.paymentSvc.CallAdmin(middleware.GetUserID(c)); err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("Admin notified"))
}

func toDTO(m *model.Membership, plan *model.Plan) *model.MembershipDTO {
	dto := &model.MembershipDTO{
		ID:         m.ID.String(),
		UserID:     m.UserID.String(),
		PlanID:     m.PlanID.String(),
		SeatNumber: m.SeatNumber,
		Shift:      m.Shift,
		StartDate:  m.StartDate.Format("2006-01-02"),
		EndDate:    m.EndDate.Format("2006-01-02"),
		Status:     m.Status,
		CreatedAt:  m.CreatedAt.Format(time.RFC3339),
	}
	if plan != nil {
		dto.PlanName = plan.Name
		dto.PlanType = plan.PlanType
	}
	return dto
}
