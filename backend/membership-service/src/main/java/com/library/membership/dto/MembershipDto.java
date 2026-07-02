package com.library.membership.dto;

import com.library.membership.entity.Membership;
import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MembershipDto {
    private String id;
    private String userId;
    private String planId;
    private String planName;
    private String planType;     // HALF_DAY | FULL_DAY
    private String seatId;
    private String seatNumber;   // e.g. "B14"
    private String shift;        // MORNING | EVENING | FULL_DAY
    private String startDate;    // yyyy-MM-dd
    private String endDate;      // yyyy-MM-dd
    private String status;       // PENDING | ACTIVE | QUEUED | GRACE | EXPIRED | CANCELLED
    private String createdAt;
    private BigDecimal planPrice;
    private BigDecimal duesAmount; // non-null while status == GRACE

    public static MembershipDto fromEntity(Membership m) {
        return MembershipDto.builder()
                .id(m.getId().toString())
                .userId(m.getUserId().toString())
                .planId(m.getPlan().getId().toString())
                .planName(m.getPlan().getName())
                .planType(m.getPlan().getPlanType().name())
                .seatId(m.getSeatId() != null ? m.getSeatId().toString() : null)
                .seatNumber(m.getSeatNumber())
                .shift(m.getShift())
                .startDate(m.getStartDate()  != null ? m.getStartDate().toString()  : null)
                .endDate(m.getEndDate()      != null ? m.getEndDate().toString()    : null)
                .status(m.getStatus().name())
                .createdAt(m.getCreatedAt()  != null ? m.getCreatedAt().toString()  : null)
                .planPrice(m.getPlan().getPrice())
                .duesAmount(m.getDuesAmount())
                .build();
    }
}