package com.library.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "seats")
@Data @NoArgsConstructor @AllArgsConstructor
public class Seat {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "seat_number", unique = true, nullable = false)
    private String seatNumber;

    @Column(name = "row_label", nullable = false)
    private String rowLabel;

    @Column(name = "seat_index", nullable = false)
    private Integer seatIndex;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
