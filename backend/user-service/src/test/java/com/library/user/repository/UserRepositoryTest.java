package com.library.user.repository;

import com.library.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    UserRepository userRepository;

    private User.UserBuilder base(String name, String mobile) {
        return User.builder()
                .name(name)
                .mobile(mobile)
                .role(User.Role.STUDENT)
                .isActive(true);
    }

    // ── findByMobile ──────────────────────────────────────────────────────────

    @Test
    void findByMobile_found() {
        userRepository.save(base("Alice", "9876543210").build());
        assertThat(userRepository.findByMobile("9876543210")).isPresent();
    }

    @Test
    void findByMobile_notFound() {
        assertThat(userRepository.findByMobile("0000000000")).isEmpty();
    }

    @Test
    void findByMobile_returnsCorrectUser() {
        userRepository.save(base("Alice", "1111111111").build());
        userRepository.save(base("Bob", "2222222222").build());

        assertThat(userRepository.findByMobile("2222222222"))
                .hasValueSatisfying(u -> assertThat(u.getName()).isEqualTo("Bob"));
    }

    // ── findByEmail ───────────────────────────────────────────────────────────

    @Test
    void findByEmail_found() {
        userRepository.save(base("Alice", "9876543210").email("alice@test.com").build());
        assertThat(userRepository.findByEmail("alice@test.com")).isPresent();
    }

    @Test
    void findByEmail_notFound() {
        assertThat(userRepository.findByEmail("nobody@test.com")).isEmpty();
    }

    // ── existsByMobile / existsByEmail ────────────────────────────────────────

    @Test
    void existsByMobile_true() {
        userRepository.save(base("Alice", "9876543210").build());
        assertThat(userRepository.existsByMobile("9876543210")).isTrue();
    }

    @Test
    void existsByMobile_false() {
        assertThat(userRepository.existsByMobile("9999999999")).isFalse();
    }

    @Test
    void existsByEmail_true() {
        userRepository.save(base("Alice", "9876543210").email("a@test.com").build());
        assertThat(userRepository.existsByEmail("a@test.com")).isTrue();
    }

    @Test
    void existsByEmail_false() {
        assertThat(userRepository.existsByEmail("none@test.com")).isFalse();
    }

    // ── unique constraints ────────────────────────────────────────────────────

    @Test
    void save_duplicateMobile_throwsDataIntegrityViolation() {
        userRepository.saveAndFlush(base("Alice", "9876543210").build());

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(base("Bob", "9876543210").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_duplicateEmail_throwsDataIntegrityViolation() {
        userRepository.saveAndFlush(base("Alice", "1111111111").email("shared@test.com").build());

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(base("Bob", "2222222222").email("shared@test.com").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── @PrePersist ───────────────────────────────────────────────────────────

    @Test
    void save_prePersistSetsCreatedAtAndUpdatedAt() {
        User saved = userRepository.save(base("Alice", "9876543210").build());

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void save_generatesId() {
        User saved = userRepository.save(base("Alice", "9876543210").build());
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void findById_found() {
        User saved = userRepository.save(base("Alice", "9876543210").build());
        assertThat(userRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void findById_notFound() {
        assertThat(userRepository.findById(UUID.randomUUID())).isEmpty();
    }
}
