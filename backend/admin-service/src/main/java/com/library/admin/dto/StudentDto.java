package com.library.admin.dto;

import com.library.admin.entity.Membership;
import com.library.admin.entity.User;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StudentDto {

    // ── Profile ────────────────────────────────────────────────────────────
    private String  id;
    private String  name;
    private String  mobile;
    private String  email;
    private String  address;
    private String  gender;
    private String  dateOfBirth;
    private String  photoUrl;
    private String  aadhaarUrl;
    private String  joinedAt;

    // ── Active membership (all nullable if student has no plan) ────────────
    private String membershipId;
    private String membershipPlanId;  // raw UUID of the plan
    private String planName;
    private String planType;          // HALF_DAY | FULL_DAY
    private String seatNumber;
    private String shift;             // MORNING | EVENING | FULL_DAY
    private String membershipStart;
    private String membershipEnd;
    private String membershipStatus;  // ACTIVE | GRACE | EXPIRED | PENDING | CANCELLED
    private int    daysRemaining;
    private String     paymentMode;    // CASH | ONLINE | null
    private BigDecimal pendingAmount; // outstanding cash balance, 0 if fully paid
    private BigDecimal duesAmount;    // overdue grace-period dues, distinct from pendingAmount above
    private String displayStatus;     // NEW | PAID | PENDING | GRACE | EXPIRED | RELEASED — see StudentStatusResolver

    public static StudentDto fromEntities(User user, Membership membership) {
        StudentDtoBuilder b = StudentDto.builder()
                .id(user.getId().toString())
                .name(user.getName())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .address(user.getAddress())
                .gender(user.getGender())
                .dateOfBirth(
                        user.getDateOfBirth() != null
                                ? user.getDateOfBirth().toString() : null)
                .photoUrl(user.getPhotoUrl())
                .aadhaarUrl(user.getAadhaarUrl())
                .joinedAt(
                        user.getCreatedAt() != null
                                ? user.getCreatedAt().toString() : null);

        if (membership != null) {
            int days = (int) ChronoUnit.DAYS.between(
                    LocalDate.now(), membership.getEndDate());

            b.membershipId(membership.getId().toString())
                    .seatNumber(membership.getSeatNumber())
                    .shift(membership.getShift())
                    .membershipStart(membership.getStartDate().toString())
                    .membershipEnd(membership.getEndDate().toString())
                    .membershipStatus(membership.getStatus().name())
                    .daysRemaining(Math.max(0, days))
                    .duesAmount(membership.getDuesAmount());
        }

        return b.build();
    }
}