package service

import (
	"errors"
	"strings"
	"time"

	"github.com/google/uuid"
	"library/user-service/model"
	"library/user-service/repository"
)

type FeedbackService struct {
	repo *repository.Repo
}

func NewFeedbackService(repo *repository.Repo) *FeedbackService {
	return &FeedbackService{repo: repo}
}

func (s *FeedbackService) Create(userID string, req model.CreateFeedbackRequest) (*model.FeedbackDTO, error) {
	t := strings.ToUpper(strings.TrimSpace(req.Type))
	if t != "FEEDBACK" && t != "COMPLAINT" {
		return nil, errors.New("type must be FEEDBACK or COMPLAINT")
	}

	uid, err := uuid.Parse(userID)
	if err != nil {
		return nil, errors.New("invalid user ID")
	}

	f := &model.Feedback{
		UserID:      uid,
		Type:        t,
		Subject:     strings.TrimSpace(req.Subject),
		Description: strings.TrimSpace(req.Description),
	}
	if err := s.repo.SaveFeedback(f); err != nil {
		return nil, err
	}
	return toFeedbackDTO(f), nil
}

func (s *FeedbackService) GetMy(userID string) ([]model.FeedbackDTO, error) {
	items, err := s.repo.FindFeedbackByUserID(userID)
	if err != nil {
		return nil, err
	}
	dtos := make([]model.FeedbackDTO, len(items))
	for i, f := range items {
		dtos[i] = *toFeedbackDTO(&f)
	}
	return dtos, nil
}

func toFeedbackDTO(f *model.Feedback) *model.FeedbackDTO {
	return &model.FeedbackDTO{
		ID:          f.ID.String(),
		Type:        f.Type,
		Subject:     f.Subject,
		Description: f.Description,
		Status:      f.Status,
		AdminNotes:  f.AdminNotes,
		CreatedAt:   f.CreatedAt.Format(time.RFC3339),
		UpdatedAt:   f.UpdatedAt.Format(time.RFC3339),
	}
}
