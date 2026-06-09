package com.library.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class User {

    // No @GeneratedValue — admin-service reads existing rows, never inserts users
    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(unique = true) private String mobile;
    @Column(unique = true) private String email;
    @Column(nullable = false) private String name;

    private String address;
    @Column(name = "photo_url")     private String    photoUrl;
    @Column(name = "date_of_birth") private LocalDate dateOfBirth;
    private String gender;

    @Column(name = "is_active") private Boolean isActive = true;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role = Role.STUDENT;

    @Column(name = "created_at") private LocalDateTime createdAt;

    public enum Role { STUDENT, ADMIN }
}