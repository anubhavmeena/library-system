package com.library.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(unique = true) private String mobile;
    @Column(unique = true) private String email;
    @Column(nullable = false) private String name;

    private String address;

    @Column(name = "father_name") private String fatherName;

    @Column(name = "photo_url")    private String photoUrl;
    @Column(name = "aadhaar_url")  private String aadhaarUrl;
    @Column(name = "date_of_birth") private LocalDate dateOfBirth;

    private String gender;

    @Column(name = "is_active")     private Boolean isActive = true;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role = Role.STUDENT;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at")                    private LocalDateTime updatedAt;

    @PrePersist  protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate   protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum Role { STUDENT, ADMIN }
}