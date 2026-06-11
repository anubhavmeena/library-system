package com.library.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "seat_bookings")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SeatBooking {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(nullable = false)
    private String shift;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public enum Status { ACTIVE, RELEASED, EXPIRED }
}
