package com.library.user.service;

import com.library.user.dto.*;
import com.library.user.entity.User;
import com.library.user.exception.ResourceNotFoundException;
import com.library.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    @Value("${app.upload.dir:/app/uploads}")
    private String uploadDir;

    @Value("${app.upload.allowed-types:image/jpeg,image/png,image/webp}")
    private String allowedTypes;

    public UserDto getProfile(String userId) {
        return UserDto.fromEntity(findUser(userId));
    }

    @Transactional
    public UserDto updateProfile(String userId, UpdateProfileRequest request) {
        User user = findUser(userId);

        if (request.getName() != null && !request.getName().isBlank())
            user.setName(request.getName().trim());

        if (request.getFatherName() != null)
            user.setFatherName(request.getFatherName().trim());

        if (request.getAddress() != null)
            user.setAddress(request.getAddress().trim());

        if (request.getGender() != null)
            user.setGender(request.getGender());

        if (request.getDateOfBirth() != null && !request.getDateOfBirth().isBlank()) {
            try { user.setDateOfBirth(LocalDate.parse(request.getDateOfBirth())); }
            catch (Exception e) { throw new IllegalArgumentException("Invalid date. Use yyyy-MM-dd"); }
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String newEmail = request.getEmail().trim().toLowerCase();
            userRepository.findByEmail(newEmail).ifPresent(existing -> {
                if (!existing.getId().equals(user.getId()))
                    throw new IllegalArgumentException("Email already in use by another account");
            });
            user.setEmail(newEmail);
        }

        log.info("Profile updated for user: {}", userId);
        return UserDto.fromEntity(userRepository.save(user));
    }

    @Transactional
    public PhotoUploadResponse uploadPhoto(String userId, MultipartFile file) throws IOException {
        User user = findUser(userId);

        String contentType = file.getContentType();
        if (contentType == null || !List.of(allowedTypes.split(",")).contains(contentType))
            throw new IllegalArgumentException("Invalid file type. Only JPEG, PNG, WebP allowed.");

        if (file.getSize() > 5_242_880)
            throw new IllegalArgumentException("File must not exceed 5MB.");

        Path uploadPath = Paths.get(uploadDir, "photos");
        Files.createDirectories(uploadPath);

        // Remove old photo
        if (user.getPhotoUrl() != null) {
            try {
                Files.deleteIfExists(uploadPath.resolve(
                        Paths.get(user.getPhotoUrl()).getFileName().toString()));
            } catch (Exception ignored) {}
        }

        String ext      = getExtension(file.getOriginalFilename());
        String fileName = "user_" + userId + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Files.copy(file.getInputStream(), uploadPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

        String photoUrl = "/uploads/photos/" + fileName;
        user.setPhotoUrl(photoUrl);
        userRepository.save(user);

        log.info("Photo uploaded for user {}: {}", userId, photoUrl);
        return PhotoUploadResponse.builder().photoUrl(photoUrl).message("Photo uploaded successfully").build();
    }

    @Transactional
    public void deletePhoto(String userId) throws IOException {
        User user = findUser(userId);
        if (user.getPhotoUrl() == null) throw new IllegalArgumentException("No photo to delete.");

        Path uploadPath = Paths.get(uploadDir, "photos");
        Files.deleteIfExists(uploadPath.resolve(Paths.get(user.getPhotoUrl()).getFileName().toString()));
        user.setPhotoUrl(null);
        userRepository.save(user);
        log.info("Photo deleted for user: {}", userId);
    }

    private User findUser(String userId) {
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }
}