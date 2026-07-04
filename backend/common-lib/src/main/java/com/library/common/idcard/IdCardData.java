package com.library.common.idcard;

// Shared input for the single student ID card design used everywhere (the
// branded Target Zone card, originally notification-service's
// StudentIdCardPdfService). Both membership-service's on-demand download and
// notification-service's auto-sent-on-booking card map their own
// differently-shaped inputs (MembershipDto+UserProfileDto vs.
// BookingConfirmedEvent) into this record before rendering.
public record IdCardData(
        String name,
        String mobile,
        String memberId,
        String planName,
        String validTillDisplay,
        byte[] photoBytes
) {
}
