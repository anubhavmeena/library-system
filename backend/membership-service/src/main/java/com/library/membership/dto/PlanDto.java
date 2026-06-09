package com.library.membership.dto;

import com.library.membership.entity.Plan;
import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PlanDto {
    private String     id;
    private String     name;
    private String     planType;     // HALF_DAY | FULL_DAY
    private BigDecimal price;
    private Integer    durationDays;
    private String     description;
    private boolean    isActive;

    public static PlanDto fromEntity(Plan plan) {
        return PlanDto.builder()
                .id(plan.getId().toString())
                .name(plan.getName())
                .planType(plan.getPlanType().name())
                .price(plan.getPrice())
                .durationDays(plan.getDurationDays())
                .description(plan.getDescription())
                .isActive(Boolean.TRUE.equals(plan.getIsActive()))
                .build();
    }
}