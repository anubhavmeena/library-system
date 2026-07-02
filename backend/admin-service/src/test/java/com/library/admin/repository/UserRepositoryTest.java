package com.library.admin.repository;

import com.library.admin.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    UserRepository userRepository;

    private User activeStudent;
    private User inactiveStudent;
    private User adminUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        activeStudent = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .name("Active Student")
                .email("active@test.com")
                .mobile("1000000001")
                .role(User.Role.STUDENT)
                .isActive(true)
                .build());

        inactiveStudent = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .name("Inactive Student")
                .email("inactive@test.com")
                .mobile("1000000002")
                .role(User.Role.STUDENT)
                .isActive(false)
                .build());

        adminUser = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .name("Admin User")
                .email("admin@test.com")
                .mobile("1000000003")
                .role(User.Role.ADMIN)
                .isActive(true)
                .build());
    }

    // ── countAllStudents ─────────────────────────────────────────────────────

    @Test
    void countAllStudents_includesActiveAndInactive() {
        assertThat(userRepository.countAllStudents()).isEqualTo(2L);
    }

    @Test
    void countAllStudents_excludesAdminRole() {
        assertThat(userRepository.countAllStudents()).isEqualTo(2L); // not 3
    }

    @Test
    void countAllStudents_zeroWhenNoStudents() {
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .name("Admin Only")
                .email("adminonly@test.com")
                .mobile("9000000001")
                .role(User.Role.ADMIN)
                .isActive(true)
                .build());
        assertThat(userRepository.countAllStudents()).isZero();
    }
}
