package com.library.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "memberships")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Membership {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false) private UUID   userId;
    @Column(name = "plan_id", nullable = false) private UUID   planId;
    @Column(name = "seat_id")                   private UUID   seatId;
    @Column(name = "seat_number")               private String seatNumber;

    // MORNING | EVENING | FULL_DAY
    private String shift;

    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date",   nullable = false) private LocalDate endDate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    @Column(name = "reminder_sent")
    private boolean reminderSent = false;

    // Set when a membership enters GRACE (endDate passed, seat held, dues owed).
    // Null for memberships that have never been in grace.
    @Column(name = "dues_amount", precision = 10, scale = 2) private BigDecimal duesAmount;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public enum Status { PENDING, ACTIVE, QUEUED, GRACE, EXPIRED, CANCELLED }
}