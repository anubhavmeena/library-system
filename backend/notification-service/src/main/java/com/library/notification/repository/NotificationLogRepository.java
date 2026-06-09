package com.library.notification.repository;

import com.library.notification.entity.NotificationLog;
import com.library.notification.enums.Channel;
import com.library.notification.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    List<NotificationLog> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<NotificationLog> findByUserIdAndChannelOrderByCreatedAtDesc(
            UUID userId,
            Channel channel
    );

    List<NotificationLog> findByEventOrderByCreatedAtDesc(String event);

    long countByStatusAndEvent(
            DeliveryStatus status,
            String event
    );

    long countByStatus(DeliveryStatus status);

    List<NotificationLog> findByStatusOrderByCreatedAtDesc(
            DeliveryStatus status
    );
}