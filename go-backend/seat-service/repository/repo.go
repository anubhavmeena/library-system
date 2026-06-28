package repository

import (
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
	"library/seat-service/model"
)

type Repo struct {
	db *sqlx.DB
}

func New(db *sqlx.DB) *Repo {
	return &Repo{db: db}
}

// Seat seeding — 110 seats across rows A-D
var blockedSeats = map[string]bool{"B8": true, "B18": true}

func (r *Repo) SeedSeats() error {
	var count int
	r.db.Get(&count, `SELECT COUNT(*) FROM seats`)
	if count > 0 {
		return nil
	}

	type rowDef struct {
		label string
		count int
	}
	rows := []rowDef{{"A", 28}, {"B", 28}, {"C", 28}, {"D", 26}}
	for _, row := range rows {
		for i := 1; i <= row.count; i++ {
			seatNum := row.label + fmt.Sprintf("%d", i)
			active := !blockedSeats[seatNum]
			_, err := r.db.Exec(
				`INSERT INTO seats (id, seat_number, row_label, seat_index, is_active) VALUES ($1,$2,$3,$4,$5)`,
				uuid.New(), seatNum, row.label, i, active)
			if err != nil {
				return err
			}
		}
	}
	return nil
}

func (r *Repo) FindSeatByNumber(number string) (*model.Seat, error) {
	var s model.Seat
	err := r.db.Get(&s, `SELECT * FROM seats WHERE seat_number=$1`, number)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &s, err
}

func (r *Repo) FindAllSeats() ([]model.Seat, error) {
	var seats []model.Seat
	err := r.db.Select(&seats, `SELECT * FROM seats ORDER BY row_label, seat_index`)
	return seats, err
}

func (r *Repo) FindBookedSeatIDs(shift string, date time.Time) ([]uuid.UUID, error) {
	var ids []uuid.UUID
	err := r.db.Select(&ids, `
		SELECT DISTINCT seat_id FROM seat_bookings
		WHERE (shift=$1 OR shift='FULL_DAY' OR $1='FULL_DAY')
		  AND status='ACTIVE'
		  AND booking_date <= $2
		  AND end_date >= $2
	`, shift, date)
	return ids, err
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

func (r *Repo) SaveBooking(b *model.SeatBooking) error {
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

func (r *Repo) FindBookingByMembershipID(membershipID string) (*model.SeatBooking, error) {
	var b model.SeatBooking
	err := r.db.Get(&b,
		`SELECT * FROM seat_bookings WHERE membership_id=$1 AND status='ACTIVE' LIMIT 1`, membershipID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &b, err
}

func (r *Repo) FindBookingsByUserID(userID string) ([]model.SeatBooking, error) {
	var items []model.SeatBooking
	err := r.db.Select(&items,
		`SELECT * FROM seat_bookings WHERE user_id=$1 ORDER BY booking_date DESC`, userID)
	return items, err
}

func (r *Repo) FindActiveBookings(shift string, date time.Time) ([]struct {
	SeatNumber string `db:"seat_number"`
	model.SeatBooking
}, error) {
	var items []struct {
		SeatNumber string `db:"seat_number"`
		model.SeatBooking
	}
	err := r.db.Select(&items, `
		SELECT sb.*, s.seat_number
		FROM seat_bookings sb
		JOIN seats s ON s.id = sb.seat_id
		WHERE (sb.shift=$1 OR sb.shift='FULL_DAY' OR $1='FULL_DAY')
		  AND sb.status='ACTIVE'
		  AND sb.booking_date <= $2
		  AND sb.end_date >= $2
	`, shift, date)
	return items, err
}
