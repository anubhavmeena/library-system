package service

import (
	"fmt"

	"library/notification-service/model"
)

type NotificationService struct {
	wa    *WhatsAppService
	email *EmailService
}

func NewNotificationService(wa *WhatsAppService, email *EmailService) *NotificationService {
	return &NotificationService{wa: wa, email: email}
}

func (s *NotificationService) BookingConfirmed(p model.BookingConfirmedPayload) {
	shiftFmt := formatShift(p.Shift)
	waMsg := fmt.Sprintf(
		"✅ Booking Confirmed!\n\nHi *%s*,\n\n📚 *Target Zone Library — Membership Details*\n━━━━━━━━━━━━━━━━━━━━\n📋 Plan   : %s\n💺 Seat   : %s\n⏰ Shift  : %s\n📅 From   : %s\n📅 To     : %s\n💰 Paid   : ₹%.0f\n━━━━━━━━━━━━━━━━━━━━\n\nPlease carry a valid ID on your first visit.\n\n📍 Happy studying! — Target Zone Library",
		p.UserName, p.PlanName, p.SeatNumber, shiftFmt, p.StartDate, p.EndDate, p.AmountPaid)

	emailBody := fmt.Sprintf(
		"Dear %s,\n\nYour library membership has been confirmed!\n\nMEMBERSHIP DETAILS\n------------------\nPlan        : %s\nSeat Number : %s\nShift       : %s\nStart Date  : %s\nEnd Date    : %s\nAmount Paid : ₹%.0f\n\nLibrary Timings:\n  Morning Shift : 6:00 AM – 2:00 PM\n  Evening Shift : 2:00 PM – 10:00 PM\n\nPlease carry a valid photo ID on your first visit.\n\nBest regards,\nTarget Zone Library Team\nhttps://targetzone.co.in",
		p.UserName, p.PlanName, p.SeatNumber, shiftFmt, p.StartDate, p.EndDate, p.AmountPaid)

	if p.UserMobile != "" {
		s.wa.Send(p.UserMobile, waMsg, p.UserID, "BOOKING_CONFIRMED")
	}
	if p.UserEmail != "" {
		s.email.Send(p.UserEmail, "Membership Confirmed — Target Zone Library", emailBody, p.UserID, "BOOKING_CONFIRMED")
	}

	adminMsg := fmt.Sprintf("📌 New Booking!\nStudent: %s\nSeat: %s | Shift: %s\nPeriod: %s to %s\nAmount: ₹%.0f",
		p.UserName, p.SeatNumber, shiftFmt, p.StartDate, p.EndDate, p.AmountPaid)
	s.wa.SendToAdmins(adminMsg, "BOOKING_CONFIRMED_ADMIN")
}

func (s *NotificationService) Welcome(p model.BookingConfirmedPayload) {
	msg := fmt.Sprintf(
		"🎉 Welcome to Target Zone Library!\n\nHi %s, your account has been created successfully.\n\nYou can now browse our membership plans and book your preferred seat.\n\n📚 Visit: https://targetzone.co.in\n\nHappy studying!",
		p.UserName)

	if p.UserMobile != "" {
		s.wa.Send(p.UserMobile, msg, p.UserID, "USER_REGISTERED")
	}
	if p.UserEmail != "" {
		s.email.Send(p.UserEmail, "Welcome to Target Zone Library! 📚", msg, p.UserID, "USER_REGISTERED")
	}
}

func (s *NotificationService) RenewalReminder(p model.RenewalReminderPayload) {
	switch p.EventType {
	case "SEAT_EXPIRED":
		adminMsg := fmt.Sprintf("🪑 Seat Now Available!\n\nSeat   : %s\nStudent: %s\nExpired: %s\n\nThe seat is free for new booking.",
			p.SeatNumber, p.UserName, p.ExpiryDate)
		s.wa.SendToAdmins(adminMsg, "SEAT_EXPIRED")

	case "PENDING_FEE_REMINDER":
		amount := 0.0
		if p.PendingAmount != nil {
			amount = *p.PendingAmount
		}
		msg := fmt.Sprintf(
			"💰 Pending Fee Reminder\n\nHi %s,\n\nYou have a pending library fee of *₹%.0f*.\n\nPlease visit the library or contact us to clear your dues.\n\n📚 Target Zone Library Team",
			p.UserName, amount)
		if p.UserMobile != "" {
			s.wa.Send(p.UserMobile, msg, p.UserID, "PENDING_FEE_REMINDER")
		}

	default: // RENEWAL_REMINDER
		urgency := "⏰ Reminder"
		if p.DaysRemaining <= 3 {
			urgency = "⚠️ URGENT"
		}
		plural := ""
		if p.DaysRemaining != 1 {
			plural = "s"
		}
		msg := fmt.Sprintf(
			"%s — Membership Expiring!\n\nHi %s,\n\nYour library membership expires on *%s* (%d day%s left).\n\nYour reserved seat is *%s*. Renew now to keep it!\n\n🔗 Renew: https://targetzone.co.in/student/membership\n\n📚 Target Zone Library Team",
			urgency, p.UserName, p.ExpiryDate, p.DaysRemaining, plural, p.SeatNumber)

		emailBody := fmt.Sprintf(
			"Dear %s,\n\nYour library membership expires on %s (%d day%s remaining).\n\nYour reserved seat %s will be released if not renewed.\n\nRenew now: https://targetzone.co.in/student/membership\n\nBest regards,\nTarget Zone Library Team",
			p.UserName, p.ExpiryDate, p.DaysRemaining, plural, p.SeatNumber)

		if p.UserMobile != "" {
			s.wa.Send(p.UserMobile, msg, p.UserID, "RENEWAL_REMINDER")
		}
		if p.UserEmail != "" {
			s.email.Send(p.UserEmail, "Membership Expiring Soon — Target Zone Library", emailBody, p.UserID, "RENEWAL_REMINDER")
		}
	}
}

func (s *NotificationService) Broadcast(p model.BroadcastPayload) {
	msg := fmt.Sprintf("📢 *Target Zone Library*\n\n%s\n\n— Library Management", p.Message)
	if p.Mobile != "" {
		s.wa.Send(p.Mobile, msg, p.UserID, "BROADCAST")
	}
	if p.IsFirst {
		echo := fmt.Sprintf("📢 Broadcast sent!\n\nMessage: %s", p.Message)
		s.wa.SendToAdmins(echo, "BROADCAST_ECHO")
	}
}

func (s *NotificationService) SeatAssistance(p model.SeatAssistancePayload) {
	msg := fmt.Sprintf("🙋 Student needs help at their seat!\n\nName : %s\nSeat : %s",
		p.UserName, p.SeatNumber)
	if p.AdminMobile != "" {
		s.wa.Send(p.AdminMobile, msg, p.UserID, "SEAT_ASSISTANCE")
	}
	s.wa.SendToAdmins(msg, "SEAT_ASSISTANCE")
}

func formatShift(shift string) string {
	switch shift {
	case "MORNING":
		return "Morning (6AM–2PM)"
	case "EVENING":
		return "Evening (2PM–10PM)"
	default:
		return "Full Day (6AM–10PM)"
	}
}
