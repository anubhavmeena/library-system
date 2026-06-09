package com.library.admin.dto;

import com.library.admin.entity.Membership;
import com.library.admin.entity.User;
import lombok.*;
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
    private Boolean isActive;
    private String  joinedAt;

    // ── Active membership (all nullable if student has no plan) ────────────
    private String membershipId;
    private String planName;
    private String planType;          // HALF_DAY | FULL_DAY
    private String seatNumber;
    private String shift;             // MORNING | EVENING | FULL_DAY
    private String membershipStart;
    private String membershipEnd;
    private String membershipStatus;  // ACTIVE | EXPIRED | PENDING | CANCELLED
    private int    daysRemaining;

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
                .isActive(Boolean.TRUE.equals(user.getIsActive()))
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
                    .daysRemaining(Math.max(0, days));
        }

        return b.build();
    }
}