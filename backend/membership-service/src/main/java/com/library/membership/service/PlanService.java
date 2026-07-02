package com.library.membership.service;

import com.library.membership.dto.PlanDto;
import com.library.membership.entity.AppSettings;
import com.library.membership.repository.AppSettingsRepository;
import com.library.membership.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;
    private final AppSettingsRepository appSettingsRepository;

    /**
     * Returns all active membership plans.
     * Called by GET /api/plans — public endpoint, no auth needed.
     * Result: Half Day ₹400 and Full Day ₹600 (seeded in init.sql).
     */
    public List<PlanDto> getAllActivePlans() {
        BigDecimal convenienceFee = appSettingsRepository.findById(1L)
                .map(AppSettings::getConvenienceFee)
                .orElse(BigDecimal.ZERO);

        return planRepository.findByIsActiveTrue()
                .stream()
                .map(plan -> {
                    PlanDto dto = PlanDto.fromEntity(plan);
                    dto.setConvenienceFee(convenienceFee);
                    return dto;
                })
                .collect(Collectors.toList());
    }
}