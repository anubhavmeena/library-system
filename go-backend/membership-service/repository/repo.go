package repository

import (
	"database/sql"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
	"library/membership-service/model"
)

type Repo struct {
	db *sqlx.DB
}

func New(db *sqlx.DB) *Repo {
	return &Repo{db: db}
}

// Plans

func (r *Repo) FindAllActivePlans() ([]model.Plan, error) {
	var plans []model.Plan
	err := r.db.Select(&plans, `SELECT * FROM membership_plans WHERE is_active=true ORDER BY price`)
	return plans, err
}

func (r *Repo) FindPlanByID(id string) (*model.Plan, error) {
	var p model.Plan
	err := r.db.Get(&p, `SELECT * FROM membership_plans WHERE id=$1`, id)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &p, err
}

func (r *Repo) SeedPlans() error {
	var count int
	r.db.Get(&count, `SELECT COUNT(*) FROM membership_plans`)
	if count > 0 {
		return nil
	}
	_, err := r.db.Exec(`
		INSERT INTO membership_plans (id, name, plan_type, price, duration_days, is_active) VALUES
		($1, 'Half Day', 'HALF_DAY', 800, 30, true),
		($2, 'Full Day', 'FULL_DAY', 1200, 30, true)
	`, uuid.New(), uuid.New())
	return err
}

// Memberships

func (r *Repo) FindActiveMembership(userID string) (*model.Membership, error) {
	var m model.Membership
	err := r.db.Get(&m,
		`SELECT * FROM memberships WHERE user_id=$1 AND status='ACTIVE' LIMIT 1`, userID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &m, err
}

func (r *Repo) FindQueuedMembership(userID string) (*model.Membership, error) {
	var m model.Membership
	err := r.db.Get(&m,
		`SELECT * FROM memberships WHERE user_id=$1 AND status='QUEUED' LIMIT 1`, userID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &m, err
}

func (r *Repo) FindAllMemberships(userID string) ([]model.Membership, error) {
	var items []model.Membership
	err := r.db.Select(&items,
		`SELECT * FROM memberships WHERE user_id=$1 ORDER BY created_at DESC`, userID)
	return items, err
}

func (r *Repo) FindMembershipByID(id string) (*model.Membership, error) {
	var m model.Membership
	err := r.db.Get(&m, `SELECT * FROM memberships WHERE id=$1`, id)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &m, err
}

func (r *Repo) SaveMembership(m *model.Membership) error {
	if m.ID == uuid.Nil {
		m.ID = uuid.New()
		m.CreatedAt = time.Now()
	}
	_, err := r.db.NamedExec(`
		INSERT INTO memberships (id, user_id, plan_id, seat_id, seat_number, shift, start_date, end_date, status, reminder_sent, created_at)
		VALUES (:id, :user_id, :plan_id, :seat_id, :seat_number, :shift, :start_date, :end_date, :status, :reminder_sent, :created_at)
		ON CONFLICT (id) DO UPDATE SET
			seat_id=EXCLUDED.seat_id, seat_number=EXCLUDED.seat_number,
			status=EXCLUDED.status, reminder_sent=EXCLUDED.reminder_sent
	`, m)
	return err
}

func (r *Repo) UpdateMembershipStatus(id, status string) error {
	_, err := r.db.Exec(`UPDATE memberships SET status=$1 WHERE id=$2`, status, id)
	return err
}

// Payments

func (r *Repo) FindPaymentsByUserID(userID string) ([]model.Payment, error) {
	var items []model.Payment
	err := r.db.Select(&items,
		`SELECT * FROM payments WHERE user_id=$1 ORDER BY created_at DESC`, userID)
	return items, err
}

func (r *Repo) FindPaymentByOrderID(orderID string) (*model.Payment, error) {
	var p model.Payment
	err := r.db.Get(&p, `SELECT * FROM payments WHERE gateway_order_id=$1 LIMIT 1`, orderID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &p, err
}

func (r *Repo) SavePayment(p *model.Payment) error {
	if p.ID == uuid.Nil {
		p.ID = uuid.New()
		p.CreatedAt = time.Now()
	}
	p.UpdatedAt = time.Now()
	_, err := r.db.NamedExec(`
		INSERT INTO payments (id, membership_id, user_id, amount, pending_amount, payment_gateway, gateway_order_id, gateway_payment_id, status, created_at, updated_at)
		VALUES (:id, :membership_id, :user_id, :amount, :pending_amount, :payment_gateway, :gateway_order_id, :gateway_payment_id, :status, :created_at, :updated_at)
		ON CONFLICT (id) DO UPDATE SET
			gateway_payment_id=EXCLUDED.gateway_payment_id, status=EXCLUDED.status, updated_at=EXCLUDED.updated_at
	`, p)
	return err
}

type UserContact struct {
	Name   string
	Mobile string
	Email  string
}

func (r *Repo) FindUserContact(userID string) UserContact {
	var name string
	var mobile, email sql.NullString
	r.db.QueryRow(`SELECT name, mobile, email FROM users WHERE id=$1`, userID).Scan(&name, &mobile, &email)
	uc := UserContact{Name: name}
	if mobile.Valid {
		uc.Mobile = mobile.String
	}
	if email.Valid {
		uc.Email = email.String
	}
	return uc
}
