package repository

import (
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
	"library/admin-service/model"
)

type Repo struct {
	db *sqlx.DB
}

func New(db *sqlx.DB) *Repo {
	return &Repo{db: db}
}

func derefStr(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

// ---- Dashboard ----

func (r *Repo) CountStudents() (total, active int, err error) {
	r.db.Get(&total, `SELECT COUNT(*) FROM users WHERE role='STUDENT'`)
	r.db.Get(&active, `SELECT COUNT(*) FROM users WHERE role='STUDENT' AND is_active=true`)
	return
}

func (r *Repo) CountMemberships() (active, expired, expiringThisWeek int, err error) {
	r.db.Get(&active, `SELECT COUNT(*) FROM memberships WHERE status='ACTIVE'`)
	r.db.Get(&expired, `SELECT COUNT(*) FROM memberships WHERE status='EXPIRED'`)
	in7 := time.Now().AddDate(0, 0, 7)
	r.db.Get(&expiringThisWeek, `SELECT COUNT(*) FROM memberships WHERE status='ACTIVE' AND end_date <= $1`, in7)
	return
}

func (r *Repo) CountSeats() (total, occupied int, err error) {
	r.db.Get(&total, `SELECT COUNT(*) FROM seats WHERE is_active=true`)
	today := time.Now().Truncate(24 * time.Hour)
	r.db.Get(&occupied, `
		SELECT COUNT(DISTINCT seat_id) FROM seat_bookings
		WHERE status='ACTIVE' AND booking_date <= $1 AND end_date >= $1
	`, today)
	return
}

func (r *Repo) RevenueToday() float64 {
	var v float64
	today := time.Now().Truncate(24 * time.Hour)
	r.db.Get(&v, `SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='SUCCESS' AND created_at >= $1`, today)
	return v
}

func (r *Repo) RevenueThisMonth() (float64, int) {
	var amount float64
	var count int
	bom := time.Now().Truncate(24*time.Hour).AddDate(0, 0, -time.Now().Day()+1)
	r.db.Get(&amount, `SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='SUCCESS' AND created_at >= $1`, bom)
	r.db.Get(&count, `SELECT COUNT(*) FROM payments WHERE status='SUCCESS' AND created_at >= $1`, bom)
	return amount, count
}

func (r *Repo) CountVisitors() (total, today int) {
	r.db.Get(&total, `SELECT COUNT(*) FROM visitor_events`)
	start := time.Now().Truncate(24 * time.Hour)
	r.db.Get(&today, `SELECT COUNT(*) FROM visitor_events WHERE created_at >= $1`, start)
	return
}

func (r *Repo) TrackVisitor(page string) {
	r.db.Exec(`INSERT INTO visitor_events (page, created_at) VALUES ($1, now())`, page)
}

// ---- Students ----

type StudentRow struct {
	model.User
	MembershipID     *uuid.UUID `db:"membership_id"`
	PlanName         *string    `db:"plan_name"`
	MembershipStatus *string    `db:"membership_status"`
	StartDate        *time.Time `db:"start_date"`
	EndDate          *time.Time `db:"end_date"`
	SeatNumber       *string    `db:"seat_number"`
	Shift            *string    `db:"shift"`
	PendingAmount    *float64   `db:"pending_amount"`
	PaymentGateway   *string    `db:"payment_gateway"`
}

func (r *Repo) FindStudents(search string, page, size int) ([]StudentRow, int, error) {
	offset := (page - 1) * size
	like := "%" + search + "%"

	var rows []StudentRow
	err := r.db.Select(&rows, `
		SELECT u.*,
		       m.id as membership_id, pl.name as plan_name, m.status as membership_status,
		       m.start_date, m.end_date, m.seat_number, m.shift,
		       p.pending_amount, p.payment_gateway
		FROM users u
		LEFT JOIN LATERAL (
			SELECT * FROM memberships WHERE user_id=u.id AND status IN ('ACTIVE','QUEUED')
			ORDER BY status DESC LIMIT 1
		) m ON true
		LEFT JOIN membership_plans pl ON pl.id = m.plan_id
		LEFT JOIN LATERAL (
			SELECT * FROM payments WHERE membership_id=m.id AND status='SUCCESS' LIMIT 1
		) p ON true
		WHERE u.role='STUDENT' AND (u.name ILIKE $1 OR u.mobile ILIKE $1 OR u.email ILIKE $1)
		ORDER BY u.created_at DESC
		LIMIT $2 OFFSET $3
	`, like, size, offset)
	if err != nil {
		return nil, 0, err
	}

	var total int
	r.db.Get(&total, `SELECT COUNT(*) FROM users WHERE role='STUDENT' AND (name ILIKE $1 OR mobile ILIKE $1 OR email ILIKE $1)`, like)
	return rows, total, nil
}

func (r *Repo) FindUserByID(id string) (*model.User, error) {
	var u model.User
	err := r.db.Get(&u, `SELECT * FROM users WHERE id=$1`, id)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &u, err
}

func (r *Repo) FindAllActiveStudents() ([]model.User, error) {
	var users []model.User
	err := r.db.Select(&users, `SELECT * FROM users WHERE role='STUDENT' AND is_active=true`)
	return users, err
}

func (r *Repo) UpdateUserActive(id string, active bool) error {
	_, err := r.db.Exec(`UPDATE users SET is_active=$1, updated_at=now() WHERE id=$2`, active, id)
	return err
}

func (r *Repo) UpdateUserFields(id string, fields map[string]interface{}) error {
	if len(fields) == 0 {
		return nil
	}
	q := `UPDATE users SET updated_at=now()`
	args := []interface{}{}
	i := 1
	for k, v := range fields {
		q += `, ` + k + `=$` + fmt.Sprintf("%d", i)
		args = append(args, v)
		i++
	}
	q += ` WHERE id=$` + fmt.Sprintf("%d", i)
	args = append(args, id)
	_, err := r.db.Exec(q, args...)
	return err
}

func (r *Repo) FindPaymentsByStudentID(userID string) ([]model.Payment, error) {
	var items []model.Payment
	err := r.db.Select(&items, `SELECT * FROM payments WHERE user_id=$1 ORDER BY created_at DESC`, userID)
	return items, err
}

// ---- Plans ----

func (r *Repo) FindAllPlans() ([]model.Plan, error) {
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

func (r *Repo) FindPlanClosestToPrice(price float64) (*model.Plan, error) {
	var p model.Plan
	err := r.db.Get(&p, `SELECT * FROM membership_plans WHERE is_active=true ORDER BY ABS(price - $1) LIMIT 1`, price)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &p, err
}

// ---- Memberships ----

func (r *Repo) FindActiveMembershipByUserID(userID string) (*model.Membership, error) {
	var m model.Membership
	err := r.db.Get(&m, `SELECT * FROM memberships WHERE user_id=$1 AND status='ACTIVE' LIMIT 1`, userID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &m, err
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
		now := time.Now()
		m.CreatedAt = &now
	}
	_, err := r.db.NamedExec(`
		INSERT INTO memberships (id, user_id, plan_id, seat_id, seat_number, shift, start_date, end_date, status, reminder_sent, created_at)
		VALUES (:id, :user_id, :plan_id, :seat_id, :seat_number, :shift, :start_date, :end_date, :status, :reminder_sent, :created_at)
		ON CONFLICT (id) DO UPDATE SET
		  seat_id=EXCLUDED.seat_id, seat_number=EXCLUDED.seat_number, shift=EXCLUDED.shift,
		  status=EXCLUDED.status, reminder_sent=EXCLUDED.reminder_sent
	`, m)
	return err
}

func (r *Repo) SavePayment(p *model.Payment) error {
	if p.ID == uuid.Nil {
		p.ID = uuid.New()
		now := time.Now()
		p.CreatedAt = &now
		p.UpdatedAt = &now
	}
	_, err := r.db.NamedExec(`
		INSERT INTO payments (id, membership_id, user_id, amount, pending_amount, payment_gateway, gateway_order_id, gateway_payment_id, status, created_at, updated_at)
		VALUES (:id, :membership_id, :user_id, :amount, :pending_amount, :payment_gateway, :gateway_order_id, :gateway_payment_id, :status, :created_at, :updated_at)
		ON CONFLICT (id) DO UPDATE SET status=EXCLUDED.status, updated_at=EXCLUDED.updated_at
	`, p)
	return err
}

// ---- Seats ----

type ActiveBookingRow struct {
	model.SeatBooking
	SeatNumber string     `db:"seat_number"`
	RowLabel   string     `db:"row_label"`
	UserName   string     `db:"user_name"`
	UserMobile *string    `db:"user_mobile"`
	UserGender *string    `db:"user_gender"`
}

func (r *Repo) FindAllSeats() ([]model.Seat, error) {
	var seats []model.Seat
	err := r.db.Select(&seats, `SELECT * FROM seats WHERE is_active=true ORDER BY row_label, seat_index`)
	return seats, err
}

func (r *Repo) FindActiveBookingsWithUser(shift string, date time.Time) ([]ActiveBookingRow, error) {
	var items []ActiveBookingRow
	err := r.db.Select(&items, `
		SELECT sb.*, s.seat_number, s.row_label, u.name as user_name, u.mobile as user_mobile, u.gender as user_gender
		FROM seat_bookings sb
		JOIN seats s ON s.id = sb.seat_id
		JOIN users u ON u.id = sb.user_id
		WHERE (sb.shift=$1 OR sb.shift='FULL_DAY' OR $1='FULL_DAY')
		  AND sb.status='ACTIVE'
		  AND sb.booking_date <= $2
		  AND sb.end_date >= $2
	`, shift, date)
	return items, err
}

func (r *Repo) FindSeatByNumber(number string) (*model.Seat, error) {
	var s model.Seat
	err := r.db.Get(&s, `SELECT * FROM seats WHERE seat_number=$1`, number)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &s, err
}

func (r *Repo) ConflictExists(seatID uuid.UUID, shift string, start, end time.Time) (bool, error) {
	var count int
	err := r.db.Get(&count, `
		SELECT COUNT(*) FROM seat_bookings
		WHERE seat_id=$1
		  AND (shift=$2 OR shift='FULL_DAY' OR $2='FULL_DAY')
		  AND status='ACTIVE'
		  AND booking_date <= $3
		  AND end_date >= $4
	`, seatID, shift, end, start)
	return count > 0, err
}

func (r *Repo) SaveSeatBooking(b *model.SeatBooking) error {
	if b.ID == uuid.Nil {
		b.ID = uuid.New()
		b.CreatedAt = time.Now()
	}
	_, err := r.db.NamedExec(`
		INSERT INTO seat_bookings (id, seat_id, user_id, membership_id, shift, booking_date, end_date, status, created_at)
		VALUES (:id, :seat_id, :user_id, :membership_id, :shift, :booking_date, :end_date, :status, :created_at)
		ON CONFLICT (id) DO UPDATE SET status=EXCLUDED.status
	`, b)
	return err
}

func (r *Repo) ReleaseSeatBookingsByMembership(membershipID string) error {
	_, err := r.db.Exec(`UPDATE seat_bookings SET status='RELEASED' WHERE membership_id=$1 AND status='ACTIVE'`, membershipID)
	return err
}

// ---- Feedback ----

func (r *Repo) FindAllFeedback(status string) ([]model.Feedback, error) {
	var items []model.Feedback
	if status != "" {
		err := r.db.Select(&items, `SELECT * FROM feedbacks WHERE status=$1 ORDER BY created_at DESC`, status)
		return items, err
	}
	err := r.db.Select(&items, `SELECT * FROM feedbacks ORDER BY created_at DESC`)
	return items, err
}

func (r *Repo) FindFeedbackByID(id string) (*model.Feedback, error) {
	var f model.Feedback
	err := r.db.Get(&f, `SELECT * FROM feedbacks WHERE id=$1`, id)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &f, err
}

func (r *Repo) UpdateFeedback(id string, fields map[string]interface{}) error {
	if len(fields) == 0 {
		return nil
	}
	q := `UPDATE feedback SET updated_at=now()`
	args := []interface{}{}
	i := 1
	for k, v := range fields {
		q += `, ` + k + `=$` + fmt.Sprintf("%d", i)
		args = append(args, v)
		i++
	}
	q += ` WHERE id=$` + fmt.Sprintf("%d", i)
	args = append(args, id)
	_, err := r.db.Exec(q, args...)
	return err
}

// ---- Broadcasts ----

func (r *Repo) SaveBroadcast(msg string, count int) error {
	_, err := r.db.Exec(`INSERT INTO broadcast_messages (id, message, recipient_count, sent_at) VALUES ($1,$2,$3,now())`,
		uuid.New(), msg, count)
	return err
}

func (r *Repo) FindBroadcastHistory() ([]model.BroadcastMessage, error) {
	var items []model.BroadcastMessage
	err := r.db.Select(&items, `SELECT * FROM broadcast_messages ORDER BY sent_at DESC LIMIT 50`)
	return items, err
}

// ---- Revenue ----

func (r *Repo) FindSuccessPayments(from, to time.Time) ([]model.Payment, error) {
	var payments []model.Payment
	err := r.db.Select(&payments, `
		SELECT * FROM payments WHERE status='SUCCESS' AND created_at >= $1 AND created_at <= $2 ORDER BY created_at
	`, from, to)
	return payments, err
}

func (r *Repo) FindPendingFeeStudents() ([]StudentRow, error) {
	var rows []StudentRow
	err := r.db.Select(&rows, `
		SELECT u.*, m.id as membership_id, pl.name as plan_name, m.status as membership_status,
		       m.start_date, m.end_date, m.seat_number, m.shift,
		       p.pending_amount, p.payment_gateway
		FROM users u
		JOIN memberships m ON m.user_id=u.id AND m.status='ACTIVE'
		JOIN membership_plans pl ON pl.id=m.plan_id
		JOIN payments p ON p.membership_id=m.id AND p.status='SUCCESS'
		WHERE p.pending_amount > 0
		ORDER BY p.pending_amount DESC
	`)
	return rows, err
}

// ---- Expenses ----

func (r *Repo) FindExpense(year, month int) (*model.MonthlyExpense, []model.MiscExpenseItem, error) {
	var e model.MonthlyExpense
	err := r.db.Get(&e, `SELECT * FROM monthly_expenses WHERE year=$1 AND month=$2`, year, month)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil, nil
	}
	if err != nil {
		return nil, nil, err
	}
	var items []model.MiscExpenseItem
	r.db.Select(&items, `SELECT * FROM misc_expense_items WHERE monthly_expense_id=$1 ORDER BY sort_order`, e.ID)
	return &e, items, nil
}

func (r *Repo) SaveExpense(req *model.MonthlyExpense, items []model.MiscExpenseItem) error {
	tx, err := r.db.Beginx()
	if err != nil {
		return err
	}
	defer tx.Rollback()

	miscTotal := 0.0
	for _, it := range items {
		miscTotal += it.Amount
	}
	req.Miscellaneous = miscTotal

	_, err = tx.NamedExec(`
		INSERT INTO monthly_expenses (id, year, month, water_tanker_qty, water_tanker_price, electricity_bill, internet_bill, miscellaneous, created_at, updated_at)
		VALUES (:id, :year, :month, :water_tanker_qty, :water_tanker_price, :electricity_bill, :internet_bill, :miscellaneous, now(), now())
		ON CONFLICT (year, month) DO UPDATE SET
		  water_tanker_qty=EXCLUDED.water_tanker_qty, water_tanker_price=EXCLUDED.water_tanker_price,
		  electricity_bill=EXCLUDED.electricity_bill, internet_bill=EXCLUDED.internet_bill,
		  miscellaneous=EXCLUDED.miscellaneous, updated_at=now()
	`, req)
	if err != nil {
		return err
	}

	tx.Exec(`DELETE FROM misc_expense_items WHERE monthly_expense_id=$1`, req.ID)
	for i, item := range items {
		item.ID = uuid.New()
		item.MonthlyExpenseID = req.ID
		item.SortOrder = i
		tx.NamedExec(`INSERT INTO misc_expense_items (id, monthly_expense_id, description, amount, sort_order) VALUES (:id, :monthly_expense_id, :description, :amount, :sort_order)`, &item)
	}
	return tx.Commit()
}

// ---- Delete student (cascaded) ----

func (r *Repo) DeleteStudent(id uuid.UUID) error {
	tx, err := r.db.Beginx()
	if err != nil {
		return err
	}
	defer tx.Rollback()
	tx.Exec(`DELETE FROM feedbacks WHERE user_id=$1`, id)
	tx.Exec(`DELETE FROM seat_bookings WHERE membership_id IN (SELECT id FROM memberships WHERE user_id=$1)`, id)
	tx.Exec(`DELETE FROM payments WHERE user_id=$1`, id)
	tx.Exec(`DELETE FROM memberships WHERE user_id=$1`, id)
	if _, err := tx.Exec(`DELETE FROM users WHERE id=$1`, id); err != nil {
		return err
	}
	return tx.Commit()
}

// ---- Pending fees ----

func (r *Repo) ClearPendingFees(userID uuid.UUID) error {
	_, err := r.db.Exec(`UPDATE payments SET pending_amount=0 WHERE user_id=$1`, userID)
	return err
}

// ---- Expiring memberships ----

func (r *Repo) FindExpiringMemberships(withinDays int) ([]StudentRow, error) {
	deadline := time.Now().AddDate(0, 0, withinDays)
	var rows []StudentRow
	err := r.db.Select(&rows, `
		SELECT u.*,
		       m.id AS membership_id, pl.name AS plan_name,
		       m.seat_number, m.shift, m.status AS membership_status,
		       m.start_date, m.end_date, 0::numeric AS pending_amount, '' AS payment_gateway
		FROM users u
		JOIN memberships m ON m.user_id=u.id AND m.status='ACTIVE'
		JOIN membership_plans pl ON pl.id=m.plan_id
		WHERE m.end_date <= $1
		ORDER BY m.end_date ASC
	`, deadline)
	return rows, err
}

// ---- Change membership plan ----

func (r *Repo) ChangeMembershipPlan(membershipID, planID uuid.UUID) error {
	_, err := r.db.Exec(`
		UPDATE memberships
		SET plan_id=$1,
		    end_date = start_date + (SELECT duration_days FROM membership_plans WHERE id=$1)::int - 1,
		    updated_at=now()
		WHERE id=$2
	`, planID, membershipID)
	return err
}

// ---- Payment breakdown & daily payments ----

func (r *Repo) FindPaymentBreakdown(from, to time.Time) ([]model.PaymentBreakdownDTO, error) {
	var rows []struct {
		Amount float64 `db:"amount"`
		Count  int     `db:"cnt"`
	}
	err := r.db.Select(&rows, `
		SELECT amount, COUNT(*) AS cnt
		FROM payments
		WHERE status='SUCCESS' AND created_at >= $1 AND created_at <= $2
		GROUP BY amount ORDER BY amount
	`, from, to)
	if err != nil {
		return nil, err
	}
	out := make([]model.PaymentBreakdownDTO, len(rows))
	for i, r := range rows {
		out[i] = model.PaymentBreakdownDTO{Amount: r.Amount, Count: r.Count}
	}
	return out, nil
}

func (r *Repo) FindPaymentsByDate(from, to time.Time) ([]model.DailyPaymentDTO, error) {
	var rows []struct {
		Amount           float64   `db:"amount"`
		PaymentGateway   *string   `db:"payment_gateway"`
		GatewayOrderID   *string   `db:"gateway_order_id"`
		GatewayPaymentID *string   `db:"gateway_payment_id"`
		CreatedAt        time.Time `db:"created_at"`
		Name             *string   `db:"name"`
		Mobile           *string   `db:"mobile"`
	}
	err := r.db.Select(&rows, `
		SELECT p.amount, p.payment_gateway, p.gateway_order_id, p.gateway_payment_id, p.created_at,
		       u.name, u.mobile
		FROM payments p
		LEFT JOIN users u ON u.id=p.user_id
		WHERE p.status='SUCCESS' AND p.created_at >= $1 AND p.created_at <= $2
		ORDER BY p.created_at DESC
	`, from, to)
	if err != nil {
		return nil, err
	}
	out := make([]model.DailyPaymentDTO, len(rows))
	for i, row := range rows {
		ref := ""
		if row.GatewayPaymentID != nil && *row.GatewayPaymentID != "" {
			ref = *row.GatewayPaymentID
		} else if row.GatewayOrderID != nil {
			ref = *row.GatewayOrderID
		}
		name := "—"
		mobile := "—"
		if row.Name != nil {
			name = *row.Name
		}
		if row.Mobile != nil {
			mobile = *row.Mobile
		}
		out[i] = model.DailyPaymentDTO{
			StudentName:    name,
			StudentMobile:  mobile,
			Amount:         row.Amount,
			PaymentGateway: derefStr(row.PaymentGateway),
			ReferenceID:    ref,
			PaidAt:         row.CreatedAt.Format(time.RFC3339),
		}
	}
	return out, nil
}

// ---- User upsert (for import) ----

func (r *Repo) FindUserByMobile(mobile string) (*model.User, error) {
	var u model.User
	err := r.db.Get(&u, `SELECT * FROM users WHERE mobile=$1`, mobile)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &u, err
}

func (r *Repo) SaveUser(u *model.User) error {
	if u.ID == uuid.Nil {
		u.ID = uuid.New()
		now := time.Now()
		u.CreatedAt = &now
		u.UpdatedAt = &now
	}
	_, err := r.db.NamedExec(`
		INSERT INTO users (id, mobile, email, name, address, father_name, photo_url, aadhaar_url, date_of_birth, gender, is_active, role, created_at, updated_at)
		VALUES (:id, :mobile, :email, :name, :address, :father_name, :photo_url, :aadhaar_url, :date_of_birth, :gender, :is_active, :role, :created_at, :updated_at)
		ON CONFLICT (mobile) DO UPDATE SET
		  name=EXCLUDED.name, updated_at=now()
	`, u)
	return err
}
