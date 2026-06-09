package com.library.user.dto;

import com.library.user.entity.User;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserDto {
    private String id;
    private String name;
    private String fatherName;
    private String mobile;
    private String email;
    private String address;
    private String photoUrl;
    private String dateOfBirth;
    private String gender;
    private String role;
    private boolean isActive;
    private String createdAt;

    public static UserDto fromEntity(User user) {
        return UserDto.builder()
                .id(user.getId().toString())
                .name(user.getName())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .fatherName(user.getFatherName())
                .address(user.getAddress())
                .photoUrl(user.getPhotoUrl())
                .dateOfBirth(user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null)
                .gender(user.getGender())
                .role(user.getRole().name())
                .isActive(Boolean.TRUE.equals(user.getIsActive()))
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                .build();
    }
}