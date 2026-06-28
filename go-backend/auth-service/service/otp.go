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
	"strings"
)

type OtpService struct {
	metaToken      string
	metaPhoneID    string
	metaAPIVersion string
	metaTemplate   string
	metaLanguage   string
	apitxtKey      string
	twilioSID      string
	twilioToken    string
	twilioFrom     string
	sendgridKey    string
	fromEmail      string
}

func NewOtpService() *OtpService {
	return &OtpService{
		metaToken:      os.Getenv("META_WHATSAPP_TOKEN"),
		metaPhoneID:    os.Getenv("META_WHATSAPP_PHONE_NUMBER_ID"),
		metaAPIVersion: getenv("META_WHATSAPP_API_VERSION", "v21.0"),
		metaTemplate:   getenv("META_WHATSAPP_TEMPLATE_NAME", "library_otp"),
		metaLanguage:   getenv("META_WHATSAPP_LANGUAGE", "en_US"),
		apitxtKey:      os.Getenv("APITXT_AUTH_KEY"),
		twilioSID:      os.Getenv("TWILIO_ACCOUNT_SID"),
		twilioToken:    os.Getenv("TWILIO_AUTH_TOKEN"),
		twilioFrom:     os.Getenv("TWILIO_PHONE_NUMBER"),
		sendgridKey:    os.Getenv("SENDGRID_API_KEY"),
		fromEmail:      getenv("FROM_EMAIL", "noreply@targetzone.co.in"),
	}
}

func (s *OtpService) IsLiveConfigured() bool {
	return s.metaToken != "" || s.apitxtKey != "" || s.twilioSID != "" || s.sendgridKey != ""
}

func (s *OtpService) Send(contact, contactType, otp string) error {
	isEmail := strings.Contains(contact, "@")

	if isEmail {
		return s.sendEmail(contact, otp)
	}

	if s.metaToken != "" && s.metaPhoneID != "" {
		if err := s.sendMetaWhatsApp(contact, otp); err == nil {
			return nil
		} else {
			log.Printf("Meta WhatsApp failed: %v", err)
		}
	}

	if s.apitxtKey != "" {
		if err := s.sendApitxt(contact, otp); err == nil {
			return nil
		} else {
			log.Printf("apitxt failed: %v", err)
		}
	}

	if s.twilioSID != "" {
		if err := s.sendTwilio(contact, otp); err == nil {
			return nil
		} else {
			log.Printf("Twilio failed: %v", err)
		}
	}

	log.Printf("[DEV] OTP for %s: %s", contact, otp)
	return nil
}

func (s *OtpService) sendMetaWhatsApp(mobile, otp string) error {
	mobile = formatMobile(mobile)
	apiURL := fmt.Sprintf("https://graph.facebook.com/%s/%s/messages",
		s.metaAPIVersion, s.metaPhoneID)

	body := map[string]interface{}{
		"messaging_product": "whatsapp",
		"to":                mobile,
		"type":              "template",
		"template": map[string]interface{}{
			"name": s.metaTemplate,
			"language": map[string]string{
				"code": s.metaLanguage,
			},
			"components": []map[string]interface{}{
				{
					"type": "body",
					"parameters": []map[string]interface{}{
						{"type": "text", "text": otp},
					},
				},
				{
					"type":     "button",
					"sub_type": "url",
					"index":    "0",
					"parameters": []map[string]interface{}{
						{"type": "text", "text": otp},
					},
				},
			},
		},
	}

	b, _ := json.Marshal(body)
	req, _ := http.NewRequest("POST", apiURL, bytes.NewReader(b))
	req.Header.Set("Authorization", "Bearer "+s.metaToken)
	req.Header.Set("Content-Type", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		rb, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("meta API error %d: %s", resp.StatusCode, string(rb))
	}
	return nil
}

func (s *OtpService) sendApitxt(mobile, otp string) error {
	digits := stripToDigits(mobile)
	if len(digits) > 10 {
		digits = digits[len(digits)-10:]
	}
	apiURL := fmt.Sprintf(
		"https://apitxt.com/api/sendOTP?authkey=%s&mobile=91%s&otp=%s",
		url.QueryEscape(s.apitxtKey), digits, otp)
	resp, err := http.Get(apiURL)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return fmt.Errorf("apitxt error %d", resp.StatusCode)
	}
	return nil
}

func (s *OtpService) sendTwilio(mobile, otp string) error {
	mobile = formatMobile(mobile)
	apiURL := fmt.Sprintf("https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json", s.twilioSID)
	data := url.Values{
		"From": {s.twilioFrom},
		"To":   {mobile},
		"Body": {fmt.Sprintf("Your OTP is %s. Valid for 5 minutes.", otp)},
	}
	req, _ := http.NewRequest("POST", apiURL, strings.NewReader(data.Encode()))
	req.SetBasicAuth(s.twilioSID, s.twilioToken)
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		rb, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("twilio error %d: %s", resp.StatusCode, string(rb))
	}
	return nil
}

func (s *OtpService) sendEmail(email, otp string) error {
	if s.sendgridKey == "" {
		log.Printf("[DEV] OTP email to %s: %s", email, otp)
		return nil
	}
	body := map[string]interface{}{
		"personalizations": []map[string]interface{}{
			{"to": []map[string]string{{"email": email}}},
		},
		"from":    map[string]string{"email": s.fromEmail, "name": "Target Zone Library"},
		"subject": "Your OTP - Target Zone Library",
		"content": []map[string]string{
			{"type": "text/plain", "value": fmt.Sprintf("Your OTP is %s. Valid for 5 minutes.", otp)},
		},
	}
	b, _ := json.Marshal(body)
	req, _ := http.NewRequest("POST", "https://api.sendgrid.com/v3/mail/send", bytes.NewReader(b))
	req.Header.Set("Authorization", "Bearer "+s.sendgridKey)
	req.Header.Set("Content-Type", "application/json")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	return nil
}

func formatMobile(mobile string) string {
	digits := stripToDigits(mobile)
	if strings.HasPrefix(mobile, "+") {
		return "+" + digits
	}
	if len(digits) == 10 {
		return "+91" + digits
	}
	return "+" + digits
}

func stripToDigits(s string) string {
	var b strings.Builder
	for _, r := range s {
		if r >= '0' && r <= '9' {
			b.WriteRune(r)
		}
	}
	return b.String()
}
