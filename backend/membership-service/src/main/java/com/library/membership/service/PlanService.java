package com.library.membership.service;

import com.library.membership.dto.PlanDto;
import com.library.membership.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;

    /**
     * Returns all active membership plans.
     * Called by GET /api/plans — public endpoint, no auth needed.
     * Result: Half Day ₹400 and Full Day ₹600 (seeded in init.sql).
     */
    public List<PlanDto> getAllActivePlans() {
        return planRepository.findByIsActiveTrue()
                .stream()
                .map(PlanDto::fromEntity)
                .collect(Collectors.toList());
    }
}