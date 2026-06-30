package com.library.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.user.dto.*;
import com.library.user.exception.GlobalExceptionHandler;
import com.library.user.exception.ResourceNotFoundException;
import com.library.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  UserService userService;
    @Autowired ObjectMapper objectMapper;

    private UserDto sampleDto() {
        return UserDto.builder()
                .id(UUID.randomUUID().toString())
                .name("Alice").mobile("9876543210")
                .email("alice@test.com").role("STUDENT").isActive(true)
                .build();
    }

    // ── GET /api/users/me ─────────────────────────────────────────────────────

    @Test
    void getMyProfile_withHeader_returns200() throws Exception {
        when(userService.getProfile(any())).thenReturn(sampleDto());

        mockMvc.perform(get("/api/users/me")
                .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Alice"));
    }

    @Test
    void getMyProfile_missingHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getMyProfile_userNotFound_returns404() throws Exception {
        when(userService.getProfile(any()))
                .thenThrow(new ResourceNotFoundException("User not found: abc"));

        mockMvc.perform(get("/api/users/me")
                .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── GET /api/users/{userId} ───────────────────────────────────────────────

    @Test
    void getUserById_withRole_returns200() throws Exception {
        when(userService.getProfile(any())).thenReturn(sampleDto());

        mockMvc.perform(get("/api/users/" + UUID.randomUUID())
                .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getUserById_missingRoleHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/users/" + UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }

    // ── PATCH /api/users/me ───────────────────────────────────────────────────

    @Test
    void updateProfile_valid_returns200() throws Exception {
        when(userService.updateProfile(any(), any())).thenReturn(sampleDto());

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setName("Alice Updated");

        mockMvc.perform(patch("/api/users/me")
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateProfile_nameTooShort_returns400() throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setName("A"); // @Size(min=2)

        mockMvc.perform(patch("/api/users/me")
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateProfile_missingHeader_returns400() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfile_duplicateEmail_returns400() throws Exception {
        when(userService.updateProfile(any(), any()))
                .thenThrow(new IllegalArgumentException("Email already in use by another account"));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setEmail("taken@test.com");

        mockMvc.perform(patch("/api/users/me")
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already in use")));
    }

    // ── POST /api/users/me/photo ──────────────────────────────────────────────

    @Test
    void uploadPhoto_validFile_returns200() throws Exception {
        when(userService.uploadPhoto(any(), any())).thenReturn(
                PhotoUploadResponse.builder()
                        .photoUrl("/java-uploads/photos/user_test.jpg")
                        .message("Photo uploaded successfully")
                        .build());

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-image".getBytes());

        mockMvc.perform(multipart("/api/users/me/photo")
                .file(file)
                .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.photoUrl").value("/java-uploads/photos/user_test.jpg"))
                .andExpect(jsonPath("$.data.message").value("Photo uploaded successfully"));
    }

    @Test
    void uploadPhoto_missingHeader_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake".getBytes());

        mockMvc.perform(multipart("/api/users/me/photo").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadPhoto_invalidFileType_returns400() throws Exception {
        when(userService.uploadPhoto(any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid file type. Only JPEG, PNG, WebP allowed."));

        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "pdf-content".getBytes());

        mockMvc.perform(multipart("/api/users/me/photo")
                .file(file)
                .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid file type")));
    }

    @Test
    void uploadPhoto_ioException_returns500() throws Exception {
        when(userService.uploadPhoto(any(), any()))
                .thenThrow(new IOException("Disk full"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "data".getBytes());

        mockMvc.perform(multipart("/api/users/me/photo")
                .file(file)
                .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isInternalServerError());
    }

    // ── DELETE /api/users/me/photo ────────────────────────────────────────────

    @Test
    void deletePhoto_valid_returns200() throws Exception {
        doNothing().when(userService).deletePhoto(any());

        mockMvc.perform(delete("/api/users/me/photo")
                .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Photo removed successfully"));
    }

    @Test
    void deletePhoto_missingHeader_returns400() throws Exception {
        mockMvc.perform(delete("/api/users/me/photo"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletePhoto_noPhoto_returns400() throws Exception {
        doThrow(new IllegalArgumentException("No photo to delete."))
                .when(userService).deletePhoto(any());

        mockMvc.perform(delete("/api/users/me/photo")
                .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No photo to delete."));
    }
}
