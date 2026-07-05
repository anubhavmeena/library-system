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
    //
    // defaultHindiEnabled/defaultHindiText* let a specific key ship bilingual out
    // of the box (see GRACE_DUES_CLEARED below) instead of every key defaulting
    // to Hindi off until an admin manually opts in. KeyDefault.of(...) is the
    // shorthand for the common case (Hindi off, no seeded text).
    private record KeyDefault(
            boolean defaultSendToStudent, boolean defaultSendToAdmin,
            boolean studentEditable, boolean adminEditable, boolean hindiEditable,
            boolean defaultHindiEnabled, String defaultHindiTextStudent, String defaultHindiTextAdmin) {

        static KeyDefault of(boolean defaultSendToStudent, boolean defaultSendToAdmin,
                              boolean studentEditable, boolean adminEditable, boolean hindiEditable) {
            return new KeyDefault(defaultSendToStudent, defaultSendToAdmin, studentEditable, adminEditable,
                    hindiEditable, false, null, null);
        }
    }

    // Seeded Hindi translation for GRACE_DUES_CLEARED — kept as a constant so
    // notification-service's fail-open default (used before this row has ever
    // been lazily created) can carry the identical text.
    static final String GRACE_DUES_CLEARED_HINDI_STUDENT =
            "आपकी ग्रेस पीरियड की बकाया राशि जमा हो गई है और आपकी सदस्यता फिर से सक्रिय कर दी गई है। " +
                    "आपकी सीट सुरक्षित है। धन्यवाद।\n📚 टारगेट ज़ोन लाइब्रेरी टीम";

    private static final Map<String, KeyDefault> NOTIFICATION_DEFAULTS = new LinkedHashMap<>();
    static {
        NOTIFICATION_DEFAULTS.put("BOOKING_CONFIRMED",     KeyDefault.of(true, true, true, true, true));
        NOTIFICATION_DEFAULTS.put("STUDENT_ID_CARD",       KeyDefault.of(true, true, true, true, true));
        NOTIFICATION_DEFAULTS.put("USER_REGISTERED",       KeyDefault.of(true, true, true, true, true));
        NOTIFICATION_DEFAULTS.put("RENEWAL_REMINDER",      KeyDefault.of(true, false, true, false, true));
        NOTIFICATION_DEFAULTS.put("PENDING_FEE_REMINDER",  KeyDefault.of(true, false, true, false, true));
        NOTIFICATION_DEFAULTS.put("GRACE_DUES_REMINDER",   KeyDefault.of(true, false, true, false, true));
        NOTIFICATION_DEFAULTS.put("MEMBERSHIP_GRACE",      KeyDefault.of(true, true, true, true, true));
        NOTIFICATION_DEFAULTS.put("PENDING_FEE_CLEARED",   KeyDefault.of(true, true, true, true, true));
        NOTIFICATION_DEFAULTS.put("PAYMENT_RECEIPT",       KeyDefault.of(true, true, true, true, true));
        // Recipient is inherently "students"; sendToStudent doubles as the
        // feature's master on/off switch. sendToAdmin stays fixed true,
        // matching the existing unconditional echo-to-admin confirmation.
        NOTIFICATION_DEFAULTS.put("ADMIN_BROADCAST",       KeyDefault.of(true, true, true, false, false));
        // Ships bilingual by default — clearing a student's grace dues should
        // read in Hindi too without an admin having to configure it first.
        NOTIFICATION_DEFAULTS.put("GRACE_DUES_CLEARED",    new KeyDefault(true, true, true, true, true,
                true, GRACE_DUES_CLEARED_HINDI_STUDENT, null));
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
                    .hindiEnabled(def.defaultHindiEnabled())
                    .hindiTextStudent(def.defaultHindiTextStudent())
                    .hindiTextAdmin(def.defaultHindiTextAdmin())
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
