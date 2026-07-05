package com.library.admin.service;

import com.library.admin.dto.NotificationSettingDto;
import com.library.admin.dto.UpdateNotificationSettingRequest;
import com.library.admin.entity.NotificationSetting;
import com.library.admin.exception.ResourceNotFoundException;
import com.library.admin.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    // Fixed catalog of every notification type notification-service can send to
    // students/admins. Defaults match today's actual hardcoded send behavior
    // exactly, so introducing this table doesn't change anything until an admin
    // edits a row. `studentEditable`/`adminEditable` reflect which side actually
    // has message content implemented in NotificationService — see the
    // Notification Settings plan for the full per-key rationale. `hindiEditable`
    // is false only for ADMIN_BROADCAST, whose body is authored fresh by the
    // admin on every send rather than being a static template.
    private record KeyDefault(
            boolean defaultSendToStudent, boolean defaultSendToAdmin,
            boolean studentEditable, boolean adminEditable, boolean hindiEditable) {
    }

    private static final Map<String, KeyDefault> NOTIFICATION_DEFAULTS = new LinkedHashMap<>();
    static {
        NOTIFICATION_DEFAULTS.put("BOOKING_CONFIRMED",     new KeyDefault(true, true, true, true, true));
        NOTIFICATION_DEFAULTS.put("STUDENT_ID_CARD",       new KeyDefault(true, true, true, true, true));
        NOTIFICATION_DEFAULTS.put("USER_REGISTERED",       new KeyDefault(true, true, true, true, true));
        NOTIFICATION_DEFAULTS.put("RENEWAL_REMINDER",      new KeyDefault(true, false, true, false, true));
        NOTIFICATION_DEFAULTS.put("PENDING_FEE_REMINDER",  new KeyDefault(true, false, true, false, true));
        NOTIFICATION_DEFAULTS.put("GRACE_DUES_REMINDER",   new KeyDefault(true, false, true, false, true));
        NOTIFICATION_DEFAULTS.put("MEMBERSHIP_GRACE",      new KeyDefault(true, true, true, true, true));
        NOTIFICATION_DEFAULTS.put("PENDING_FEE_CLEARED",   new KeyDefault(true, true, true, true, true));
        NOTIFICATION_DEFAULTS.put("PAYMENT_RECEIPT",       new KeyDefault(true, true, true, true, true));
        // Recipient is inherently "students"; sendToStudent doubles as the
        // feature's master on/off switch. sendToAdmin stays fixed true,
        // matching the existing unconditional echo-to-admin confirmation.
        NOTIFICATION_DEFAULTS.put("ADMIN_BROADCAST",       new KeyDefault(true, true, true, false, false));
    }

    private final NotificationSettingRepository notificationSettingRepository;

    public List<NotificationSettingDto> getAll() {
        return NOTIFICATION_DEFAULTS.keySet().stream()
                .map(this::getOrCreate)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public NotificationSettingDto updateOne(String key, UpdateNotificationSettingRequest req) {
        KeyDefault def = NOTIFICATION_DEFAULTS.get(key);
        if (def == null) {
            throw new ResourceNotFoundException("Unknown notification key: " + key);
        }

        NotificationSetting setting = getOrCreate(key);

        if (!def.studentEditable() && req.getSendToStudent() != def.defaultSendToStudent()) {
            throw new IllegalArgumentException("'" + key + "' does not support changing Send to Student");
        }
        if (!def.adminEditable() && req.getSendToAdmin() != def.defaultSendToAdmin()) {
            throw new IllegalArgumentException("'" + key + "' does not support changing Send to Admin");
        }
        if (!def.hindiEditable() && (Boolean.TRUE.equals(req.getHindiEnabled())
                || hasValue(req.getHindiTextStudent()) || hasValue(req.getHindiTextAdmin()))) {
            throw new IllegalArgumentException("'" + key + "' does not support a Hindi translation");
        }

        setting.setSendToStudent(def.studentEditable() ? req.getSendToStudent() : def.defaultSendToStudent());
        setting.setSendToAdmin(def.adminEditable() ? req.getSendToAdmin() : def.defaultSendToAdmin());
        setting.setHindiEnabled(def.hindiEditable() && Boolean.TRUE.equals(req.getHindiEnabled()));
        setting.setHindiTextStudent(def.hindiEditable() ? req.getHindiTextStudent() : null);
        setting.setHindiTextAdmin(def.hindiEditable() ? req.getHindiTextAdmin() : null);

        return toDto(notificationSettingRepository.save(setting));
    }

    private NotificationSetting getOrCreate(String key) {
        return notificationSettingRepository.findById(key).orElseGet(() -> {
            KeyDefault def = NOTIFICATION_DEFAULTS.get(key);
            NotificationSetting defaults = NotificationSetting.builder()
                    .notificationKey(key)
                    .sendToStudent(def.defaultSendToStudent())
                    .sendToAdmin(def.defaultSendToAdmin())
                    .hindiEnabled(false)
                    .build();
            return notificationSettingRepository.save(defaults);
        });
    }

    private NotificationSettingDto toDto(NotificationSetting s) {
        KeyDefault def = NOTIFICATION_DEFAULTS.get(s.getNotificationKey());
        return NotificationSettingDto.builder()
                .notificationKey(s.getNotificationKey())
                .sendToStudent(s.isSendToStudent())
                .sendToAdmin(s.isSendToAdmin())
                .studentEditable(def.studentEditable())
                .adminEditable(def.adminEditable())
                .hindiEditable(def.hindiEditable())
                .hindiEnabled(s.isHindiEnabled())
                .hindiTextStudent(s.getHindiTextStudent())
                .hindiTextAdmin(s.getHindiTextAdmin())
                .updatedAt(s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null)
                .build();
    }

    private boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }
}
