package com.library.seat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "seat_bookings",
        uniqueConstraints = {
                // A seat can only be booked once per shift per start date
                @UniqueConstraint(columnNames = {"seat_id", "shift", "booking_date"})
        }
)
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SeatBooking {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    // MORNING | EVENING | FULL_DAY
    @Column(nullable = false)
    private String shift;

    // Membership start date
    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    // Membership end date
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum Status {
        ACTIVE, RELEASED, EXPIRED
    }
}