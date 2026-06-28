package service

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"

	"library/notification-service/repository"
)

type EmailService struct {
	logRepo      *repository.LogRepo
	sendgridKey  string
	fromEmail    string
	fromName     string
	adminEmail   string
}

func NewEmailService(logRepo *repository.LogRepo) *EmailService {
	return &EmailService{
		logRepo:     logRepo,
		sendgridKey: os.Getenv("SENDGRID_API_KEY"),
		fromEmail:   getenv("FROM_EMAIL", "noreply@targetzone.co.in"),
		fromName:    getenv("FROM_NAME", "Target Zone Library"),
		adminEmail:  os.Getenv("ADMIN_EMAIL"),
	}
}

func (s *EmailService) Send(to, subject, body, userID, event string) {
	status := "SENT"
	var errMsg *string

	if s.sendgridKey == "" {
		log.Printf("[DEV] Email to %s [%s]: %s", to, event, body)
	} else {
		if err := s.sendSendGrid(to, subject, body); err != nil {
			log.Printf("[warn] email send failed to %s: %v", to, err)
			status = "FAILED"
			e := err.Error()
			errMsg = &e
		}
	}

	var uid *string
	if userID != "" {
		uid = &userID
	}
	s.logRepo.Save(&repository.NotificationLog{
		UserID:       uid,
		Channel:      "EMAIL",
		Event:        event,
		Recipient:    to,
		Message:      fmt.Sprintf("Subject: %s\n%s", subject, body),
		Status:       status,
		ErrorMessage: errMsg,
	})
}

func (s *EmailService) SendAdmin(subject, body, event string) {
	if s.adminEmail != "" {
		s.Send(s.adminEmail, subject, body, "", event)
	}
}

func (s *EmailService) sendSendGrid(to, subject, body string) error {
	payload := map[string]interface{}{
		"personalizations": []map[string]interface{}{
			{"to": []map[string]string{{"email": to}}},
		},
		"from":    map[string]string{"email": s.fromEmail, "name": s.fromName},
		"subject": subject,
		"content": []map[string]string{
			{"type": "text/plain", "value": body},
		},
	}
	b, _ := json.Marshal(payload)
	req, _ := http.NewRequest("POST", "https://api.sendgrid.com/v3/mail/send", bytes.NewReader(b))
	req.Header.Set("Authorization", "Bearer "+s.sendgridKey)
	req.Header.Set("Content-Type", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return fmt.Errorf("sendgrid API %d", resp.StatusCode)
	}
	return nil
}
