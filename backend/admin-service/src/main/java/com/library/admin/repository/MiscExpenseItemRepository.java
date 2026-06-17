package com.library.admin.repository;

import com.library.admin.entity.MiscExpenseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface MiscExpenseItemRepository extends JpaRepository<MiscExpenseItem, UUID> {

    List<MiscExpenseItem> findByMonthlyExpenseIdOrderBySortOrder(UUID monthlyExpenseId);

    @Modifying
    @Transactional
    @Query("DELETE FROM MiscExpenseItem m WHERE m.monthlyExpenseId = :id")
    void deleteByMonthlyExpenseId(@Param("id") UUID id);
}
