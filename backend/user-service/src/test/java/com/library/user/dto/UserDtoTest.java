package com.library.user.dto;

import com.library.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserDtoTest {

    private User buildUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .name("Alice")
                .mobile("9876543210")
                .email("alice@test.com")
                .address("123 Main St")
                .photoUrl("/uploads/photos/alice.jpg")
                .dateOfBirth(LocalDate.of(1995, 6, 15))
                .gender("Female")
                .role(User.Role.STUDENT)
                .isActive(true)
                .createdAt(LocalDateTime.of(2025, 1, 1, 10, 0))
                .build();
    }

    @Test
    void fromEntity_allFieldsMapped() {
        User user = buildUser();
        UserDto dto = UserDto.fromEntity(user);

        assertThat(dto.getId()).isEqualTo(user.getId().toString());
        assertThat(dto.getName()).isEqualTo("Alice");
        assertThat(dto.getMobile()).isEqualTo("9876543210");
        assertThat(dto.getEmail()).isEqualTo("alice@test.com");
        assertThat(dto.getAddress()).isEqualTo("123 Main St");
        assertThat(dto.getPhotoUrl()).isEqualTo("/uploads/photos/alice.jpg");
        assertThat(dto.getDateOfBirth()).isEqualTo("1995-06-15");
        assertThat(dto.getGender()).isEqualTo("Female");
        assertThat(dto.getRole()).isEqualTo("STUDENT");
        assertThat(dto.isActive()).isTrue();
        assertThat(dto.getCreatedAt()).isNotNull();
    }

    @Test
    void fromEntity_dateOfBirthNull_dtoDateOfBirthNull() {
        User user = buildUser();
        user.setDateOfBirth(null);

        assertThat(UserDto.fromEntity(user).getDateOfBirth()).isNull();
    }

    @Test
    void fromEntity_createdAtNull_dtoCreatedAtNull() {
        User user = buildUser();
        user.setCreatedAt(null);

        assertThat(UserDto.fromEntity(user).getCreatedAt()).isNull();
    }

    @Test
    void fromEntity_isActiveNull_isActiveFalse() {
        // Boolean.TRUE.equals(null) = false
        User user = buildUser();
        user.setIsActive(null);

        assertThat(UserDto.fromEntity(user).isActive()).isFalse();
    }

    @Test
    void fromEntity_isActiveFalse_isActiveFalse() {
        User user = buildUser();
        user.setIsActive(false);

        assertThat(UserDto.fromEntity(user).isActive()).isFalse();
    }

    @Test
    void fromEntity_adminRole_mappedCorrectly() {
        User user = buildUser();
        user.setRole(User.Role.ADMIN);

        assertThat(UserDto.fromEntity(user).getRole()).isEqualTo("ADMIN");
    }

    @Test
    void fromEntity_photoUrlNull_dtoPhotoUrlNull() {
        User user = buildUser();
        user.setPhotoUrl(null);

        assertThat(UserDto.fromEntity(user).getPhotoUrl()).isNull();
    }
}
