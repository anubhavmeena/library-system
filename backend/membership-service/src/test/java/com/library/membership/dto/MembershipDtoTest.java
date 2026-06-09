package com.library.membership.dto;

import com.library.membership.entity.Membership;
import com.library.membership.entity.Plan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class MembershipDtoTest {

    private Plan buildPlan() {
        return Plan.builder()
                .id(UUID.randomUUID())
                .name("Full Day Plan")
                .planType(Plan.PlanType.FULL_DAY)
                .price(BigDecimal.valueOf(600))
                .durationDays(30)
                .isActive(true)
                .build();
    }

    private Membership buildMembership() {
        UUID seatId = UUID.randomUUID();
        return Membership.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .plan(buildPlan())
                .seatId(seatId)
                .seatNumber("B12")
                .shift("FULL_DAY")
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 1, 31))
                .status(Membership.Status.ACTIVE)
                .createdAt(LocalDateTime.of(2025, 1, 1, 10, 0))
                .build();
    }

    @Test
    void fromEntity_allFieldsMapped() {
        Membership m = buildMembership();
        MembershipDto dto = MembershipDto.fromEntity(m);

        assertThat(dto.getId()).isEqualTo(m.getId().toString());
        assertThat(dto.getUserId()).isEqualTo(m.getUserId().toString());
        assertThat(dto.getPlanId()).isEqualTo(m.getPlan().getId().toString());
        assertThat(dto.getPlanName()).isEqualTo("Full Day Plan");
        assertThat(dto.getPlanType()).isEqualTo("FULL_DAY");
        assertThat(dto.getSeatId()).isEqualTo(m.getSeatId().toString());
        assertThat(dto.getSeatNumber()).isEqualTo("B12");
        assertThat(dto.getShift()).isEqualTo("FULL_DAY");
        assertThat(dto.getStartDate()).isEqualTo("2025-01-01");
        assertThat(dto.getEndDate()).isEqualTo("2025-01-31");
        assertThat(dto.getStatus()).isEqualTo("ACTIVE");
        assertThat(dto.getCreatedAt()).isNotNull();
    }

    @Test
    void fromEntity_nullSeatId_seatIdNullInDto() {
        Membership m = buildMembership();
        m.setSeatId(null);

        assertThat(MembershipDto.fromEntity(m).getSeatId()).isNull();
    }

    @Test
    void fromEntity_nullCreatedAt_createdAtNullInDto() {
        Membership m = buildMembership();
        m.setCreatedAt(null);

        assertThat(MembershipDto.fromEntity(m).getCreatedAt()).isNull();
    }

    @Test
    void fromEntity_nullStartDate_startDateNullInDto() {
        Membership m = buildMembership();
        m.setStartDate(null);

        assertThat(MembershipDto.fromEntity(m).getStartDate()).isNull();
    }

    @Test
    void fromEntity_pendingStatus_mappedCorrectly() {
        Membership m = buildMembership();
        m.setStatus(Membership.Status.PENDING);

        assertThat(MembershipDto.fromEntity(m).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void fromEntity_expiredStatus_mappedCorrectly() {
        Membership m = buildMembership();
        m.setStatus(Membership.Status.EXPIRED);

        assertThat(MembershipDto.fromEntity(m).getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void fromEntity_cancelledStatus_mappedCorrectly() {
        Membership m = buildMembership();
        m.setStatus(Membership.Status.CANCELLED);

        assertThat(MembershipDto.fromEntity(m).getStatus()).isEqualTo("CANCELLED");
    }
}
