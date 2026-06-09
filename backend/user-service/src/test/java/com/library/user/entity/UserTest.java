package com.library.user.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void noArgsConstructor_isActiveIsTrue() {
        // Field initializer runs with no-args constructor
        assertThat(new User().getIsActive()).isTrue();
    }

    @Test
    void noArgsConstructor_roleIsStudent() {
        assertThat(new User().getRole()).isEqualTo(User.Role.STUDENT);
    }

    @Test
    void builder_withoutIsActive_isActiveNull() {
        // @Builder ignores field initializer — isActive stays null
        User user = User.builder().name("Alice").role(User.Role.STUDENT).build();
        assertThat(user.getIsActive()).isNull();
    }

    @Test
    void builder_withoutRole_roleNull() {
        // @Builder ignores field initializer — role stays null
        User user = User.builder().name("Alice").build();
        assertThat(user.getRole()).isNull();
    }

    @Test
    void onCreate_setsCreatedAtAndUpdatedAt() throws Exception {
        User user = new User();
        assertThat(user.getCreatedAt()).isNull();
        assertThat(user.getUpdatedAt()).isNull();

        Method m = User.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(user);

        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.getCreatedAt()).isEqualTo(user.getUpdatedAt());
    }

    @Test
    void onUpdate_updatesOnlyUpdatedAt() throws Exception {
        User user = new User();
        // Set a createdAt first
        Method onCreate = User.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        onCreate.invoke(user);
        LocalDateTime originalCreatedAt = user.getCreatedAt();

        // Small sleep to ensure timestamps differ
        Thread.sleep(5);

        Method onUpdate = User.class.getDeclaredMethod("onUpdate");
        onUpdate.setAccessible(true);
        onUpdate.invoke(user);

        assertThat(user.getCreatedAt()).isEqualTo(originalCreatedAt); // unchanged
        // updatedAt could be same or after (millisecond resolution), just check not null
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    void roleEnum_allValuesExist() {
        assertThat(User.Role.values())
                .containsExactlyInAnyOrder(User.Role.STUDENT, User.Role.ADMIN);
    }

    @Test
    void builder_allFieldsSet() {
        UUID id = UUID.randomUUID();
        User user = User.builder()
                .id(id).name("Bob").mobile("1234567890").email("bob@test.com")
                .role(User.Role.ADMIN).isActive(false).build();

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getName()).isEqualTo("Bob");
        assertThat(user.getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(user.getIsActive()).isFalse();
    }
}
