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

const maxFileSize = 5 * 1024 * 1024 // 5 MB

type UserService struct {
	repo      *repository.Repo
	uploadDir string
}

func NewUserService(repo *repository.Repo) *UserService {
	dir := os.Getenv("UPLOAD_DIR")
	if dir == "" {
		dir = "/app/uploads"
	}
	return &UserService{repo: repo, uploadDir: dir}
}

func (s *UserService) GetAdminContact() (*model.AdminContactDTO, error) {
	u, err := s.repo.FindFirstAdmin()
	if err != nil {
		return nil, err
	}
	if u == nil {
		return &model.AdminContactDTO{Name: "Admin"}, nil
	}
	return &model.AdminContactDTO{Name: u.Name, Mobile: u.Mobile, Email: u.Email}, nil
}

func (s *UserService) GetMe(userID string) (*model.UserDTO, error) {
	u, err := s.repo.FindByID(userID)
	if err != nil {
		return nil, err
	}
	if u == nil {
		return nil, errors.New("user not found")
	}
	return toDTO(u), nil
}

func (s *UserService) GetUser(userID string) (*model.UserDTO, error) {
	return s.GetMe(userID)
}

func (s *UserService) UpdateMe(userID string, req *model.UpdateProfileRequest) (*model.UserDTO, error) {
	u, err := s.repo.FindByID(userID)
	if err != nil || u == nil {
		return nil, errors.New("user not found")
	}

	if req.Name != nil && strings.TrimSpace(*req.Name) != "" {
		name := strings.TrimSpace(*req.Name)
		u.Name = name
	}
	if req.FatherName != nil {
		v := strings.TrimSpace(*req.FatherName)
		u.FatherName = &v
	}
	if req.Address != nil {
		v := strings.TrimSpace(*req.Address)
		u.Address = &v
	}
	if req.Gender != nil {
		v := strings.TrimSpace(*req.Gender)
		u.Gender = &v
	}
	if req.DateOfBirth != nil && *req.DateOfBirth != "" {
		t, err := time.Parse("2006-01-02", *req.DateOfBirth)
		if err == nil {
			u.DateOfBirth = &t
		}
	}
	if req.Email != nil && strings.TrimSpace(*req.Email) != "" {
		email := strings.TrimSpace(*req.Email)
		existing, _ := s.repo.FindByEmail(email)
		if existing != nil && existing.ID != u.ID {
			return nil, errors.New("email already in use")
		}
		u.Email = &email
	}

	if err := s.repo.UpdateUser(u); err != nil {
		return nil, err
	}
	return toDTO(u), nil
}

func (s *UserService) UploadPhoto(userID string, file multipart.File, header *multipart.FileHeader) (string, error) {
	return s.uploadFile(userID, file, header, "photos", []string{"image/jpeg", "image/png", "image/webp"})
}

func (s *UserService) DeletePhoto(userID string) error {
	return s.deleteFile(userID, "photo_url")
}

func (s *UserService) UploadAadhaar(userID string, file multipart.File, header *multipart.FileHeader) (string, error) {
	allowed := []string{"image/jpeg", "image/png", "image/webp", "application/pdf"}
	return s.uploadFile(userID, file, header, "aadhaar", allowed)
}

func (s *UserService) DeleteAadhaar(userID string) error {
	return s.deleteFile(userID, "aadhaar_url")
}

func (s *UserService) uploadFile(userID string, file multipart.File, header *multipart.FileHeader, subdir string, allowed []string) (string, error) {
	if header.Size > maxFileSize {
		return "", errors.New("file size exceeds 5MB limit")
	}

	// Detect MIME from first 512 bytes
	buf := make([]byte, 512)
	n, _ := file.Read(buf)
	mime := http.DetectContentType(buf[:n])
	file.Seek(0, 0)

	ok := false
	for _, a := range allowed {
		if strings.HasPrefix(mime, a) {
			ok = true
			break
		}
	}
	if !ok {
		return "", fmt.Errorf("unsupported file type: %s", mime)
	}

	ext := filepath.Ext(header.Filename)
	if ext == "" {
		ext = mimeToExt(mime)
	}

	u, _ := s.repo.FindByID(userID)

	// Delete old file if exists
	if u != nil {
		var oldURL *string
		if subdir == "photos" {
			oldURL = u.PhotoURL
		} else {
			oldURL = u.AadhaarURL
		}
		if oldURL != nil {
			oldPath := filepath.Join(s.uploadDir, strings.TrimPrefix(*oldURL, "/uploads/"))
			os.Remove(oldPath)
		}
	}

	filename := fmt.Sprintf("%s_%s_%s%s", subdir[:min(len(subdir), 6)], userID, uuid.New().String()[:8], ext)
	destDir := filepath.Join(s.uploadDir, subdir)
	os.MkdirAll(destDir, 0755)
	destPath := filepath.Join(destDir, filename)

	data, err := readAll(file)
	if err != nil {
		return "", err
	}
	if err := os.WriteFile(destPath, data, 0644); err != nil {
		return "", err
	}

	relURL := "/uploads/" + subdir + "/" + filename

	if u != nil {
		if subdir == "photos" {
			u.PhotoURL = &relURL
		} else {
			u.AadhaarURL = &relURL
		}
		s.repo.UpdateUser(u)
	}

	return relURL, nil
}

func (s *UserService) deleteFile(userID, field string) error {
	u, err := s.repo.FindByID(userID)
	if err != nil || u == nil {
		return errors.New("user not found")
	}

	var urlPtr *string
	if field == "photo_url" {
		urlPtr = u.PhotoURL
		u.PhotoURL = nil
	} else {
		urlPtr = u.AadhaarURL
		u.AadhaarURL = nil
	}

	if urlPtr != nil {
		path := filepath.Join(s.uploadDir, strings.TrimPrefix(*urlPtr, "/uploads/"))
		os.Remove(path)
	}

	return s.repo.UpdateUser(u)
}

func toDTO(u *model.User) *model.UserDTO {
	dto := &model.UserDTO{
		ID:         u.ID.String(),
		Name:       u.Name,
		FatherName: u.FatherName,
		Mobile:     u.Mobile,
		Email:      u.Email,
		Address:    u.Address,
		PhotoURL:   u.PhotoURL,
		AadhaarURL: u.AadhaarURL,
		Gender:     u.Gender,
		Role:       u.Role,
		IsActive:   u.IsActive,
		CreatedAt:  u.CreatedAt.Format(time.RFC3339),
	}
	if u.DateOfBirth != nil {
		s := u.DateOfBirth.Format("2006-01-02")
		dto.DateOfBirth = &s
	}
	return dto
}

func mimeToExt(mime string) string {
	switch {
	case strings.Contains(mime, "jpeg"):
		return ".jpg"
	case strings.Contains(mime, "png"):
		return ".png"
	case strings.Contains(mime, "webp"):
		return ".webp"
	case strings.Contains(mime, "pdf"):
		return ".pdf"
	default:
		return ".bin"
	}
}

func readAll(file multipart.File) ([]byte, error) {
	var buf []byte
	tmp := make([]byte, 4096)
	for {
		n, err := file.Read(tmp)
		buf = append(buf, tmp[:n]...)
		if err != nil {
			break
		}
	}
	return buf, nil
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
