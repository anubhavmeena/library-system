package main

import (
	"log"
	"net/http"
	"os"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/robfig/cron/v3"
	"library/admin-service/handler"
	"library/admin-service/repository"
	"library/admin-service/scheduler"
	"library/admin-service/service"
	shareddb "library/shared/db"
	"library/shared/middleware"
)

func main() {
	db    := shareddb.NewDB()
	repo  := repository.New(db)
	notif := service.NewNotifClient()

	adminSvc    := service.NewAdminService(repo, notif)
	seatSvc     := service.NewSeatAdminService(repo, notif)
	broadcastSvc := service.NewBroadcastService(repo, notif)
	feedbackSvc := service.NewFeedbackService(repo)
	reportSvc   := service.NewReportService(repo)
	mailboxSvc  := service.NewMailboxService()
	importSvc   := service.NewImportService(repo)

	h := handler.New(adminSvc, seatSvc, broadcastSvc, feedbackSvc, reportSvc, mailboxSvc, importSvc, repo)

	// Cron scheduler (single replica assumed — no ShedLock needed at this scale)
	c := cron.New()
	c.AddFunc("0 9 * * *", func() {
		log.Println("[cron] running expiry reminders")
		scheduler.SendExpiryReminders(db, notif)
	})
	c.AddFunc("0 10 * * *", func() {
		log.Println("[cron] marking expired memberships")
		scheduler.MarkExpiredAndNotify(db, notif)
	})
	c.Start()
	defer c.Stop()

	r := gin.New()
	r.Use(gin.Logger(), gin.Recovery())
	r.Use(cors.Default())

	r.GET("/actuator/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "UP"})
	})

	// Visitor tracking — public (api-gateway routes this without auth)
	r.POST("/api/visitor/track", h.TrackVisitor)

	admin := r.Group("/api/admin", middleware.AuthRequired(), middleware.AdminRequired())

	// Dashboard
	admin.GET("/dashboard", h.GetDashboard)

	// Students
	admin.GET("/students", h.ListStudents)
	admin.GET("/students/pending-fees", h.GetPendingFee)
	admin.GET("/students/:id", h.GetStudent)
	admin.PATCH("/students/:id/status", h.UpdateStudentStatus)
	admin.PATCH("/students/:id", h.UpdateStudent)
	admin.DELETE("/students/:id", h.DeleteStudent)
	admin.GET("/students/:id/payments", h.GetStudentPayments)
	admin.PATCH("/students/:id/clear-pending-fees", h.ClearPendingFees)
	admin.POST("/students/:id/message", h.SendDirectMessage)
	admin.POST("/students/import", h.ImportStudents)
	admin.POST("/students/import/single", h.ImportSingleStudent)

	// Seats & memberships
	admin.GET("/seats/map", h.GetSeatMap)
	admin.POST("/memberships/cash", h.CreateCashMembership)
	admin.PATCH("/memberships/:membershipId/seat", h.ChangeSeat)
	admin.PATCH("/memberships/:membershipId/plan", h.ChangeMembershipPlan)
	admin.GET("/memberships/expiring", h.GetExpiringMemberships)
	admin.GET("/plans", h.GetPlans)

	// Reminders & Broadcast
	admin.POST("/reminders/send", h.SendReminders)
	admin.POST("/reminders/pending-fees", h.SendPendingFeeReminders)
	admin.POST("/broadcast", h.Broadcast)
	admin.GET("/broadcast/history", h.BroadcastHistory)

	// Feedback
	admin.GET("/feedback", h.ListFeedback)
	admin.PATCH("/feedback/:id", h.UpdateFeedback)

	// Reports
	admin.GET("/reports/revenue", h.GetRevenue)
	admin.GET("/reports/export/students", h.ExportStudents)
	admin.GET("/reports/payments/breakdown", h.GetPaymentBreakdown)
	admin.GET("/reports/payments/daily", h.GetDailyPayments)

	// Expenses
	admin.GET("/expenses", h.GetExpense)
	admin.POST("/expenses", h.SaveExpense)

	// Inbox (mailbox)
	admin.GET("/inbox", h.GetInbox)
	admin.GET("/inbox/:seqNum", h.GetMessage)
	admin.POST("/inbox/:seqNum/reply", h.ReplyMessage)
	admin.DELETE("/inbox/:seqNum", h.DeleteMessage)

	port := os.Getenv("PORT")
	if port == "" {
		port = "8086"
	}
	r.Run(":" + port)
}
