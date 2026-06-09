package com.library.admin.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RevenueReportDto {
    private String     fromDate;
    private String     toDate;
    private BigDecimal totalRevenue;
    private long       totalTransactions;
    private BigDecimal halfDayRevenue;    // breakdown by plan type (future use)
    private BigDecimal fullDayRevenue;
    private List<DailyRevenueDto> dailyBreakdown;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyRevenueDto {
        private String     date;    // yyyy-MM-dd
        private BigDecimal amount;
        private long       count;   // number of successful transactions that day
    }
}