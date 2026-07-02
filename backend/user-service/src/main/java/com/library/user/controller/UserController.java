package com.library.user.controller;

import com.library.user.dto.*;
import com.library.user.service.UserService;
import com.library.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;

    // X-User-Id is injected by the API Gateway after JWT validation

    @GetMapping("/admin-contact")
    public ResponseEntity<ApiResponse<AdminContactDto>> getAdminContact() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAdminContact()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> getMyProfile(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getProfile(userId)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserDto>> getUserById(
            @PathVariable String userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success(userService.getProfile(userId)));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> updateProfile(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateProfile(userId, request)));
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PhotoUploadResponse>> uploadPhoto(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(ApiResponse.success(userService.uploadPhoto(userId, file)));
    }

    @DeleteMapping("/me/photo")
    public ResponseEntity<ApiResponse<String>> deletePhoto(
            @RequestHeader("X-User-Id") String userId) throws IOException {
        userService.deletePhoto(userId);
        return ResponseEntity.ok(ApiResponse.success("Photo removed successfully"));
    }

    @PostMapping(value = "/me/aadhaar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PhotoUploadResponse>> uploadAadhaar(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(ApiResponse.success(userService.uploadAadhaar(userId, file)));
    }

    @DeleteMapping("/me/aadhaar")
    public ResponseEntity<ApiResponse<String>> deleteAadhaar(
            @RequestHeader("X-User-Id") String userId) throws IOException {
        userService.deleteAadhaar(userId);
        return ResponseEntity.ok(ApiResponse.success("Aadhaar removed successfully"));
    }

    // Called directly pod-to-pod by notification-service (bypasses the gateway,
    // same pattern as membership-service's IdCardService fetching photo bytes) to
    // host a generated payment-receipt PDF so it can be linked from WhatsApp.
    @PostMapping(value = "/internal/receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ReceiptUploadResponse>> uploadReceipt(
            @RequestParam("invoiceId") String invoiceId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(ApiResponse.success(userService.uploadReceipt(invoiceId, file)));
    }
}