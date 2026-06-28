package handler

import (
	"fmt"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"library/admin-service/model"
	"library/admin-service/repository"
	"library/admin-service/service"
	"library/shared/response"
)

type Handler struct {
	admin     *service.AdminService
	seatAdmin *service.SeatAdminService
	broadcast *service.BroadcastService
	feedback  *service.FeedbackService
	report    *service.ReportService
	mailbox   *service.MailboxService
	importer  *service.ImportService
	repo      *repository.Repo
}

func New(
	admin *service.AdminService,
	seatAdmin *service.SeatAdminService,
	broadcast *service.BroadcastService,
	feedback *service.FeedbackService,
	report *service.ReportService,
	mailbox *service.MailboxService,
	importer *service.ImportService,
	repo *repository.Repo,
) *Handler {
	return &Handler{
		admin: admin, seatAdmin: seatAdmin, broadcast: broadcast,
		feedback: feedback, report: report, mailbox: mailbox,
		importer: importer, repo: repo,
	}
}

// ---- Dashboard ----

func (h *Handler) GetDashboard(c *gin.Context) {
	data, err := h.admin.GetDashboard()
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) TrackVisitor(c *gin.Context) {
	var req struct {
		Page string `json:"page"`
	}
	c.ShouldBindJSON(&req)
	h.admin.TrackVisitor(req.Page)
	c.JSON(http.StatusOK, response.SuccessMsg("tracked"))
}

// ---- Students ----

func (h *Handler) ListStudents(c *gin.Context) {
	search := c.Query("search")
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	size, _ := strconv.Atoi(c.DefaultQuery("size", "20"))
	data, err := h.admin.ListStudents(search, page, size)
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) GetStudent(c *gin.Context) {
	data, err := h.admin.GetStudent(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusNotFound, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) UpdateStudentStatus(c *gin.Context) {
	var req model.UpdateStatusRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	if err := h.admin.UpdateStudentStatus(c.Param("id"), req.IsActive); err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("updated"))
}

func (h *Handler) UpdateStudent(c *gin.Context) {
	var req model.UpdateStudentRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	if err := h.admin.UpdateStudentFields(c.Param("id"), req); err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("updated"))
}

func (h *Handler) GetStudentPayments(c *gin.Context) {
	data, err := h.admin.GetStudentPayments(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

// ---- Seats ----

func (h *Handler) DeleteStudent(c *gin.Context) {
	if err := h.admin.DeleteStudent(c.Param("id")); err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("student deleted"))
}

func (h *Handler) GetExpiringMemberships(c *gin.Context) {
	days := 7
	if d := c.Query("withinDays"); d != "" {
		fmt.Sscanf(d, "%d", &days)
	}
	data, err := h.admin.GetExpiringMemberships(days)
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) ClearPendingFees(c *gin.Context) {
	if err := h.admin.ClearPendingFees(c.Param("id")); err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("pending fees cleared"))
}

func (h *Handler) SendPendingFeeReminders(c *gin.Context) {
	var req struct {
		UserIDs []string `json:"userIds"`
	}
	c.ShouldBindJSON(&req)
	count, err := h.admin.SendPendingFeeReminders(req.UserIDs)
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg(fmt.Sprintf("reminders queued for %d students", count)))
}

func (h *Handler) SendDirectMessage(c *gin.Context) {
	var req struct {
		Message string `json:"message"`
	}
	if err := c.ShouldBindJSON(&req); err != nil || req.Message == "" {
		c.JSON(http.StatusBadRequest, response.Fail("message required"))
		return
	}
	if err := h.admin.SendDirectMessage(c.Param("id"), req.Message); err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("message sent"))
}

func (h *Handler) ImportSingleStudent(c *gin.Context) {
	var req model.ManualStudentImportRequest
	if err := c.ShouldBindJSON(&req); err != nil || req.Name == "" || req.Phone == "" {
		c.JSON(http.StatusBadRequest, response.Fail("name and phone required"))
		return
	}
	if err := h.importer.ImportSingleStudent(req); err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("student added successfully"))
}

func (h *Handler) ChangeMembershipPlan(c *gin.Context) {
	var req struct {
		PlanID string `json:"planId"`
	}
	if err := c.ShouldBindJSON(&req); err != nil || req.PlanID == "" {
		c.JSON(http.StatusBadRequest, response.Fail("planId required"))
		return
	}
	if err := h.seatAdmin.ChangeMembershipPlan(c.Param("membershipId"), req.PlanID); err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("plan updated"))
}

