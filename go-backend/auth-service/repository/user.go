package repository

import (
	"database/sql"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
	"library/auth-service/model"
)

type UserRepo struct {
	db *sqlx.DB
}

func NewUserRepo(db *sqlx.DB) *UserRepo {
	return &UserRepo{db: db}
}

func (r *UserRepo) FindByMobileOrEmail(contact string) (*model.User, error) {
	var u model.User
	err := r.db.Get(&u,
		`SELECT * FROM users WHERE mobile=$1 OR email=$1 LIMIT 1`, contact)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &u, err
}

func (r *UserRepo) ExistsByMobileOrEmail(contact string) (bool, error) {
	var count int
	err := r.db.Get(&count,
		`SELECT COUNT(*) FROM users WHERE mobile=$1 OR email=$1`, contact)
	return count > 0, err
}

func (r *UserRepo) FindByID(id uuid.UUID) (*model.User, error) {
	var u model.User
	err := r.db.Get(&u, `SELECT * FROM users WHERE id=$1`, id)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &u, err
}

func (r *UserRepo) Save(u *model.User) error {
	if u.ID == uuid.Nil {
		u.ID = uuid.New()
	}
	now := time.Now()
	u.CreatedAt = now
	u.UpdatedAt = now

	_, err := r.db.NamedExec(`
		INSERT INTO users
			(id, mobile, email, name, address, father_name, photo_url, aadhaar_url,
			 date_of_birth, gender, is_active, role, created_at, updated_at)
		VALUES
			(:id, :mobile, :email, :name, :address, :father_name, :photo_url, :aadhaar_url,
			 :date_of_birth, :gender, :is_active, :role, :created_at, :updated_at)
	`, u)
	return err
}
