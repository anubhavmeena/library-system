package com.library.notification.repository;

import com.library.notification.entity.NotificationLog;
import com.library.notification.enums.Channel;
import com.library.notification.enums.DeliveryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class NotificationLogRepositoryTest {

    @Autowired
    NotificationLogRepository repository;

    private NotificationLog.NotificationLogBuilder base() {
        return NotificationLog.builder()
                .recipient("user@test.com")
                .message("Test message")
                .channel(Channel.EMAIL)
                .status(DeliveryStatus.SENT)
                .event("TEST_EVENT");
    }

    // ── findByUserIdOrderByCreatedAtDesc ──────────────────────────────────────

    @Test
    void findByUserId_returnsOnlyThatUser() {
        UUID userId = UUID.randomUUID();
        repository.save(base().userId(userId).build());
        repository.save(base().userId(userId).build());
        repository.save(base().userId(UUID.randomUUID()).build());

        List<NotificationLog> results = repository.findByUserIdOrderByCreatedAtDesc(userId);

        assertThat(results).hasSize(2);
    }

    @Test
    void findByUserId_unknownUser_empty() {
        repository.save(base().userId(UUID.randomUUID()).build());

        assertThat(repository.findByUserIdOrderByCreatedAtDesc(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByUserId_orderedNewestFirst() {
        UUID userId = UUID.randomUUID();
        LocalDateTime older = LocalDateTime.now().minusHours(2);
        LocalDateTime newer = LocalDateTime.now().minusHours(1);

        repository.save(base().userId(userId).createdAt(older).build());
        repository.save(base().userId(userId).createdAt(newer).build());

        List<NotificationLog> results = repository.findByUserIdOrderByCreatedAtDesc(userId);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getCreatedAt()).isEqualTo(newer);
        assertThat(results.get(1).getCreatedAt()).isEqualTo(older);
    }

    // ── findByUserIdAndChannelOrderByCreatedAtDesc ────────────────────────────

    @Test
    void findByUserIdAndChannel_filtersByChannel() {
        UUID userId = UUID.randomUUID();
        repository.save(base().userId(userId).channel(Channel.EMAIL).build());
        repository.save(base().userId(userId).channel(Channel.WHATSAPP).build());
        repository.save(base().userId(userId).channel(Channel.WHATSAPP).build());

        List<NotificationLog> emails = repository.findByUserIdAndChannelOrderByCreatedAtDesc(userId, Channel.EMAIL);
        assertThat(emails).hasSize(1);
        assertThat(emails.get(0).getChannel()).isEqualTo(Channel.EMAIL);

        List<NotificationLog> whatsapps = repository.findByUserIdAndChannelOrderByCreatedAtDesc(userId, Channel.WHATSAPP);
        assertThat(whatsapps).hasSize(2);
    }

    @Test
    void findByUserIdAndChannel_wrongUser_empty() {
        UUID userId = UUID.randomUUID();
        repository.save(base().userId(userId).channel(Channel.EMAIL).build());

        assertThat(repository.findByUserIdAndChannelOrderByCreatedAtDesc(UUID.randomUUID(), Channel.EMAIL))
                .isEmpty();
    }

    // ── findByEventOrderByCreatedAtDesc ───────────────────────────────────────

    @Test
    void findByEvent_filtersByEvent() {
        repository.save(base().event("BOOKING_CONFIRMED").build());
        repository.save(base().event("BOOKING_CONFIRMED").build());
        repository.save(base().event("RENEWAL_REMINDER").build());

        assertThat(repository.findByEventOrderByCreatedAtDesc("BOOKING_CONFIRMED")).hasSize(2);
        assertThat(repository.findByEventOrderByCreatedAtDesc("RENEWAL_REMINDER")).hasSize(1);
        assertThat(repository.findByEventOrderByCreatedAtDesc("NONEXISTENT")).isEmpty();
    }

    // ── countByStatusAndEvent ─────────────────────────────────────────────────

    @Test
    void countByStatusAndEvent_countsCorrectly() {
        repository.save(base().status(DeliveryStatus.SENT).event("BOOKING_CONFIRMED").build());
        repository.save(base().status(DeliveryStatus.SENT).event("BOOKING_CONFIRMED").build());
        repository.save(base().status(DeliveryStatus.FAILED).event("BOOKING_CONFIRMED").build());
        repository.save(base().status(DeliveryStatus.SENT).event("RENEWAL_REMINDER").build());

        assertThat(repository.countByStatusAndEvent(DeliveryStatus.SENT, "BOOKING_CONFIRMED")).isEqualTo(2);
        assertThat(repository.countByStatusAndEvent(DeliveryStatus.FAILED, "BOOKING_CONFIRMED")).isEqualTo(1);
        assertThat(repository.countByStatusAndEvent(DeliveryStatus.SENT, "RENEWAL_REMINDER")).isEqualTo(1);
        assertThat(repository.countByStatusAndEvent(DeliveryStatus.FAILED, "RENEWAL_REMINDER")).isZero();
    }

    // ── countByStatus ─────────────────────────────────────────────────────────

    @Test
    void countByStatus_countsAcrossAllEvents() {
        repository.save(base().status(DeliveryStatus.SENT).event("BOOKING_CONFIRMED").build());
        repository.save(base().status(DeliveryStatus.SENT).event("RENEWAL_REMINDER").build());
        repository.save(base().status(DeliveryStatus.FAILED).event("BOOKING_CONFIRMED").build());

        assertThat(repository.countByStatus(DeliveryStatus.SENT)).isEqualTo(2);
        assertThat(repository.countByStatus(DeliveryStatus.FAILED)).isEqualTo(1);
        assertThat(repository.countByStatus(DeliveryStatus.PENDING)).isZero();
    }

    // ── findByStatusOrderByCreatedAtDesc ──────────────────────────────────────

    @Test
    void findByStatus_filtersByStatus() {
        repository.save(base().status(DeliveryStatus.SENT).build());
        repository.save(base().status(DeliveryStatus.FAILED).build());
        repository.save(base().status(DeliveryStatus.FAILED).build());

        assertThat(repository.findByStatusOrderByCreatedAtDesc(DeliveryStatus.FAILED)).hasSize(2);
        assertThat(repository.findByStatusOrderByCreatedAtDesc(DeliveryStatus.SENT)).hasSize(1);
        assertThat(repository.findByStatusOrderByCreatedAtDesc(DeliveryStatus.PENDING)).isEmpty();
    }

    // ── @PrePersist ───────────────────────────────────────────────────────────

    @Test
    void save_prePersistSetsCreatedAt() {
        NotificationLog log = base().build();
        assertThat(log.getCreatedAt()).isNull();

        NotificationLog saved = repository.save(log);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void save_withNullUserId_succeeds() {
        NotificationLog log = base().userId(null).build();

        NotificationLog saved = repository.saveAndFlush(log);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isNull();
    }
}
