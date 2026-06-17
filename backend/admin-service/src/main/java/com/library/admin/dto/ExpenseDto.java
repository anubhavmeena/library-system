package com.library.admin.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExpenseDto {
    private int year;
    private int month;
    private int waterTankerQty;
    private BigDecimal waterTankerPrice;
    private BigDecimal electricityBill;
    private BigDecimal internetBill;
    private BigDecimal miscellaneous;   // sum of miscItems amounts
    private BigDecimal totalExpense;
    private List<MiscItemDto> miscItems;
}
