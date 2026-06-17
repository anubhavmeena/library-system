package com.library.admin.service;

import com.library.admin.dto.ExpenseDto;
import com.library.admin.dto.MiscItemDto;
import com.library.admin.dto.SaveExpenseRequest;
import com.library.admin.entity.MiscExpenseItem;
import com.library.admin.entity.MonthlyExpense;
import com.library.admin.repository.MiscExpenseItemRepository;
import com.library.admin.repository.MonthlyExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final MonthlyExpenseRepository     expenseRepository;
    private final MiscExpenseItemRepository    miscItemRepository;

    public ExpenseDto getExpense(int year, int month) {
        return expenseRepository.findByYearAndMonth(year, month)
                .map(this::toDto)
                .orElseGet(() -> emptyDto(year, month));
    }

    @Transactional
    public ExpenseDto saveExpense(SaveExpenseRequest req) {
        BigDecimal electricity = nullSafe(req.getElectricityBill());
        BigDecimal internet    = nullSafe(req.getInternetBill());
        BigDecimal tankerPrice = nullSafe(req.getWaterTankerPrice());

        // Upsert parent
        MonthlyExpense expense = expenseRepository
                .findByYearAndMonth(req.getYear(), req.getMonth())
                .orElseGet(MonthlyExpense::new);

        expense.setYear(req.getYear());
        expense.setMonth(req.getMonth());
        expense.setWaterTankerQty(req.getWaterTankerQty());
        expense.setWaterTankerPrice(tankerPrice);
        expense.setElectricityBill(electricity);
        expense.setInternetBill(internet);

        // Compute misc total from items
        List<MiscItemDto> items = req.getMiscItems() != null ? req.getMiscItems() : List.of();
        BigDecimal miscTotal = items.stream()
                .map(it -> nullSafe(it.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        expense.setMiscellaneous(miscTotal);

        expense = expenseRepository.save(expense);

        // Replace misc items
        if (expense.getId() != null) {
            miscItemRepository.deleteByMonthlyExpenseId(expense.getId());
        }
        List<MiscExpenseItem> toSave = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            MiscItemDto dto = items.get(i);
            if (dto.getDescription() != null && !dto.getDescription().isBlank()) {
                toSave.add(MiscExpenseItem.builder()
                        .monthlyExpenseId(expense.getId())
                        .description(dto.getDescription().trim())
                        .amount(nullSafe(dto.getAmount()))
                        .sortOrder(i)
                        .build());
            }
        }
        miscItemRepository.saveAll(toSave);

        return toDto(expense, toSave.stream()
                .map(m -> new MiscItemDto(m.getDescription(), m.getAmount()))
                .toList());
    }

    private ExpenseDto toDto(MonthlyExpense e) {
        List<MiscExpenseItem> items = e.getId() != null
                ? miscItemRepository.findByMonthlyExpenseIdOrderBySortOrder(e.getId())
                : List.of();

        // Migration: old record has miscellaneous > 0 but no items yet
        List<MiscItemDto> miscDtos;
        if (items.isEmpty() && nullSafe(e.getMiscellaneous()).compareTo(BigDecimal.ZERO) > 0) {
            miscDtos = List.of(new MiscItemDto("General", e.getMiscellaneous()));
        } else {
            miscDtos = items.stream()
                    .map(m -> new MiscItemDto(m.getDescription(), m.getAmount()))
                    .toList();
        }

        return toDto(e, miscDtos);
    }

    private ExpenseDto toDto(MonthlyExpense e, List<MiscItemDto> miscDtos) {
        BigDecimal waterCost = nullSafe(e.getWaterTankerPrice())
                .multiply(BigDecimal.valueOf(e.getWaterTankerQty()));
        BigDecimal miscTotal = miscDtos.stream()
                .map(it -> nullSafe(it.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = waterCost
                .add(nullSafe(e.getElectricityBill()))
                .add(nullSafe(e.getInternetBill()))
                .add(miscTotal);

        return ExpenseDto.builder()
                .year(e.getYear())
                .month(e.getMonth())
                .waterTankerQty(e.getWaterTankerQty())
                .waterTankerPrice(nullSafe(e.getWaterTankerPrice()))
                .electricityBill(nullSafe(e.getElectricityBill()))
                .internetBill(nullSafe(e.getInternetBill()))
                .miscellaneous(miscTotal)
                .totalExpense(total)
                .miscItems(miscDtos)
                .build();
    }

    private ExpenseDto emptyDto(int year, int month) {
        return ExpenseDto.builder()
                .year(year).month(month)
                .waterTankerQty(0)
                .waterTankerPrice(BigDecimal.ZERO)
                .electricityBill(BigDecimal.ZERO)
                .internetBill(BigDecimal.ZERO)
                .miscellaneous(BigDecimal.ZERO)
                .totalExpense(BigDecimal.ZERO)
                .miscItems(List.of())
                .build();
    }

    private BigDecimal nullSafe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
