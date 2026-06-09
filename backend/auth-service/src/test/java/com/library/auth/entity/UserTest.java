package com.library.auth.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class UserTest {

    @Test
    void roleEnum_containsStudentAndAdmin() {
        assertThat(User.Role.values())
                .containsExactlyInAnyOrder(User.Role.STUDENT, User.Role.ADMIN);
    }

    @Test
    void onCreate_setsCreatedAtAndUpdatedAt() throws Exception {
        User user = new User();
        invokeLifecycle(user, "onCreate");

        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    void onUpdate_setsUpdatedAt() throws Exception {
        User user = new User();
        invokeLifecycle(user, "onUpdate");

        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    void onCreate_createdAtAndUpdatedAtAreClose() throws Exception {
        User user = new User();
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        invokeLifecycle(user, "onCreate");
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertThat(user.getCreatedAt()).isBetween(before, after);
        assertThat(user.getUpdatedAt()).isBetween(before, after);
    }

    @Test
    void builderWithoutDefaults_roleAndIsActiveAreNull() {
        // Documents that @Builder ignores field initializers without @Builder.Default.
        // AuthService explicitly sets role and isActive — this test documents the gotcha.
        User user = User.builder().name("Alice").build();

        assertThat(user.getRole()).isNull();
        assertThat(user.getIsActive()).isNull();
    }

    @Test
    void builderWithExplicitValues_roleAndIsActiveSet() {
        User user = User.builder()
                .name("Alice")
                .role(User.Role.STUDENT)
                .isActive(true)
                .email("alice@example.com")
                .mobile("9876543210")
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .build();

        assertThat(user.getRole()).isEqualTo(User.Role.STUDENT);
        assertThat(user.getIsActive()).isTrue();
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getMobile()).isEqualTo("9876543210");
        assertThat(user.getDateOfBirth()).isEqualTo(LocalDate.of(2000, 1, 1));
    }

    @Test
    void noArgsConstructor_fieldInitializersAreApplied() {
        // @NoArgsConstructor runs field initializers; role and isActive get their defaults.
        // Only @Builder skips field initializers without @Builder.Default.
        User user = new User();

        assertThat(user.getId()).isNull();
        assertThat(user.getName()).isNull();
        assertThat(user.getRole()).isEqualTo(User.Role.STUDENT);
        assertThat(user.getIsActive()).isTrue();
    }

    private void invokeLifecycle(User user, String methodName) throws Exception {
        Method method = User.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(user);
    }
}
