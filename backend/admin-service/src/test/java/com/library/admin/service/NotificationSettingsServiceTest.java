package com.library.admin.service;

import com.library.admin.dto.NotificationSettingDto;
import com.library.admin.dto.UpdateNotificationSettingRequest;
import com.library.admin.entity.NotificationSetting;
import com.library.admin.exception.ResourceNotFoundException;
import com.library.admin.repository.NotificationSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsServiceTest {

    @Mock NotificationSettingRepository notificationSettingRepository;

    @InjectMocks NotificationSettingsService notificationSettingsService;

    // ── getAll — lazy seeding ────────────────────────────────────────────────

    @Test
    void getAll_seedsAllTenCatalogKeys_whenNoneExistYet() {
        when(notificationSettingRepository.findById(anyString())).thenReturn(Optional.empty());
        when(notificationSettingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<NotificationSettingDto> all = notificationSettingsService.getAll();

        assertThat(all).extracting(NotificationSettingDto::getNotificationKey)
                .containsExactlyInAnyOrder(
                        "BOOKING_CONFIRMED", "STUDENT_ID_CARD", "USER_REGISTERED",
                        "RENEWAL_REMINDER", "PENDING_FEE_REMINDER", "GRACE_DUES_REMINDER",
                        "MEMBERSHIP_GRACE", "PENDING_FEE_CLEARED", "PAYMENT_RECEIPT",
                        "ADMIN_BROADCAST", "GRACE_DUES_CLEARED");
        verify(notificationSettingRepository, times(11)).save(any());
    }

    @Test
    void getAll_doesNotOverwrite_existingRow() {
        NotificationSetting existing = NotificationSetting.builder()
                .notificationKey("BOOKING_CONFIRMED")
                .sendToStudent(false).sendToAdmin(false)
                .hindiEnabled(true).hindiTextStudent("custom text")
                .build();
        // Every key falls back to findById; only BOOKING_CONFIRMED already exists.
        when(notificationSettingRepository.findById("BOOKING_CONFIRMED")).thenReturn(Optional.of(existing));
        when(notificationSettingRepository.findById(argThat(k -> !"BOOKING_CONFIRMED".equals(k))))
                .thenReturn(Optional.empty());
        when(notificationSettingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<NotificationSettingDto> all = notificationSettingsService.getAll();

        NotificationSettingDto bookingConfirmed = all.stream()
                .filter(d -> d.getNotificationKey().equals("BOOKING_CONFIRMED"))
                .findFirst().orElseThrow();
        assertThat(bookingConfirmed.isSendToStudent()).isFalse();
        assertThat(bookingConfirmed.isHindiEnabled()).isTrue();
        assertThat(bookingConfirmed.getHindiTextStudent()).isEqualTo("custom text");
        verify(notificationSettingRepository, never()).save(existing);
    }

    @Test
    void getAll_graceDuesCleared_defaultsToHindiEnabledWithSeededText() {
        when(notificationSettingRepository.findById(anyString())).thenReturn(Optional.empty());
        when(notificationSettingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<NotificationSettingDto> all = notificationSettingsService.getAll();

        NotificationSettingDto graceDuesCleared = all.stream()
                .filter(d -> d.getNotificationKey().equals("GRACE_DUES_CLEARED"))
                .findFirst().orElseThrow();
        assertThat(graceDuesCleared.isHindiEnabled()).isTrue();
        assertThat(graceDuesCleared.getHindiTextStudent()).isNotBlank();
        assertThat(graceDuesCleared.getHindiTextAdmin()).isNull();
    }

    @Test
    void getAll_everyOtherKey_defaultsToHindiDisabledWithNoSeededText() {
        when(notificationSettingRepository.findById(anyString())).thenReturn(Optional.empty());
        when(notificationSettingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<NotificationSettingDto> all = notificationSettingsService.getAll();

        all.stream()
                .filter(d -> !d.getNotificationKey().equals("GRACE_DUES_CLEARED"))
                .forEach(d -> {
                    assertThat(d.isHindiEnabled()).as(d.getNotificationKey() + " hindiEnabled").isFalse();
                    assertThat(d.getHindiTextStudent()).as(d.getNotificationKey() + " hindiTextStudent").isNull();
                });
    }

    @Test
    void getAll_lockedRecipientKeys_reflectEditableFlagsInDto() {
        when(notificationSettingRepository.findById(anyString())).thenReturn(Optional.empty());
        when(notificationSettingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<NotificationSettingDto> all = notificationSettingsService.getAll();

        NotificationSettingDto renewalReminder = all.stream()
                .filter(d -> d.getNotificationKey().equals("RENEWAL_REMINDER"))
                .findFirst().orElseThrow();
        assertThat(renewalReminder.isStudentEditable()).isTrue();
        assertThat(renewalReminder.isAdminEditable()).isFalse();
        assertThat(renewalReminder.isSendToAdmin()).isFalse();

        NotificationSettingDto adminBroadcast = all.stream()
                .filter(d -> d.getNotificationKey().equals("ADMIN_BROADCAST"))
                .findFirst().orElseThrow();
        assertThat(adminBroadcast.isAdminEditable()).isFalse();
        assertThat(adminBroadcast.isHindiEditable()).isFalse();
    }

    // ── updateOne — unknown key ──────────────────────────────────────────────

    @Test
    void updateOne_unknownKey_throwsResourceNotFoundException() {
        UpdateNotificationSettingRequest req = new UpdateNotificationSettingRequest();
        req.setSendToStudent(true);
        req.setSendToAdmin(true);
        req.setHindiEnabled(false);

        assertThatThrownBy(() -> notificationSettingsService.updateOne("NOT_A_REAL_KEY", req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("NOT_A_REAL_KEY");
        verifyNoInteractions(notificationSettingRepository);
    }

    // ── updateOne — locked-field rejection ───────────────────────────────────

    @Test
    void updateOne_lockedAdminField_rejectsChangeToTrue() {
        stubExistingRow("RENEWAL_REMINDER", true, false, false, null, null);

        UpdateNotificationSettingRequest req = new UpdateNotificationSettingRequest();
        req.setSendToStudent(true);
        req.setSendToAdmin(true); // RENEWAL_REMINDER admin is locked at false
        req.setHindiEnabled(false);

        assertThatThrownBy(() -> notificationSettingsService.updateOne("RENEWAL_REMINDER", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not support changing Send to Admin");
        verify(notificationSettingRepository, never()).save(any());
    }

    @Test
    void updateOne_lockedStudentField_rejectsChangeToFalse() {
        // ADMIN_BROADCAST: studentEditable=true (it's the master switch) but
        // adminEditable=false — flipping sendToAdmin should be rejected even
        // though sendToStudent is freely editable for this key.
        stubExistingRow("ADMIN_BROADCAST", true, true, false, null, null);

        UpdateNotificationSettingRequest req = new UpdateNotificationSettingRequest();
        req.setSendToStudent(false);
        req.setSendToAdmin(false);
        req.setHindiEnabled(false);

        assertThatThrownBy(() -> notificationSettingsService.updateOne("ADMIN_BROADCAST", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not support changing Send to Admin");
    }

    @Test
    void updateOne_hindiNotEditable_rejectsEnablingHindi() {
        stubExistingRow("ADMIN_BROADCAST", true, true, false, null, null);

        UpdateNotificationSettingRequest req = new UpdateNotificationSettingRequest();
        req.setSendToStudent(true);
        req.setSendToAdmin(true);
        req.setHindiEnabled(true);

        assertThatThrownBy(() -> notificationSettingsService.updateOne("ADMIN_BROADCAST", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not support a Hindi translation");
    }

    @Test
    void updateOne_hindiNotEditable_rejectsSettingHindiTextEvenIfDisabled() {
        stubExistingRow("ADMIN_BROADCAST", true, true, false, null, null);

        UpdateNotificationSettingRequest req = new UpdateNotificationSettingRequest();
        req.setSendToStudent(true);
        req.setSendToAdmin(true);
        req.setHindiEnabled(false);
        req.setHindiTextStudent("कोई पाठ");

        assertThatThrownBy(() -> notificationSettingsService.updateOne("ADMIN_BROADCAST", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not support a Hindi translation");
    }

    // ── updateOne — happy path ───────────────────────────────────────────────

    @Test
    void updateOne_fullyEditableKey_appliesAllFields() {
        stubExistingRow("BOOKING_CONFIRMED", true, true, false, null, null);
        when(notificationSettingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateNotificationSettingRequest req = new UpdateNotificationSettingRequest();
        req.setSendToStudent(false);
        req.setSendToAdmin(true);
        req.setHindiEnabled(true);
        req.setHindiTextStudent("student hindi");
        req.setHindiTextAdmin("admin hindi");

        NotificationSettingDto result = notificationSettingsService.updateOne("BOOKING_CONFIRMED", req);

        assertThat(result.isSendToStudent()).isFalse();
        assertThat(result.isSendToAdmin()).isTrue();
        assertThat(result.isHindiEnabled()).isTrue();
        assertThat(result.getHindiTextStudent()).isEqualTo("student hindi");
        assertThat(result.getHindiTextAdmin()).isEqualTo("admin hindi");

        ArgumentCaptor<NotificationSetting> captor = ArgumentCaptor.forClass(NotificationSetting.class);
        verify(notificationSettingRepository).save(captor.capture());
        assertThat(captor.getValue().isSendToStudent()).isFalse();
        assertThat(captor.getValue().isSendToAdmin()).isTrue();
    }

    @Test
    void updateOne_hindiEditableKey_savesTextEvenWhileHindiDisabled() {
        // RENEWAL_REMINDER has hindiEditable=true, so the text field is only
        // gated by def.hindiEditable() in updateOne() — an admin can draft a
        // translation before flipping hindiEnabled on (NotificationService's
        // withHindi() is what actually gates using it at send time).
        stubExistingRow("RENEWAL_REMINDER", true, false, true, "old hindi text", null);
        when(notificationSettingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateNotificationSettingRequest req = new UpdateNotificationSettingRequest();
        req.setSendToStudent(true);
        req.setSendToAdmin(false);
        req.setHindiEnabled(false);
        req.setHindiTextStudent("attempted new text");

        NotificationSettingDto result = notificationSettingsService.updateOne("RENEWAL_REMINDER", req);

        assertThat(result.isHindiEnabled()).isFalse();
        assertThat(result.getHindiTextStudent()).isEqualTo("attempted new text");
    }

    private void stubExistingRow(String key, boolean sendToStudent, boolean sendToAdmin,
                                  boolean hindiEnabled, String hindiTextStudent, String hindiTextAdmin) {
        NotificationSetting existing = NotificationSetting.builder()
                .notificationKey(key)
                .sendToStudent(sendToStudent)
                .sendToAdmin(sendToAdmin)
                .hindiEnabled(hindiEnabled)
                .hindiTextStudent(hindiTextStudent)
                .hindiTextAdmin(hindiTextAdmin)
                .build();
        when(notificationSettingRepository.findById(key)).thenReturn(Optional.of(existing));
    }
}
