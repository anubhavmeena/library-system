package com.library.membership.service;

import com.library.membership.dto.PlanDto;
import com.library.membership.entity.Plan;
import com.library.membership.repository.PlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock  PlanRepository planRepository;
    @InjectMocks PlanService planService;

    private Plan buildPlan(String name, Plan.PlanType type) {
        return Plan.builder()
                .id(UUID.randomUUID())
                .name(name)
                .planType(type)
                .price(BigDecimal.valueOf(400))
                .durationDays(30)
                .description("Test plan")
                .isActive(true)
                .build();
    }

    @Test
    void getAllActivePlans_returnsMappedDtos() {
        Plan half = buildPlan("Half Day", Plan.PlanType.HALF_DAY);
        Plan full = buildPlan("Full Day", Plan.PlanType.FULL_DAY);
        when(planRepository.findByIsActiveTrue()).thenReturn(List.of(half, full));

        List<PlanDto> result = planService.getAllActivePlans();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PlanDto::getName)
                .containsExactlyInAnyOrder("Half Day", "Full Day");
    }

    @Test
    void getAllActivePlans_emptyRepository_returnsEmptyList() {
        when(planRepository.findByIsActiveTrue()).thenReturn(List.of());

        assertThat(planService.getAllActivePlans()).isEmpty();
    }

    @Test
    void getAllActivePlans_dtoFieldsPopulatedFromEntity() {
        Plan plan = buildPlan("Full Day Plan", Plan.PlanType.FULL_DAY);
        plan.setPrice(BigDecimal.valueOf(600));
        plan.setDurationDays(30);
        when(planRepository.findByIsActiveTrue()).thenReturn(List.of(plan));

        PlanDto dto = planService.getAllActivePlans().get(0);

        assertThat(dto.getId()).isEqualTo(plan.getId().toString());
        assertThat(dto.getName()).isEqualTo("Full Day Plan");
        assertThat(dto.getPlanType()).isEqualTo("FULL_DAY");
        assertThat(dto.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(dto.getDurationDays()).isEqualTo(30);
        assertThat(dto.isActive()).isTrue();
    }

    @Test
    void getAllActivePlans_callsRepository() {
        when(planRepository.findByIsActiveTrue()).thenReturn(List.of());
        planService.getAllActivePlans();
        verify(planRepository).findByIsActiveTrue();
    }
}