func (h *Handler) GetPaymentBreakdown(c *gin.Context) {
	data, err := h.report.GetPaymentBreakdown(c.Query("from"), c.Query("to"))
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) GetDailyPayments(c *gin.Context) {
	data, err := h.report.GetDailyPayments(c.Query("date"))
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) GetSeatMap(c *gin.Context) {
	data, err := h.seatAdmin.GetSeatMap(c.Query("shift"), c.Query("date"))
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) CreateCashMembership(c *gin.Context) {
	var req model.CreateCashMembershipRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	data, err := h.seatAdmin.CreateCashMembership(req)
	if err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) ChangeSeat(c *gin.Context) {
	var req model.ChangeSeatRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	if err := h.seatAdmin.ChangeSeat(c.Param("membershipId"), req.SeatNumber); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("seat changed"))
}

// ---- Reminders ----

func (h *Handler) SendReminders(c *gin.Context) {
	var req model.SendReminderRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	// Fire and forget for each user ID
	for _, id := range req.UserIDs {
		go func(uid string) {
			rows, _, _ := h.repo.FindStudents("", 1, 1)
			_ = rows
			// Fetch user and active membership, then send reminder
		}(id)
	}
	c.JSON(http.StatusOK, response.SuccessMsg("reminders queued"))
}

// ---- Broadcast ----

func (h *Handler) Broadcast(c *gin.Context) {
	var req model.BroadcastRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	count, err := h.broadcast.Send(req.Message)
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(gin.H{"sentTo": count}))
}

func (h *Handler) BroadcastHistory(c *gin.Context) {
	data, err := h.broadcast.GetHistory()
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

// ---- Feedback ----

func (h *Handler) ListFeedback(c *gin.Context) {
	data, err := h.feedback.List(c.Query("status"))
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) UpdateFeedback(c *gin.Context) {
	var req model.UpdateFeedbackRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	if err := h.feedback.Update(c.Param("id"), req); err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("updated"))
}

// ---- Reports ----

func (h *Handler) GetRevenue(c *gin.Context) {
	data, err := h.report.GetRevenue(c.Query("from"), c.Query("to"))
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) ExportStudents(c *gin.Context) {
	rows, _, err := h.repo.FindStudents("", 1, 10000)
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	data, err := h.report.ExportStudentsExcel(rows)
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.Header("Content-Disposition", "attachment; filename=students.xlsx")
	c.Data(http.StatusOK, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", data)
}

func (h *Handler) GetPendingFee(c *gin.Context) {
	data, err := h.report.GetPendingFee()
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

// ---- Expenses ----

func (h *Handler) GetExpense(c *gin.Context) {
	year, _ := strconv.Atoi(c.Query("year"))
	month, _ := strconv.Atoi(c.Query("month"))
	data, err := h.report.GetExpense(year, month)
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) SaveExpense(c *gin.Context) {
	var req model.SaveExpenseRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	data, err := h.report.SaveExpense(req)
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

// ---- Mailbox ----

func (h *Handler) GetInbox(c *gin.Context) {
	data, err := h.mailbox.List(c.Query("folder"))
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) GetMessage(c *gin.Context) {
	seqNum, _ := strconv.ParseUint(c.Param("seqNum"), 10, 32)
	data, err := h.mailbox.GetMessage(c.Query("folder"), uint32(seqNum))
	if err != nil {
		c.JSON(http.StatusNotFound, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) ReplyMessage(c *gin.Context) {
	seqNum, _ := strconv.ParseUint(c.Param("seqNum"), 10, 32)
	var req model.ReplyRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	if err := h.mailbox.Reply(c.Query("folder"), uint32(seqNum), req.Body); err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("reply sent"))
}

func (h *Handler) DeleteMessage(c *gin.Context) {
	seqNum, _ := strconv.ParseUint(c.Param("seqNum"), 10, 32)
	if err := h.mailbox.DeleteMessage(c.Query("folder"), uint32(seqNum)); err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("message deleted"))
}

// ---- Import ----

func (h *Handler) ImportStudents(c *gin.Context) {
	file, header, err := c.Request.FormFile("file")
	if err != nil {
		c.JSON(http.StatusBadRequest, response.Fail("file required"))
		return
	}
	defer file.Close()
	data, err := h.importer.ImportFile(file, header.Filename)
	if err != nil {
		c.JSON(http.StatusBadRequest, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(data))
}

func (h *Handler) GetPlans(c *gin.Context) {
	plans, err := h.repo.FindAllPlans()
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, response.Success(plans))
}
