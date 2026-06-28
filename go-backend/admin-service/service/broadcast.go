package service

import (
	"library/admin-service/model"
	"library/admin-service/repository"
)

type BroadcastService struct {
	repo  *repository.Repo
	notif *NotifClient
}

func NewBroadcastService(repo *repository.Repo, notif *NotifClient) *BroadcastService {
	return &BroadcastService{repo: repo, notif: notif}
}

func (s *BroadcastService) Send(message string) (int, error) {
	users, err := s.repo.FindAllActiveStudents()
	if err != nil {
		return 0, err
	}

	count := 0
	for i, u := range users {
		mobile := ""
		if u.Mobile != nil {
			mobile = *u.Mobile
		}
		if mobile == "" {
			continue
		}
		go s.notif.Broadcast(u.ID.String(), mobile, message, i == 0)
		count++
	}

	s.repo.SaveBroadcast(message, count)
	return count, nil
}

func (s *BroadcastService) GetHistory() ([]model.BroadcastHistoryDTO, error) {
	items, err := s.repo.FindBroadcastHistory()
	if err != nil {
		return nil, err
	}
	dtos := make([]model.BroadcastHistoryDTO, len(items))
	for i, b := range items {
		dtos[i] = model.BroadcastHistoryDTO{
			ID:             b.ID.String(),
			Message:        b.Message,
			RecipientCount: b.RecipientCount,
			SentAt:         b.SentAt.Format("2006-01-02T15:04:05Z07:00"),
		}
	}
	return dtos, nil
}
