package com.library.membership.config;

import com.library.membership.entity.Plan;
import com.library.membership.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlanDataInitializer implements ApplicationRunner {

    private final PlanRepository planRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (planRepository.count() > 0) {
            log.info("Plans already seeded, skipping.");
            return;
        }

        List<Plan> plans = List.of(
                Plan.builder()
                        .name("Half Day")
                        .planType(Plan.PlanType.HALF_DAY)
                        .price(new BigDecimal("800.00"))
                        .durationDays(30)
                        .description("Morning or Evening shift access for 30 days")
                        .isActive(true)
                        .build(),
                Plan.builder()
                        .name("Full Day")
                        .planType(Plan.PlanType.FULL_DAY)
                        .price(new BigDecimal("1200.00"))
                        .durationDays(30)
                        .description("Unrestricted all-day access for 30 days")
                        .isActive(true)
                        .build()
        );

        planRepository.saveAll(plans);
        log.info("Seeded {} membership plans", plans.size());
    }
}
