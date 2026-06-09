package com.library.admin.dto;

import com.library.admin.entity.Membership;
import com.library.admin.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class StudentDtoTest {

    private User buildUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .name("Alice")
                .mobile("9876543210")
                .email("alice@example.com")
                .address("123 Main St")
                .gender("F")
                .dateOfBirth(LocalDate.of(1995, 5, 20))
                .photoUrl("/uploads/photos/alice.jpg")
                .isActive(true)
                .role(User.Role.STUDENT)
                .createdAt(LocalDateTime.of(2024, 1, 10, 9, 0))
                .build();
    }

    private Membership buildMembership(LocalDate endDate) {
        return Membership.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .planId(UUID.randomUUID())
                .seatNumber("B5")
                .shift("MORNING")
                .startDate(LocalDate.now().minusDays(25))
                .endDate(endDate)
                .status(Membership.Status.ACTIVE)
                .build();
    }

    // -- Null membership ----------------------------------------------------------

    @Test
    void fromEntities_nullMembership_profileFieldsPopulated() {
        User user = buildUser();
        StudentDto dto = StudentDto.fromEntities(user, null);

        assertThat(dto.getId()).isEqualTo(user.getId().toString());
        assertThat(dto.getName()).isEqualTo("Alice");
        assertThat(dto.getMobile()).isEqualTo("9876543210");
        assertThat(dto.getEmail()).isEqualTo("alice@example.com");
        assertThat(dto.getAddress()).isEqualTo("123 Main St");
        assertThat(dto.getGender()).isEqualTo("F");
        assertThat(dto.getPhotoUrl()).isEqualTo("/uploads/photos/alice.jpg");
        assertThat(dto.getIsActive()).isTrue();
        assertThat(dto.getDateOfBirth()).isEqualTo("1995-05-20");
        assertThat(dto.getJoinedAt()).isEqualTo("2024-01-10T09:00");
    }

    @Test
    void fromEntities_nullMembership_membershipFieldsAreNullOrZero() {
        StudentDto dto = StudentDto.fromEntities(buildUser(), null);

        assertThat(dto.getMembershipId()).isNull();
        assertThat(dto.getSeatNumber()).isNull();
        assertThat(dto.getShift()).isNull();
        assertThat(dto.getMembershipStart()).isNull();
        assertThat(dto.getMembershipEnd()).isNull();
        assertThat(dto.getMembershipStatus()).isNull();
        assertThat(dto.getDaysRemaining()).isZero(); // int primitive defaults to 0
    }

    // -- With active membership ---------------------------------------------------

    @Test
    void fromEntities_withMembership_membershipFieldsPopulated() {
        User user = buildUser();
        LocalDate endDate = LocalDate.now().plusDays(10);
        Membership mem = buildMembership(endDate);

        StudentDto dto = StudentDto.fromEntities(user, mem);

        assertThat(dto.getMembershipId()).isEqualTo(mem.getId().toString());
        assertThat(dto.getSeatNumber()).isEqualTo("B5");
        assertThat(dto.getShift()).isEqualTo("MORNING");
        assertThat(dto.getMembershipStart()).isEqualTo(mem.getStartDate().toString());
        assertThat(dto.getMembershipEnd()).isEqualTo(endDate.toString());
        assertThat(dto.getMembershipStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void fromEntities_daysRemaining_futureDate() {
        LocalDate endDate = LocalDate.now().plusDays(5);
        StudentDto dto = StudentDto.fromEntities(buildUser(), buildMembership(endDate));

        assertThat(dto.getDaysRemaining()).isEqualTo(5);
    }

    @Test
    void fromEntities_daysRemaining_expiresExactlyToday_isZero() {
        LocalDate endDate = LocalDate.now();
        StudentDto dto = StudentDto.fromEntities(buildUser(), buildMembership(endDate));

        assertThat(dto.getDaysRemaining()).isZero();
    }

    @Test
    void fromEntities_daysRemaining_alreadyExpired_clampedToZero() {
        LocalDate endDate = LocalDate.now().minusDays(3); // past
        StudentDto dto = StudentDto.fromEntities(buildUser(), buildMembership(endDate));

        assertThat(dto.getDaysRemaining()).isZero(); // not negative
    }

    @Test
    void fromEntities_isActive_falseWhenUserInactive() {
        User user = buildUser();
        user.setIsActive(false);
        StudentDto dto = StudentDto.fromEntities(user, null);

        assertThat(dto.getIsActive()).isFalse();
    }

    @Test
    void fromEntities_isActive_falseWhenIsActiveNull() {
        User user = buildUser();
        user.setIsActive(null);
        StudentDto dto = StudentDto.fromEntities(user, null);

        // Boolean.TRUE.equals(null) == false
        assertThat(dto.getIsActive()).isFalse();
    }

    @Test
    void fromEntities_nullDateOfBirth_yields_nullInDto() {
        User user = buildUser();
        user.setDateOfBirth(null);
        StudentDto dto = StudentDto.fromEntities(user, null);

        assertThat(dto.getDateOfBirth()).isNull();
    }

    @Test
    void fromEntities_nullCreatedAt_yields_nullJoinedAt() {
        User user = buildUser();
        user.setCreatedAt(null);
        StudentDto dto = StudentDto.fromEntities(user, null);

        assertThat(dto.getJoinedAt()).isNull();
    }

    @Test
    void fromEntities_planNameAndPlanType_areNullEvenWithMembership() {
        // Known gap: fromEntities does not populate planName or planType.
        // This test documents the current behaviour so regressions are visible.
        Membership mem = buildMembership(LocalDate.now().plusDays(10));
        StudentDto dto = StudentDto.fromEntities(buildUser(), mem);

        assertThat(dto.getPlanName()).isNull();
        assertThat(dto.getPlanType()).isNull();
    }
}
