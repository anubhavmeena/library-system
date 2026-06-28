package service

import (
	"errors"
	"fmt"
	"mime/multipart"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/google/uuid"
	"library/user-service/model"
	"library/user-service/repository"
)

type GalleryService struct {
	repo      *repository.Repo
	uploadDir string
}

func NewGalleryService(repo *repository.Repo) *GalleryService {
	dir := os.Getenv("UPLOAD_DIR")
	if dir == "" {
		dir = "/app/uploads"
	}
	return &GalleryService{repo: repo, uploadDir: dir}
}

func (s *GalleryService) GetAll() ([]model.GalleryPhotoDTO, error) {
	photos, err := s.repo.FindAllGallery()
	if err != nil {
		return nil, err
	}
	dtos := make([]model.GalleryPhotoDTO, len(photos))
	for i, p := range photos {
		dtos[i] = model.GalleryPhotoDTO{
			ID:         p.ID.String(),
			URL:        p.URL,
			Caption:    p.Caption,
			UploadedAt: p.UploadedAt.Format(time.RFC3339),
		}
	}
	return dtos, nil
}

func (s *GalleryService) Upload(userID string, file multipart.File, header *multipart.FileHeader, caption string) (*model.GalleryPhotoDTO, error) {
	if header.Size > maxFileSize {
		return nil, errors.New("file size exceeds 5MB limit")
	}

	buf := make([]byte, 512)
	n, _ := file.Read(buf)
	mime := http.DetectContentType(buf[:n])
	file.Seek(0, 0)

	allowed := []string{"image/jpeg", "image/png", "image/webp"}
	ok := false
	for _, a := range allowed {
		if strings.HasPrefix(mime, a) {
			ok = true
			break
		}
	}
	if !ok {
		return nil, fmt.Errorf("unsupported file type: %s", mime)
	}

	ext := filepath.Ext(header.Filename)
	if ext == "" {
		ext = mimeToExt(mime)
	}

	filename := fmt.Sprintf("gallery_%s%s", uuid.New().String()[:8], ext)
	destDir := filepath.Join(s.uploadDir, "gallery")
	os.MkdirAll(destDir, 0755)
	destPath := filepath.Join(destDir, filename)

	data, _ := readAll(file)
	if err := os.WriteFile(destPath, data, 0644); err != nil {
		return nil, err
	}

	relURL := "/uploads/gallery/" + filename
	var capPtr *string
	if caption != "" {
		c := strings.TrimSpace(caption)
		capPtr = &c
	}

	p := &model.GalleryPhoto{
		URL:        relURL,
		Caption:    capPtr,
		UploadedBy: &userID,
	}
	if err := s.repo.SaveGalleryPhoto(p); err != nil {
		os.Remove(destPath)
		return nil, err
	}

	return &model.GalleryPhotoDTO{
		ID:         p.ID.String(),
		URL:        p.URL,
		Caption:    p.Caption,
		UploadedAt: p.UploadedAt.Format(time.RFC3339),
	}, nil
}

func (s *GalleryService) Delete(photoID string) error {
	p, err := s.repo.FindGalleryByID(photoID)
	if err != nil {
		return err
	}
	if p == nil {
		return errors.New("photo not found")
	}
	path := filepath.Join(s.uploadDir, strings.TrimPrefix(p.URL, "/uploads/"))
	os.Remove(path)
	return s.repo.DeleteGalleryPhoto(photoID)
}
