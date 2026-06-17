package com.library.admin.repository;

import com.library.admin.entity.MonthlyExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MonthlyExpenseRepository extends JpaRepository<MonthlyExpense, UUID> {
    Optional<MonthlyExpense> findByYearAndMonth(int year, int month);
}
