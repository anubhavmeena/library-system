package service

import (
	"fmt"
	"io"
	"log"
	"net/smtp"
	"os"
	"strings"
	"time"

	"github.com/emersion/go-imap"
	"github.com/emersion/go-imap/client"
	"github.com/emersion/go-message/mail"
	"library/admin-service/model"
)

type MailboxService struct{}

func NewMailboxService() *MailboxService {
	return &MailboxService{}
}

func (s *MailboxService) connect() (*client.Client, error) {
	host := os.Getenv("IMAP_HOST")
	port := os.Getenv("IMAP_PORT")
	if port == "" {
		port = "993"
	}
	c, err := client.DialTLS(host+":"+port, nil)
	if err != nil {
		return nil, err
	}
	if err := c.Login(os.Getenv("EMAIL_FROM"), os.Getenv("EMAIL_PASSWORD")); err != nil {
		c.Logout()
		return nil, err
	}
	return c, nil
}

func (s *MailboxService) List(folder string) ([]model.InboxSummaryDTO, error) {
	if os.Getenv("IMAP_HOST") == "" {
		return []model.InboxSummaryDTO{}, nil
	}
	c, err := s.connect()
	if err != nil {
		log.Printf("[mailbox] connect error: %v", err)
		return []model.InboxSummaryDTO{}, nil
	}
	defer c.Logout()

	if folder == "" {
		folder = "INBOX"
	}
	mbox, err := c.Select(folder, true)
	if err != nil {
		return nil, err
	}
	if mbox.Messages == 0 {
		return []model.InboxSummaryDTO{}, nil
	}

	from := uint32(1)
	if mbox.Messages > 50 {
		from = mbox.Messages - 49
	}
	seqSet := new(imap.SeqSet)
	seqSet.AddRange(from, mbox.Messages)

	messages := make(chan *imap.Message, 50)
	done := make(chan error, 1)
	go func() {
		done <- c.Fetch(seqSet, []imap.FetchItem{imap.FetchEnvelope, imap.FetchFlags}, messages)
	}()

	var summaries []model.InboxSummaryDTO
	for msg := range messages {
		env := msg.Envelope
		from := ""
		if len(env.From) > 0 {
			from = env.From[0].Address()
		}
		isRead := false
		for _, f := range msg.Flags {
			if f == imap.SeenFlag {
				isRead = true
			}
		}
		summaries = append(summaries, model.InboxSummaryDTO{
			MessageNumber: msg.SeqNum,
			From:          from,
			Subject:       env.Subject,
			Date:          env.Date.Format(time.RFC3339),
			IsRead:        isRead,
		})
	}
	if err := <-done; err != nil {
		return nil, err
	}
	// Return newest first
	for i, j := 0, len(summaries)-1; i < j; i, j = i+1, j-1 {
		summaries[i], summaries[j] = summaries[j], summaries[i]
	}
	return summaries, nil
}

func (s *MailboxService) GetMessage(folder string, seqNum uint32) (*model.InboxMessageDTO, error) {
	if os.Getenv("IMAP_HOST") == "" {
		return nil, fmt.Errorf("IMAP not configured")
	}
	c, err := s.connect()
	if err != nil {
		return nil, err
	}
	defer c.Logout()

	if folder == "" {
		folder = "INBOX"
	}
	c.Select(folder, false)

	seqSet := new(imap.SeqSet)
	seqSet.AddNum(seqNum)

	messages := make(chan *imap.Message, 1)
	done := make(chan error, 1)
	go func() {
		done <- c.Fetch(seqSet, []imap.FetchItem{
			imap.FetchEnvelope, imap.FetchBody, imap.FetchBodyStructure,
			"BODY[]",
		}, messages)
	}()

	var result *model.InboxMessageDTO
	for msg := range messages {
		env := msg.Envelope
		from := ""
		if len(env.From) > 0 {
			from = env.From[0].Address()
		}
		body := extractBody(msg)
		result = &model.InboxMessageDTO{
			MessageNumber: msg.SeqNum,
			From:          from,
			Subject:       env.Subject,
			Date:          env.Date.Format(time.RFC3339),
			Body:          body,
		}
	}
	<-done
	if result == nil {
		return nil, fmt.Errorf("message not found")
	}
	return result, nil
}

func (s *MailboxService) DeleteMessage(folder string, seqNum uint32) error {
	if os.Getenv("IMAP_HOST") == "" {
		return fmt.Errorf("IMAP not configured")
	}
	c, err := s.connect()
	if err != nil {
		return err
	}
	defer c.Logout()

	if folder == "" {
		folder = "INBOX"
	}
	if _, err := c.Select(folder, false); err != nil {
		return err
	}

	seqSet := new(imap.SeqSet)
	seqSet.AddNum(seqNum)

	flags := []interface{}{imap.DeletedFlag}
	if err := c.Store(seqSet, "+FLAGS", flags, nil); err != nil {
		return err
	}
	return c.Expunge(nil)
}

func (s *MailboxService) Reply(folder string, seqNum uint32, body string) error {
	if os.Getenv("IMAP_HOST") == "" {
		return fmt.Errorf("IMAP not configured")
	}
	orig, err := s.GetMessage(folder, seqNum)
	if err != nil {
		return err
	}

	host := os.Getenv("SMTP_HOST")
	port := os.Getenv("SMTP_PORT")
	if port == "" {
		port = "587"
	}
	user := os.Getenv("EMAIL_FROM")
	pass := os.Getenv("EMAIL_PASSWORD")

	auth := smtp.PlainAuth("", user, pass, host)

	subject := orig.Subject
	if !strings.HasPrefix(subject, "Re: ") {
		subject = "Re: " + subject
	}

	msg := fmt.Sprintf("From: %s\r\nTo: %s\r\nSubject: %s\r\n\r\n%s", user, orig.From, subject, body)
	return smtp.SendMail(host+":"+port, auth, user, []string{orig.From}, []byte(msg))
}

func extractBody(msg *imap.Message) string {
	for _, literal := range msg.Body {
		mr, err := mail.CreateReader(literal)
		if err != nil {
			b, _ := io.ReadAll(literal)
			return string(b)
		}
		for {
			part, err := mr.NextPart()
			if err != nil {
				break
			}
			switch part.Header.(type) {
			case *mail.InlineHeader:
				b, _ := io.ReadAll(part.Body)
				return string(b)
			}
		}
	}
	return ""
}
