package com.library.admin.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class SaveExpenseRequest {
    private int year;
    private int month;
    private int waterTankerQty;
    private BigDecimal waterTankerPrice;
    private BigDecimal electricityBill;
    private BigDecimal internetBill;
    private List<MiscItemDto> miscItems;
}
