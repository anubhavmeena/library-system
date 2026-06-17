package com.library.admin.dto;

import lombok.*;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExpenseDto {
    private int year;
    private int month;
    private int waterTankerQty;
    private BigDecimal waterTankerPrice;
    private BigDecimal electricityBill;
    private BigDecimal internetBill;
    private BigDecimal miscellaneous;
    private BigDecimal totalExpense;
}
