package com.library.admin.service;

import com.library.admin.dto.ExpenseDto;
import com.library.admin.dto.MiscItemDto;
import com.library.admin.dto.SaveExpenseRequest;
import com.library.admin.entity.MiscExpenseItem;
import com.library.admin.entity.MonthlyExpense;
import com.library.admin.repository.MiscExpenseItemRepository;
import com.library.admin.repository.MonthlyExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock MonthlyExpenseRepository expenseRepository;
    @Mock MiscExpenseItemRepository miscItemRepository;

    @InjectMocks ExpenseService expenseService;

    // ── getExpense ───────────────────────────────────────────────────────────

    @Test
    void getExpense_noRecordYet_returnsEmptyDto() {
        when(expenseRepository.findByYearAndMonth(2026, 3)).thenReturn(Optional.empty());

        ExpenseDto dto = expenseService.getExpense(2026, 3);

        assertThat(dto.getYear()).isEqualTo(2026);
        assertThat(dto.getMonth()).isEqualTo(3);
        assertThat(dto.getTotalExpense()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getMiscItems()).isEmpty();
    }

    @Test
    void getExpense_existingRecordWithMiscItems_usesItemsNotLegacyField() {
        UUID expenseId = UUID.randomUUID();
        MonthlyExpense expense = MonthlyExpense.builder()
                .id(expenseId).year(2026).month(3)
                .waterTankerQty(2).waterTankerPrice(new BigDecimal("100.00"))
                .electricityBill(new BigDecimal("500.00")).internetBill(new BigDecimal("200.00"))
                .miscellaneous(new BigDecimal("999.00")) // stale legacy total — should be ignored
                .build();
        when(expenseRepository.findByYearAndMonth(2026, 3)).thenReturn(Optional.of(expense));
        when(miscItemRepository.findByMonthlyExpenseIdOrderBySortOrder(expenseId)).thenReturn(List.of(
                MiscExpenseItem.builder().description("Cleaning").amount(new BigDecimal("50.00")).sortOrder(0).build(),
                MiscExpenseItem.builder().description("Repairs").amount(new BigDecimal("150.00")).sortOrder(1).build()
        ));

        ExpenseDto dto = expenseService.getExpense(2026, 3);

        assertThat(dto.getMiscItems()).extracting(MiscItemDto::getDescription).containsExactly("Cleaning", "Repairs");
        assertThat(dto.getMiscellaneous()).isEqualByComparingTo("200.00"); // recomputed from items, not the stale 999
        // total = water(2*100=200) + electricity(500) + internet(200) + misc(200) = 1100
        assertThat(dto.getTotalExpense()).isEqualByComparingTo("1100.00");
    }

    @Test
    void getExpense_legacyRecordWithNoItemsButNonZeroMiscellaneous_migratesToSingleGeneralItem() {
        UUID expenseId = UUID.randomUUID();
        MonthlyExpense expense = MonthlyExpense.builder()
                .id(expenseId).year(2025).month(12)
                .waterTankerQty(0).waterTankerPrice(BigDecimal.ZERO)
                .electricityBill(BigDecimal.ZERO).internetBill(BigDecimal.ZERO)
                .miscellaneous(new BigDecimal("300.00"))
                .build();
        when(expenseRepository.findByYearAndMonth(2025, 12)).thenReturn(Optional.of(expense));
        when(miscItemRepository.findByMonthlyExpenseIdOrderBySortOrder(expenseId)).thenReturn(List.of());

        ExpenseDto dto = expenseService.getExpense(2025, 12);

        assertThat(dto.getMiscItems()).hasSize(1);
        assertThat(dto.getMiscItems().get(0).getDescription()).isEqualTo("General");
        assertThat(dto.getMiscItems().get(0).getAmount()).isEqualByComparingTo("300.00");
    }

    // ── saveExpense ──────────────────────────────────────────────────────────

    @Test
    void saveExpense_newMonth_createsRecordAndSavesMiscItems() {
        when(expenseRepository.findByYearAndMonth(2026, 4)).thenReturn(Optional.empty());
        when(expenseRepository.save(any())).thenAnswer(inv -> {
            MonthlyExpense e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        SaveExpenseRequest req = new SaveExpenseRequest();
        req.setYear(2026); req.setMonth(4);
        req.setWaterTankerQty(3);
        req.setWaterTankerPrice(new BigDecimal("100.00"));
        req.setElectricityBill(new BigDecimal("600.00"));
        req.setInternetBill(new BigDecimal("300.00"));
        req.setMiscItems(List.of(
                new MiscItemDto("Snacks", new BigDecimal("50.00")),
                new MiscItemDto("Stationery", new BigDecimal("25.00"))
        ));

        ExpenseDto dto = expenseService.saveExpense(req);

        // water(3*100=300) + electricity(600) + internet(300) + misc(75) = 1275
        assertThat(dto.getTotalExpense()).isEqualByComparingTo("1275.00");
        assertThat(dto.getMiscellaneous()).isEqualByComparingTo("75.00");

        ArgumentCaptor<List<MiscExpenseItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(miscItemRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).hasSize(2);
        assertThat(itemsCaptor.getValue().get(0).getSortOrder()).isZero();
        assertThat(itemsCaptor.getValue().get(1).getSortOrder()).isEqualTo(1);
    }

    @Test
    void saveExpense_existingMonth_updatesInPlaceRatherThanCreatingSecondRow() {
        UUID expenseId = UUID.randomUUID();
        MonthlyExpense existing = MonthlyExpense.builder()
                .id(expenseId).year(2026).month(5)
                .waterTankerQty(1).waterTankerPrice(BigDecimal.ZERO)
                .electricityBill(BigDecimal.ZERO).internetBill(BigDecimal.ZERO)
                .miscellaneous(BigDecimal.ZERO)
                .build();
        when(expenseRepository.findByYearAndMonth(2026, 5)).thenReturn(Optional.of(existing));
        when(expenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SaveExpenseRequest req = new SaveExpenseRequest();
        req.setYear(2026); req.setMonth(5);
        req.setWaterTankerQty(5);
        req.setWaterTankerPrice(new BigDecimal("120.00"));
        req.setElectricityBill(new BigDecimal("400.00"));
        req.setInternetBill(new BigDecimal("100.00"));
        req.setMiscItems(List.of());

        expenseService.saveExpense(req);

        ArgumentCaptor<MonthlyExpense> captor = ArgumentCaptor.forClass(MonthlyExpense.class);
        verify(expenseRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(expenseId); // same row, not a new one
        assertThat(captor.getValue().getWaterTankerQty()).isEqualTo(5);
        verify(miscItemRepository).deleteByMonthlyExpenseId(expenseId);
    }

    @Test
    void saveExpense_blankDescriptionItems_areSkipped() {
        when(expenseRepository.findByYearAndMonth(2026, 6)).thenReturn(Optional.empty());
        when(expenseRepository.save(any())).thenAnswer(inv -> {
            MonthlyExpense e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        SaveExpenseRequest req = new SaveExpenseRequest();
        req.setYear(2026); req.setMonth(6);
        req.setWaterTankerQty(0);
        req.setWaterTankerPrice(BigDecimal.ZERO);
        req.setElectricityBill(BigDecimal.ZERO);
        req.setInternetBill(BigDecimal.ZERO);
        req.setMiscItems(List.of(
                new MiscItemDto("", new BigDecimal("50.00")),
                new MiscItemDto(null, new BigDecimal("25.00")),
                new MiscItemDto("Valid Item", new BigDecimal("10.00"))
        ));

        expenseService.saveExpense(req);

        ArgumentCaptor<List<MiscExpenseItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(miscItemRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).hasSize(1);
        assertThat(itemsCaptor.getValue().get(0).getDescription()).isEqualTo("Valid Item");
    }

    @Test
    void saveExpense_nullBillsAndNullMiscItems_treatedAsZero() {
        when(expenseRepository.findByYearAndMonth(2026, 7)).thenReturn(Optional.empty());
        when(expenseRepository.save(any())).thenAnswer(inv -> {
            MonthlyExpense e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        SaveExpenseRequest req = new SaveExpenseRequest();
        req.setYear(2026); req.setMonth(7);
        req.setWaterTankerQty(0);
        req.setWaterTankerPrice(null);
        req.setElectricityBill(null);
        req.setInternetBill(null);
        req.setMiscItems(null);

        ExpenseDto dto = expenseService.saveExpense(req);

        assertThat(dto.getTotalExpense()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(miscItemRepository).saveAll(anyList());
    }
}
