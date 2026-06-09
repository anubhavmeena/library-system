package com.library.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "memberships")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Membership {

    @Id
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

    @Column(name = "created_at") private LocalDateTime createdAt;

    public enum Status { PENDING, ACTIVE, EXPIRED, CANCELLED }
}