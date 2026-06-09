package com.library.seat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.library.seat.entity.Seat;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SeatDto {
    private String  id;
    private String  seatNumber;  // e.g. "A12"
    private String  rowLabel;    // A, B, C, D
    private Integer seatIndex;
    @JsonProperty("isBooked")
    private boolean isBooked;    // true if taken for the requested shift/date
    @JsonProperty("isActive")
    private boolean isActive;

    public static SeatDto fromEntity(Seat seat, boolean isBooked) {
        return SeatDto.builder()
                .id(seat.getId().toString())
                .seatNumber(seat.getSeatNumber())
                .rowLabel(seat.getRowLabel())
                .seatIndex(seat.getSeatIndex())
                .isBooked(isBooked)
                .isActive(Boolean.TRUE.equals(seat.getIsActive()))
                .build();
    }
}