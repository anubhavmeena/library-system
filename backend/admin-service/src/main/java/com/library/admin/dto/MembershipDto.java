package com.library.admin.dto;

import com.library.admin.entity.Membership;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MembershipDto {

    private String id;
    private String userId;
    private String planId;
    private String planName;
    private String seatId;
    private String seatNumber;
    private String shift;
    private String startDate;
    private String endDate;
    private String status;

    public static MembershipDto fromEntity(Membership m, String planName) {
        return MembershipDto.builder()
                .id(m.getId().toString())
                .userId(m.getUserId().toString())
                .planId(m.getPlanId().toString())
                .planName(planName)
                .seatId(m.getSeatId() != null ? m.getSeatId().toString() : null)
                .seatNumber(m.getSeatNumber())
                .shift(m.getShift())
                .startDate(m.getStartDate().toString())
                .endDate(m.getEndDate().toString())
                .status(m.getStatus().name())
                .build();
    }
}
