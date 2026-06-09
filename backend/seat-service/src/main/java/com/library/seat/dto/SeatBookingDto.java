package com.library.seat.dto;

import com.library.seat.entity.SeatBooking;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SeatBookingDto {
    private String id;
    private String seatId;
    private String seatNumber;
    private String rowLabel;
    private String userId;
    private String membershipId;
    private String shift;
    private String bookingDate;
    private String endDate;
    private String status;

    public static SeatBookingDto fromEntity(SeatBooking booking) {
        return SeatBookingDto.builder()
                .id(booking.getId().toString())
                .seatId(booking.getSeat().getId().toString())
                .seatNumber(booking.getSeat().getSeatNumber())
                .rowLabel(booking.getSeat().getRowLabel())
                .userId(booking.getUserId().toString())
                .membershipId(booking.getMembershipId().toString())
                .shift(booking.getShift())
                .bookingDate(booking.getBookingDate().toString())
                .endDate(booking.getEndDate().toString())
                .status(booking.getStatus().name())
                .build();
    }
}