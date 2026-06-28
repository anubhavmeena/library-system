package repository

import (
	"log"
	"time"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
)

type NotificationLog struct {
	ID           uuid.UUID  `db:"id"`
	UserID       *string    `db:"user_id"`
	Channel      string     `db:"channel"`
	Event        string     `db:"event"`
	Recipient    string     `db:"recipient"`
	Message      string     `db:"message"`
	Status       string     `db:"status"`
	ErrorMessage *string    `db:"error_message"`
	SentAt       time.Time  `db:"sent_at"`
	CreatedAt    time.Time  `db:"created_at"`
}

type LogRepo struct {
	db *sqlx.DB
}

func NewLogRepo(db *sqlx.DB) *LogRepo {
	return &LogRepo{db: db}
}

func (r *LogRepo) Save(l *NotificationLog) {
	l.ID = uuid.New()
	l.CreatedAt = time.Now()
	if l.SentAt.IsZero() {
		l.SentAt = l.CreatedAt
	}
	// Truncate message to 1000 chars
	if len(l.Message) > 1000 {
		l.Message = l.Message[:1000]
	}

	_, err := r.db.NamedExec(`
		INSERT INTO notification_logs
			(id, user_id, channel, event, recipient, message, status, error_message, sent_at, created_at)
		VALUES
			(:id, :user_id, :channel, :event, :recipient, :message, :status, :error_message, :sent_at, :created_at)
	`, l)
	if err != nil {
		log.Printf("[warn] failed to save notification log: %v", err)
	}
}
