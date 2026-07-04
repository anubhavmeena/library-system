package com.library.common.idcard;

// Consolidates the "first 8 chars of the membership UUID, uppercased" member
// ID scheme, which was previously reimplemented independently three times
// (membership-service's IdCardService.buildQrData, notification-service's
// StudentIdCardPdfService.buildIdNumber, and NotificationService.buildIdNumber).
public final class IdCardIdGenerator {

    private IdCardIdGenerator() {
    }

    public static String shortId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return "UNKNOWN";
        }
        return rawId.length() >= 8
                ? rawId.substring(0, 8).toUpperCase()
                : rawId.toUpperCase();
    }
}
