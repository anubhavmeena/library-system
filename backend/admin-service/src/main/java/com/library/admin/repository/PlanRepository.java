package com.library.admin.repository;

import com.library.admin.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {
    // Used by AdminService when plan name needs to be resolved
    // (currently the service joins via Membership.planId if needed)
}