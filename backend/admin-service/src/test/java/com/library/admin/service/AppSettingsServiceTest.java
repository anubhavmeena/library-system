package com.library.admin.service;

import com.library.admin.dto.AppSettingsDto;
import com.library.admin.dto.SaveAppSettingsRequest;
import com.library.admin.entity.AppSettings;
import com.library.admin.repository.AppSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppSettingsServiceTest {

    @Mock AppSettingsRepository appSettingsRepository;

    @InjectMocks AppSettingsService appSettingsService;

    @Test
    void getSettings_noRowYet_createsSingletonWithDefaults() {
        when(appSettingsRepository.findById(1L)).thenReturn(Optional.empty());
        when(appSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppSettingsDto dto = appSettingsService.getSettings();

        assertThat(dto.getWifiName()).isEmpty();
        assertThat(dto.getWifiPassword()).isEmpty();
        assertThat(dto.getGraceDays()).isEqualTo(AppSettingsService.DEFAULT_GRACE_DAYS);
        assertThat(dto.getConvenienceFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getWaterTankerRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.isCouponsEnabled()).isTrue();

        ArgumentCaptor<AppSettings> captor = ArgumentCaptor.forClass(AppSettings.class);
        verify(appSettingsRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
    }

    @Test
    void getSettings_existingRow_returnsStoredValuesWithoutCreatingNewRow() {
        AppSettings existing = AppSettings.builder()
                .id(1L).wifiName("LibraryWifi").wifiPassword("secret123")
                .graceDays(15).convenienceFee(new BigDecimal("20.00"))
                .waterTankerRate(new BigDecimal("500.00"))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 12, 0))
                .build();
        when(appSettingsRepository.findById(1L)).thenReturn(Optional.of(existing));

        AppSettingsDto dto = appSettingsService.getSettings();

        assertThat(dto.getWifiName()).isEqualTo("LibraryWifi");
        assertThat(dto.getGraceDays()).isEqualTo(15);
        assertThat(dto.getConvenienceFee()).isEqualByComparingTo("20.00");
        assertThat(dto.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 0).toString());
        verify(appSettingsRepository, never()).save(any());
    }

    @Test
    void saveSettings_updatesExistingSingletonRow() {
        AppSettings existing = AppSettings.builder()
                .id(1L).wifiName("Old").wifiPassword("old-pass")
                .graceDays(10).convenienceFee(BigDecimal.ZERO).waterTankerRate(BigDecimal.ZERO)
                .build();
        when(appSettingsRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(appSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SaveAppSettingsRequest req = new SaveAppSettingsRequest();
        req.setWifiName("NewWifi");
        req.setWifiPassword("new-pass");
        req.setGraceDays(20);
        req.setConvenienceFee(new BigDecimal("30.00"));
        req.setWaterTankerRate(new BigDecimal("600.00"));
        req.setCouponsEnabled(false);

        AppSettingsDto dto = appSettingsService.saveSettings(req);

        assertThat(dto.getWifiName()).isEqualTo("NewWifi");
        assertThat(dto.getGraceDays()).isEqualTo(20);
        assertThat(dto.getConvenienceFee()).isEqualByComparingTo("30.00");
        assertThat(dto.getWaterTankerRate()).isEqualByComparingTo("600.00");
        assertThat(dto.isCouponsEnabled()).isFalse();

        ArgumentCaptor<AppSettings> captor = ArgumentCaptor.forClass(AppSettings.class);
        verify(appSettingsRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L); // singleton id preserved, never a new row
    }

    @Test
    void saveSettings_noRowYetEither_createsThenUpdatesInOneCall() {
        when(appSettingsRepository.findById(1L)).thenReturn(Optional.empty());
        when(appSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SaveAppSettingsRequest req = new SaveAppSettingsRequest();
        req.setWifiName("FreshWifi");
        req.setWifiPassword("fresh-pass");
        req.setGraceDays(7);
        req.setConvenienceFee(new BigDecimal("10.00"));
        req.setWaterTankerRate(new BigDecimal("400.00"));
        req.setCouponsEnabled(true);

        AppSettingsDto dto = appSettingsService.saveSettings(req);

        assertThat(dto.getWifiName()).isEqualTo("FreshWifi");
        assertThat(dto.getGraceDays()).isEqualTo(7);
        // getOrCreateEntity's initial save (defaults) + saveSettings' own save (applied values)
        verify(appSettingsRepository, times(2)).save(any());
    }
}
