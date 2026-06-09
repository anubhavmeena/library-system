package com.library.user.service;

import com.library.user.dto.PhotoUploadResponse;
import com.library.user.dto.UpdateProfileRequest;
import com.library.user.dto.UserDto;
import com.library.user.entity.User;
import com.library.user.exception.ResourceNotFoundException;
import com.library.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserService userService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(userService, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(userService, "allowedTypes", "image/jpeg,image/png,image/webp");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User buildUser(String userId) {
        return User.builder()
                .id(UUID.fromString(userId))
                .name("Alice")
                .mobile("9876543210")
                .email("alice@test.com")
                .role(User.Role.STUDENT)
                .isActive(true)
                .build();
    }

    private MultipartFile mockFile(String contentType, long size, String filename) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        lenient().when(file.getContentType()).thenReturn(contentType);
        lenient().when(file.getSize()).thenReturn(size);
        lenient().when(file.getOriginalFilename()).thenReturn(filename);
        lenient().when(file.getInputStream())
                .thenReturn(new ByteArrayInputStream("fake-image-data".getBytes()));
        return file;
    }

    // ── getProfile ────────────────────────────────────────────────────────────

    @Test
    void getProfile_found_returnsDto() {
        String userId = UUID.randomUUID().toString();
        when(userRepository.findById(UUID.fromString(userId)))
                .thenReturn(Optional.of(buildUser(userId)));

        UserDto dto = userService.getProfile(userId);

        assertThat(dto.getId()).isEqualTo(userId);
        assertThat(dto.getName()).isEqualTo("Alice");
    }

    @Test
    void getProfile_notFound_throwsResourceNotFoundException() {
        String userId = UUID.randomUUID().toString();
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(userId);
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    void updateProfile_name_trimmedAndSaved() {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setName("  Bob  ");
        userService.updateProfile(userId, req);

        assertThat(user.getName()).isEqualTo("Bob");
    }

    @Test
    void updateProfile_blankName_nameNotChanged() {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setName("   "); // blank
        userService.updateProfile(userId, req);

        assertThat(user.getName()).isEqualTo("Alice"); // unchanged
    }

    @Test
    void updateProfile_address_trimmedAndSaved() {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setAddress("  123 Main St  ");
        userService.updateProfile(userId, req);

        assertThat(user.getAddress()).isEqualTo("123 Main St");
    }

    @Test
    void updateProfile_gender_saved() {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setGender("Female");
        userService.updateProfile(userId, req);

        assertThat(user.getGender()).isEqualTo("Female");
    }

    @Test
    void updateProfile_validDate_saved() {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setDateOfBirth("1995-06-15");
        userService.updateProfile(userId, req);

        assertThat(user.getDateOfBirth().toString()).isEqualTo("1995-06-15");
    }

    @Test
    void updateProfile_invalidDate_throwsIllegalArgument() {
        String userId = UUID.randomUUID().toString();
        when(userRepository.findById(UUID.fromString(userId)))
                .thenReturn(Optional.of(buildUser(userId)));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setDateOfBirth("not-a-date");

        assertThatThrownBy(() -> userService.updateProfile(userId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yyyy-MM-dd");
    }

    @Test
    void updateProfile_email_lowercasedAndSaved() {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenReturn(user);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setEmail("  NEW@TEST.COM  ");
        userService.updateProfile(userId, req);

        assertThat(user.getEmail()).isEqualTo("new@test.com");
    }

    @Test
    void updateProfile_emailTakenByOtherUser_throwsIllegalArgument() {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);
        User other = buildUser(UUID.randomUUID().toString());
        other.setEmail("taken@test.com");
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("taken@test.com")).thenReturn(Optional.of(other));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setEmail("taken@test.com");

        assertThatThrownBy(() -> userService.updateProfile(userId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    void updateProfile_emailBelongsToSameUser_succeeds() {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);
        user.setEmail("same@test.com");
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("same@test.com")).thenReturn(Optional.of(user)); // same user
        when(userRepository.save(any())).thenReturn(user);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setEmail("same@test.com");

        assertThatCode(() -> userService.updateProfile(userId, req)).doesNotThrowAnyException();
    }

    @Test
    void updateProfile_allFieldsNull_noChanges() {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        userService.updateProfile(userId, new UpdateProfileRequest());

        assertThat(user.getName()).isEqualTo("Alice");
        assertThat(user.getEmail()).isEqualTo("alice@test.com");
    }

    @Test
    void updateProfile_returnsDto() {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        UserDto result = userService.updateProfile(userId, new UpdateProfileRequest());

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(userId);
    }

    // ── uploadPhoto ───────────────────────────────────────────────────────────

    @Test
    void uploadPhoto_validJpeg_createsFileAndReturnsUrl() throws IOException {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        MultipartFile file = mockFile("image/jpeg", 1024L, "photo.jpg");

        PhotoUploadResponse resp = userService.uploadPhoto(userId, file);

        assertThat(resp.getPhotoUrl()).startsWith("/uploads/photos/");
        assertThat(resp.getPhotoUrl()).endsWith(".jpg");
        assertThat(resp.getMessage()).isEqualTo("Photo uploaded successfully");

        // Actual file created on disk
        Path photosDir = tempDir.resolve("photos");
        assertThat(Files.list(photosDir).count()).isEqualTo(1);
    }

    @Test
    void uploadPhoto_validPng_createsFileWithPngExtension() throws IOException {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        MultipartFile file = mockFile("image/png", 512L, "avatar.PNG");

        PhotoUploadResponse resp = userService.uploadPhoto(userId, file);

        assertThat(resp.getPhotoUrl()).endsWith(".png");
    }

    @Test
    void uploadPhoto_invalidContentType_throwsIllegalArgument() throws IOException {
        String userId = UUID.randomUUID().toString();
        when(userRepository.findById(UUID.fromString(userId)))
                .thenReturn(Optional.of(buildUser(userId)));

        MultipartFile file = mockFile("application/pdf", 1024L, "doc.pdf");

        assertThatThrownBy(() -> userService.uploadPhoto(userId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid file type");
    }

    @Test
    void uploadPhoto_nullContentType_throwsIllegalArgument() throws IOException {
        String userId = UUID.randomUUID().toString();
        when(userRepository.findById(UUID.fromString(userId)))
                .thenReturn(Optional.of(buildUser(userId)));

        MultipartFile file = mockFile(null, 1024L, "photo.jpg");

        assertThatThrownBy(() -> userService.uploadPhoto(userId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid file type");
    }

    @Test
    void uploadPhoto_fileTooLarge_throwsIllegalArgument() throws IOException {
        String userId = UUID.randomUUID().toString();
        when(userRepository.findById(UUID.fromString(userId)))
                .thenReturn(Optional.of(buildUser(userId)));

        MultipartFile file = mockFile("image/jpeg", 5_242_881L, "big.jpg"); // 5MB + 1 byte

        assertThatThrownBy(() -> userService.uploadPhoto(userId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void uploadPhoto_exactlyMaxSize_succeeds() throws IOException {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        MultipartFile file = mockFile("image/jpeg", 5_242_880L, "maxsize.jpg"); // exactly 5MB

        assertThatCode(() -> userService.uploadPhoto(userId, file)).doesNotThrowAnyException();
    }

    @Test
    void uploadPhoto_withExistingPhoto_deletesOldFile() throws IOException {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);

        // Create old photo file on disk
        Path photosDir = tempDir.resolve("photos");
        Files.createDirectories(photosDir);
        Path oldFile = photosDir.resolve("old_photo.jpg");
        Files.write(oldFile, "old".getBytes());
        user.setPhotoUrl("/uploads/photos/old_photo.jpg");

        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        MultipartFile file = mockFile("image/jpeg", 512L, "new.jpg");

        userService.uploadPhoto(userId, file);

        assertThat(oldFile).doesNotExist();
    }

    @Test
    void uploadPhoto_userNotFound_throwsResourceNotFoundException() throws IOException {
        String userId = UUID.randomUUID().toString();
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.empty());

        MultipartFile file = mockFile("image/jpeg", 512L, "photo.jpg");

        assertThatThrownBy(() -> userService.uploadPhoto(userId, file))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void uploadPhoto_savesPhotoUrlOnUser() throws IOException {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        MultipartFile file = mockFile("image/jpeg", 512L, "photo.jpg");
        userService.uploadPhoto(userId, file);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPhotoUrl()).startsWith("/uploads/photos/");
    }

    // ── deletePhoto ───────────────────────────────────────────────────────────

    @Test
    void deletePhoto_withExistingPhoto_deletesAndClearsUrl() throws IOException {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);

        Path photosDir = tempDir.resolve("photos");
        Files.createDirectories(photosDir);
        Path photoFile = photosDir.resolve("photo.jpg");
        Files.write(photoFile, "content".getBytes());
        user.setPhotoUrl("/uploads/photos/photo.jpg");

        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        userService.deletePhoto(userId);

        assertThat(photoFile).doesNotExist();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPhotoUrl()).isNull();
    }

    @Test
    void deletePhoto_noPhoto_throwsIllegalArgument() {
        String userId = UUID.randomUUID().toString();
        User user = buildUser(userId);
        user.setPhotoUrl(null);
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.deletePhoto(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No photo");
    }

    @Test
    void deletePhoto_userNotFound_throwsResourceNotFoundException() {
        String userId = UUID.randomUUID().toString();
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deletePhoto(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getExtension (private helper, tested via reflection) ──────────────────

    @Test
    void getExtension_normalFilename_extractsExtension() {
        String ext = ReflectionTestUtils.invokeMethod(userService, "getExtension", "photo.jpg");
        assertThat(ext).isEqualTo(".jpg");
    }

    @Test
    void getExtension_uppercase_lowercased() {
        String ext = ReflectionTestUtils.invokeMethod(userService, "getExtension", "photo.PNG");
        assertThat(ext).isEqualTo(".png");
    }

    @Test
    void getExtension_noExtension_returnsDefault() {
        String ext = ReflectionTestUtils.invokeMethod(userService, "getExtension", "photonoext");
        assertThat(ext).isEqualTo(".jpg");
    }

    @Test
    void getExtension_null_returnsDefault() {
        String ext = ReflectionTestUtils.invokeMethod(userService, "getExtension", (Object) null);
        assertThat(ext).isEqualTo(".jpg");
    }

    @Test
    void getExtension_multipleDots_usesLastDot() {
        String ext = ReflectionTestUtils.invokeMethod(userService, "getExtension", "archive.tar.gz");
        assertThat(ext).isEqualTo(".gz");
    }
}
