package com.library.admin.dto;

import lombok.*;

import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor
public class SaveExpenseRequest {
    private int year;
    private int month;
    private int waterTankerQty;
    private BigDecimal waterTankerPrice;
    private BigDecimal electricityBill;
    private BigDecimal internetBill;
    private BigDecimal miscellaneous;
}
