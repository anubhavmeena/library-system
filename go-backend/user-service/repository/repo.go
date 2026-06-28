package repository

import (
	"database/sql"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
	"library/user-service/model"
)

type Repo struct {
	db *sqlx.DB
}

func New(db *sqlx.DB) *Repo {
	return &Repo{db: db}
}

func (r *Repo) FindByID(id string) (*model.User, error) {
	var u model.User
	err := r.db.Get(&u, `SELECT * FROM users WHERE id=$1`, id)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &u, err
}

func (r *Repo) FindByEmail(email string) (*model.User, error) {
	var u model.User
	err := r.db.Get(&u, `SELECT * FROM users WHERE email=$1`, email)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &u, err
}

func (r *Repo) FindFirstAdmin() (*model.User, error) {
	var u model.User
	err := r.db.Get(&u, `SELECT * FROM users WHERE role='ADMIN' ORDER BY created_at LIMIT 1`)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &u, err
}

func (r *Repo) UpdateUser(u *model.User) error {
	u.UpdatedAt = time.Now()
	_, err := r.db.NamedExec(`
		UPDATE users SET
			name=:name, father_name=:father_name, address=:address,
			gender=:gender, date_of_birth=:date_of_birth, email=:email,
			photo_url=:photo_url, aadhaar_url=:aadhaar_url, updated_at=:updated_at
		WHERE id=:id
	`, u)
	return err
}

// Feedback

func (r *Repo) SaveFeedback(f *model.Feedback) error {
	f.ID = uuid.New()
	now := time.Now()
	f.CreatedAt = now
	f.UpdatedAt = now
	f.Status = "OPEN"
	_, err := r.db.NamedExec(`
		INSERT INTO feedbacks (id, user_id, type, subject, description, status, created_at, updated_at)
		VALUES (:id, :user_id, :type, :subject, :description, :status, :created_at, :updated_at)
	`, f)
	return err
}

func (r *Repo) FindFeedbackByUserID(userID string) ([]model.Feedback, error) {
	var items []model.Feedback
	err := r.db.Select(&items,
		`SELECT * FROM feedbacks WHERE user_id=$1 ORDER BY created_at DESC`, userID)
	return items, err
}

// Gallery

func (r *Repo) FindAllGallery() ([]model.GalleryPhoto, error) {
	var items []model.GalleryPhoto
	err := r.db.Select(&items,
		`SELECT * FROM gallery_photos ORDER BY uploaded_at DESC`)
	return items, err
}

func (r *Repo) SaveGalleryPhoto(p *model.GalleryPhoto) error {
	p.ID = uuid.New()
	p.UploadedAt = time.Now()
	_, err := r.db.NamedExec(`
		INSERT INTO gallery_photos (id, url, caption, uploaded_by, uploaded_at)
		VALUES (:id, :url, :caption, :uploaded_by, :uploaded_at)
	`, p)
	return err
}

func (r *Repo) FindGalleryByID(id string) (*model.GalleryPhoto, error) {
	var p model.GalleryPhoto
	err := r.db.Get(&p, `SELECT * FROM gallery_photos WHERE id=$1`, id)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return &p, err
}

func (r *Repo) DeleteGalleryPhoto(id string) error {
	_, err := r.db.Exec(`DELETE FROM gallery_photos WHERE id=$1`, id)
	return err
}
