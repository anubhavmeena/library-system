package com.library.admin.controller;

import com.library.admin.dto.NotificationSettingDto;
import com.library.admin.dto.UpdateNotificationSettingRequest;
import com.library.admin.service.NotificationSettingsService;
import com.library.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/notification-settings")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationSettingsController {

    private final NotificationSettingsService notificationSettingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationSettingDto>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(notificationSettingsService.getAll()));
    }

    @PatchMapping("/{key}")
    public ResponseEntity<ApiResponse<NotificationSettingDto>> updateOne(
            @PathVariable String key,
            @Valid @RequestBody UpdateNotificationSettingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(notificationSettingsService.updateOne(key, request)));
    }
}
