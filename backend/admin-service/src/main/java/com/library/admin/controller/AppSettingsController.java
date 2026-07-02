package com.library.admin.controller;

import com.library.admin.dto.AppSettingsDto;
import com.library.admin.dto.SaveAppSettingsRequest;
import com.library.admin.service.AppSettingsService;
import com.library.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AppSettingsController {

    private final AppSettingsService appSettingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<AppSettingsDto>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(appSettingsService.getSettings()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AppSettingsDto>> saveSettings(
            @Valid @RequestBody SaveAppSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(appSettingsService.saveSettings(request)));
    }
}
