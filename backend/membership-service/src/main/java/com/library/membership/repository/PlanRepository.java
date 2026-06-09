package com.library.membership.repository;

import com.library.membership.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {
    List<Plan> findByIsActiveTrue();
}