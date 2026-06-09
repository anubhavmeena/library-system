package com.library.membership.dto;

import com.library.membership.entity.Plan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PlanDtoTest {

    private Plan buildPlan() {
        return Plan.builder()
                .id(UUID.randomUUID())
                .name("Full Day Plan")
                .planType(Plan.PlanType.FULL_DAY)
                .price(BigDecimal.valueOf(600))
                .durationDays(30)
                .description("Study all day")
                .isActive(true)
                .build();
    }

    @Test
    void fromEntity_allFieldsMapped() {
        Plan plan = buildPlan();
        PlanDto dto = PlanDto.fromEntity(plan);

        assertThat(dto.getId()).isEqualTo(plan.getId().toString());
        assertThat(dto.getName()).isEqualTo("Full Day Plan");
        assertThat(dto.getPlanType()).isEqualTo("FULL_DAY");
        assertThat(dto.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(dto.getDurationDays()).isEqualTo(30);
        assertThat(dto.getDescription()).isEqualTo("Study all day");
        assertThat(dto.isActive()).isTrue();
    }

    @Test
    void fromEntity_halfDayPlan_planTypeMappedCorrectly() {
        Plan plan = Plan.builder()
                .id(UUID.randomUUID())
                .name("Half Day Plan")
                .planType(Plan.PlanType.HALF_DAY)
                .price(BigDecimal.valueOf(400))
                .durationDays(30)
                .isActive(true)
                .build();

        PlanDto dto = PlanDto.fromEntity(plan);

        assertThat(dto.getPlanType()).isEqualTo("HALF_DAY");
    }

    @Test
    void fromEntity_isActiveFalse_isActiveFalseInDto() {
        Plan plan = buildPlan();
        plan.setIsActive(false);

        assertThat(PlanDto.fromEntity(plan).isActive()).isFalse();
    }

    @Test
    void fromEntity_isActiveNull_isActiveFalseInDto() {
        // Boolean.TRUE.equals(null) = false — null is treated as inactive
        Plan plan = buildPlan();
        plan.setIsActive(null);

        assertThat(PlanDto.fromEntity(plan).isActive()).isFalse();
    }

    @Test
    void fromEntity_nullDescription_nullInDto() {
        Plan plan = buildPlan();
        plan.setDescription(null);

        assertThat(PlanDto.fromEntity(plan).getDescription()).isNull();
    }
}
