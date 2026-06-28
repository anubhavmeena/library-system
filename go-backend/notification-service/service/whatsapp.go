package service

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"strings"

	"library/notification-service/repository"
)

type WhatsAppService struct {
	logRepo    *repository.LogRepo
	token      string
	phoneID    string
	apiVersion string
	template   string
	language   string
	enabled    bool
	adminNums  []string
	apitxtKey  string
}

func NewWhatsAppService(logRepo *repository.LogRepo) *WhatsAppService {
	token   := os.Getenv("META_WHATSAPP_TOKEN")
	phoneID := os.Getenv("META_WHATSAPP_PHONE_NUMBER_ID")
	admins  := []string{}
	for _, n := range strings.Split(os.Getenv("ADMIN_WHATSAPP"), ",") {
		n = strings.TrimSpace(n)
		if n != "" {
			admins = append(admins, n)
		}
	}
	return &WhatsAppService{
		logRepo:    logRepo,
		token:      token,
		phoneID:    phoneID,
		apiVersion: getenv("META_WHATSAPP_API_VERSION", "v21.0"),
		template:   getenv("META_NOTIFICATION_TEMPLATE_NAME", "library_notification"),
		language:   getenv("META_WHATSAPP_LANGUAGE", "en_US"),
		enabled:    token != "" && phoneID != "",
		adminNums:  admins,
		apitxtKey:  os.Getenv("APITXT_AUTH_KEY"),
	}
}

func (s *WhatsAppService) Send(mobile, message, userID, event string) {
	mobile = formatMobile(mobile)
	status := "SENT"
	var errMsg *string

	if !s.enabled {
		log.Printf("[DEV] WhatsApp to %s [%s]: %s", mobile, event, message)
	} else {
		if err := s.sendMeta(mobile, sanitizeMessage(message)); err != nil {
			log.Printf("[warn] Meta WhatsApp failed for %s: %v — trying APITXT SMS", mobile, err)
			if s.apitxtKey != "" {
				if smsErr := s.sendApitxtSMS(mobile, message); smsErr != nil {
					log.Printf("[warn] APITXT SMS also failed for %s: %v", mobile, smsErr)
					status = "FAILED"
					e := fmt.Sprintf("meta: %v; apitxt: %v", err, smsErr)
					errMsg = &e
				} else {
					status = "SENT_SMS"
				}
			} else {
				status = "FAILED"
				e := err.Error()
				errMsg = &e
			}
		}
	}

	var uid *string
	if userID != "" {
		uid = &userID
	}
	s.logRepo.Save(&repository.NotificationLog{
		UserID:       uid,
		Channel:      "WHATSAPP",
		Event:        event,
		Recipient:    mobile,
		Message:      message,
		Status:       status,
		ErrorMessage: errMsg,
	})
}

func (s *WhatsAppService) SendToAdmins(message, event string) {
	for _, num := range s.adminNums {
		s.Send(num, message, "", event)
	}
}

func (s *WhatsAppService) sendMeta(mobile, message string) error {
	apiURL := fmt.Sprintf("https://graph.facebook.com/%s/%s/messages", s.apiVersion, s.phoneID)
	body := map[string]interface{}{
		"messaging_product": "whatsapp",
		"to":                mobile,
		"type":              "template",
		"template": map[string]interface{}{
			"name":     s.template,
			"language": map[string]string{"code": s.language},
			"components": []map[string]interface{}{
				{
					"type": "body",
					"parameters": []map[string]interface{}{
						{"type": "text", "text": message},
					},
				},
			},
		},
	}
	b, _ := json.Marshal(body)
	req, _ := http.NewRequest("POST", apiURL, bytes.NewReader(b))
	req.Header.Set("Authorization", "Bearer "+s.token)
	req.Header.Set("Content-Type", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		rb, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("meta API %d: %s", resp.StatusCode, string(rb))
	}
	return nil
}

func (s *WhatsAppService) sendApitxtSMS(mobile, message string) error {
	digits := ""
	for _, r := range mobile {
		if r >= '0' && r <= '9' {
			digits += string(r)
		}
	}
	if len(digits) > 10 {
		digits = digits[len(digits)-10:]
	}
	apiURL := fmt.Sprintf(
		"https://apitxt.com/api/sendSMS?authkey=%s&mobile=91%s&message=%s",
		url.QueryEscape(s.apitxtKey), digits, url.QueryEscape(message))
	resp, err := http.Get(apiURL)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		rb, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("apitxt SMS error %d: %s", resp.StatusCode, string(rb))
	}
	return nil
}

var multiSpace = regexp.MustCompile(`\s{5,}`)

func sanitizeMessage(msg string) string {
	msg = strings.ReplaceAll(msg, "\n", " ")
	msg = strings.ReplaceAll(msg, "\r", " ")
	msg = strings.ReplaceAll(msg, "\t", " ")
	return multiSpace.ReplaceAllString(msg, "    ")
}

func formatMobile(mobile string) string {
	digits := ""
	for _, r := range mobile {
		if r >= '0' && r <= '9' {
			digits += string(r)
		}
	}
	if strings.HasPrefix(mobile, "+") {
		return "+" + digits
	}
	if len(digits) == 10 {
		return "+91" + digits
	}
	return "+" + digits
}

func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
