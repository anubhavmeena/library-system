package com.library.admin.repository;

import com.library.admin.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

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

    // ── findStudentsByStatus ─────────────────────────────────────────────────

    @Test
    void findStudentsByStatus_nullStatus_returnsAllStudents() {
        Page<User> page = userRepository.findStudentsByStatus(null, PageRequest.of(0, 10));

        assertThat(page.getContent())
                .hasSize(2)
                .extracting(User::getName)
                .containsExactlyInAnyOrder("Active Student", "Inactive Student");
    }

    @Test
    void findStudentsByStatus_active_returnsOnlyActiveStudents() {
        Page<User> page = userRepository.findStudentsByStatus("ACTIVE", PageRequest.of(0, 10));

        assertThat(page.getContent())
                .hasSize(1)
                .first()
                .extracting(User::getName).isEqualTo("Active Student");
    }

    @Test
    void findStudentsByStatus_inactive_returnsOnlyInactiveStudents() {
        Page<User> page = userRepository.findStudentsByStatus("INACTIVE", PageRequest.of(0, 10));

        assertThat(page.getContent())
                .hasSize(1)
                .first()
                .extracting(User::getName).isEqualTo("Inactive Student");
    }

    @Test
    void findStudentsByStatus_doesNotReturnAdminUsers() {
        Page<User> page = userRepository.findStudentsByStatus(null, PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(User::getName)
                .doesNotContain("Admin User");
    }

    @Test
    void findStudentsByStatus_pagination_respectsPageSize() {
        Page<User> page = userRepository.findStudentsByStatus(null, PageRequest.of(0, 1));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findStudentsByStatus_emptyTable_returnsEmptyPage() {
        userRepository.deleteAll();
        Page<User> page = userRepository.findStudentsByStatus(null, PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
    }

    // ── countActiveStudents ──────────────────────────────────────────────────

    @Test
    void countActiveStudents_returnsOnlyActiveStudents() {
        assertThat(userRepository.countActiveStudents()).isEqualTo(1L);
    }

    @Test
    void countActiveStudents_excludesAdmin() {
        // adminUser is active but role=ADMIN — should NOT be counted
        assertThat(userRepository.countActiveStudents()).isEqualTo(1L);
    }

    @Test
    void countActiveStudents_zeroWhenNoneActive() {
        userRepository.deleteAll();
        assertThat(userRepository.countActiveStudents()).isZero();
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
