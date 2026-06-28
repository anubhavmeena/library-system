package scheduler

import (
	"log"
	"time"

	"github.com/jmoiron/sqlx"
	"library/admin-service/model"
	"library/admin-service/service"
)

func SendExpiryReminders(db *sqlx.DB, notif *service.NotifClient) {
	today := time.Now().Truncate(24 * time.Hour)
	in7 := today.AddDate(0, 0, 7)

	type row struct {
		model.Membership
		UserName   string  `db:"user_name"`
		UserMobile *string `db:"user_mobile"`
		UserEmail  *string `db:"user_email"`
	}

	var items []row
	err := db.Select(&items, `
		SELECT m.*, u.name as user_name, u.mobile as user_mobile, u.email as user_email
		FROM memberships m
		JOIN users u ON u.id = m.user_id
		WHERE m.status='ACTIVE'
		  AND m.end_date BETWEEN $1 AND $2
		  AND m.reminder_sent = false
	`, today, in7)
	if err != nil {
		log.Printf("[scheduler] expiry query error: %v", err)
		return
	}

	for _, item := range items {
		daysLeft := int(item.EndDate.Sub(today).Hours() / 24)
		if daysLeft != 7 && daysLeft != 3 {
			continue
		}

		mobile := ""
		if item.UserMobile != nil {
			mobile = *item.UserMobile
		}
		email := ""
		if item.UserEmail != nil {
			email = *item.UserEmail
		}
		seat := ""
		if item.SeatNumber != nil {
			seat = *item.SeatNumber
		}

		notif.RenewalReminder(
			item.UserID.String(), item.ID.String(), item.UserName,
			mobile, email, seat, item.EndDate.Format("2006-01-02"),
			daysLeft, "RENEWAL_REMINDER", nil,
		)

		db.Exec(`UPDATE memberships SET reminder_sent=true WHERE id=$1`, item.ID)
		log.Printf("[scheduler] reminder sent for membership %s (day %d)", item.ID, daysLeft)
	}
}

func MarkExpiredAndNotify(db *sqlx.DB, notif *service.NotifClient) {
	today := time.Now().Truncate(24 * time.Hour)

	type row struct {
		model.Membership
		UserName   string  `db:"user_name"`
		UserMobile *string `db:"user_mobile"`
		UserEmail  *string `db:"user_email"`
	}

	var expired []row
	err := db.Select(&expired, `
		SELECT m.*, u.name as user_name, u.mobile as user_mobile, u.email as user_email
		FROM memberships m
		JOIN users u ON u.id = m.user_id
		WHERE m.status='ACTIVE' AND m.end_date < $1
	`, today)
	if err != nil {
		log.Printf("[scheduler] expired query error: %v", err)
		return
	}

	for _, item := range expired {
		// Mark expired
		db.Exec(`UPDATE memberships SET status='EXPIRED' WHERE id=$1`, item.ID)

		// Activate any queued membership for same user
		db.Exec(`
			UPDATE memberships SET status='ACTIVE'
			WHERE user_id=$1 AND status='QUEUED'
			LIMIT 1
		`, item.UserID)

		seat := ""
		if item.SeatNumber != nil {
			seat = *item.SeatNumber
		}
		mobile := ""
		if item.UserMobile != nil {
			mobile = *item.UserMobile
		}
		email := ""
		if item.UserEmail != nil {
			email = *item.UserEmail
		}

		// Notify admin seat is free
		notif.RenewalReminder(
			item.UserID.String(), item.ID.String(), item.UserName,
			mobile, email, seat, item.EndDate.Format("2006-01-02"),
			0, "SEAT_EXPIRED", nil,
		)
		log.Printf("[scheduler] marked membership %s as EXPIRED", item.ID)
	}
}
