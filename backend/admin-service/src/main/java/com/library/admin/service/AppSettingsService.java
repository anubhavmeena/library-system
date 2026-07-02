package com.library.admin.service;

import com.library.admin.dto.AppSettingsDto;
import com.library.admin.dto.SaveAppSettingsRequest;
import com.library.admin.entity.AppSettings;
import com.library.admin.repository.AppSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AppSettingsService {

    // Single source of truth for the bootstrap grace-period default — preserves
    // today's hardcoded behavior (StudentStatusResolver used to define this
    // itself) until an admin visits the settings page and saves a different value.
    public static final int DEFAULT_GRACE_DAYS = 10;

    private static final long SETTINGS_ID = 1L;

    private final AppSettingsRepository appSettingsRepository;

    public AppSettingsDto getSettings() {
        return toDto(getOrCreateEntity());
    }

    @Transactional
    public AppSettingsDto saveSettings(SaveAppSettingsRequest req) {
        AppSettings settings = getOrCreateEntity();
        settings.setWifiName(req.getWifiName());
        settings.setWifiPassword(req.getWifiPassword());
        settings.setGraceDays(req.getGraceDays());
        settings.setConvenienceFee(req.getConvenienceFee());
        settings.setWaterTankerRate(req.getWaterTankerRate());
        settings = appSettingsRepository.save(settings);
        return toDto(settings);
    }

    private AppSettings getOrCreateEntity() {
        return appSettingsRepository.findById(SETTINGS_ID).orElseGet(() -> {
            AppSettings defaults = AppSettings.builder()
                    .id(SETTINGS_ID)
                    .wifiName("")
                    .wifiPassword("")
                    .graceDays(DEFAULT_GRACE_DAYS)
                    .convenienceFee(BigDecimal.ZERO)
                    .waterTankerRate(BigDecimal.ZERO)
                    .build();
            return appSettingsRepository.save(defaults);
        });
    }

    private AppSettingsDto toDto(AppSettings s) {
        return AppSettingsDto.builder()
                .wifiName(s.getWifiName())
                .wifiPassword(s.getWifiPassword())
                .graceDays(s.getGraceDays())
                .convenienceFee(s.getConvenienceFee())
                .waterTankerRate(s.getWaterTankerRate())
                .updatedAt(s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null)
                .build();
    }
}
