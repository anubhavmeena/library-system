package com.library.admin.service;

import com.library.admin.dto.ExpenseDto;
import com.library.admin.dto.SaveExpenseRequest;
import com.library.admin.entity.MonthlyExpense;
import com.library.admin.repository.MonthlyExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final MonthlyExpenseRepository expenseRepository;

    public ExpenseDto getExpense(int year, int month) {
        return expenseRepository.findByYearAndMonth(year, month)
                .map(this::toDto)
                .orElseGet(() -> emptyDto(year, month));
    }

    public ExpenseDto saveExpense(SaveExpenseRequest req) {
        BigDecimal tankerPrice = nullSafe(req.getWaterTankerPrice());
        BigDecimal electricity = nullSafe(req.getElectricityBill());
        BigDecimal internet    = nullSafe(req.getInternetBill());
        BigDecimal misc        = nullSafe(req.getMiscellaneous());

        MonthlyExpense expense = expenseRepository
                .findByYearAndMonth(req.getYear(), req.getMonth())
                .orElseGet(MonthlyExpense::new);

        expense.setYear(req.getYear());
        expense.setMonth(req.getMonth());
        expense.setWaterTankerQty(req.getWaterTankerQty());
        expense.setWaterTankerPrice(tankerPrice);
        expense.setElectricityBill(electricity);
        expense.setInternetBill(internet);
        expense.setMiscellaneous(misc);

        return toDto(expenseRepository.save(expense));
    }

    private ExpenseDto toDto(MonthlyExpense e) {
        BigDecimal waterCost = nullSafe(e.getWaterTankerPrice())
                .multiply(BigDecimal.valueOf(e.getWaterTankerQty()));
        BigDecimal total = waterCost
                .add(nullSafe(e.getElectricityBill()))
                .add(nullSafe(e.getInternetBill()))
                .add(nullSafe(e.getMiscellaneous()));

        return ExpenseDto.builder()
                .year(e.getYear())
                .month(e.getMonth())
                .waterTankerQty(e.getWaterTankerQty())
                .waterTankerPrice(nullSafe(e.getWaterTankerPrice()))
                .electricityBill(nullSafe(e.getElectricityBill()))
                .internetBill(nullSafe(e.getInternetBill()))
                .miscellaneous(nullSafe(e.getMiscellaneous()))
                .totalExpense(total)
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
                .build();
    }

    private BigDecimal nullSafe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
