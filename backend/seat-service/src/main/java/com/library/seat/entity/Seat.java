package com.library.seat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import java.util.UUID;

@Entity
@Table(name = "seats")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    // e.g. "A1", "B14", "D26"
    @Column(name = "seat_number", unique = true, nullable = false)
    private String seatNumber;

    // A, B, C, or D
    @Column(name = "row_label", nullable = false)
    private String rowLabel;

    // position within the row (1-based)
    @Column(name = "seat_index", nullable = false)
    private Integer seatIndex;

    @Column(name = "is_active")
    private Boolean isActive = true;
}