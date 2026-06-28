package service

import (
	"errors"

	"library/admin-service/model"
	"library/admin-service/repository"
)

type FeedbackService struct {
	repo *repository.Repo
}

func NewFeedbackService(repo *repository.Repo) *FeedbackService {
	return &FeedbackService{repo: repo}
}

func (s *FeedbackService) List(status string) ([]model.FeedbackDTO, error) {
	items, err := s.repo.FindAllFeedback(status)
	if err != nil {
		return nil, err
	}
	dtos := make([]model.FeedbackDTO, len(items))
	for i, f := range items {
		dtos[i] = model.FeedbackDTO{
			ID:          f.ID.String(),
			UserID:      f.UserID.String(),
			Type:        f.Type,
			Subject:     f.Subject,
			Description: f.Description,
			Status:      f.Status,
			AdminNotes:  f.AdminNotes,
			CreatedAt:   timeStr(f.CreatedAt, "2006-01-02T15:04:05Z07:00"),
			UpdatedAt:   timeStr(f.UpdatedAt, "2006-01-02T15:04:05Z07:00"),
		}
	}
	return dtos, nil
}

func (s *FeedbackService) Update(id string, req model.UpdateFeedbackRequest) error {
	f, err := s.repo.FindFeedbackByID(id)
	if err != nil {
		return err
	}
	if f == nil {
		return errors.New("feedback not found")
	}
	fields := map[string]interface{}{}
	if req.Status != nil {
		fields["status"] = *req.Status
	}
	if req.AdminNotes != nil {
		fields["admin_notes"] = *req.AdminNotes
	}
	return s.repo.UpdateFeedback(id, fields)
}
