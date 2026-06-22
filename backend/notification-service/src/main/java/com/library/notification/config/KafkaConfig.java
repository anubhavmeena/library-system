package com.library.notification.config;

import com.library.notification.dto.BookingConfirmedEvent;
import com.library.notification.dto.BroadcastNotificationEvent;
import com.library.notification.dto.RenewalReminderEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Slf4j
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // Zero retries: log the poison-pill record and skip it immediately.
    // Deserialization failures are surfaced here via ErrorHandlingDeserializer.
    private DefaultErrorHandler skipOnError() {
        return new DefaultErrorHandler(
                (record, ex) -> log.error(
                        "Skipping unprocessable record — topic={} partition={} offset={} cause={}",
                        record.topic(), record.partition(), record.offset(), ex.getMessage(), ex),
                new FixedBackOff(0L, 0L)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent>
            bookingKafkaListenerContainerFactory() {

        JsonDeserializer<BookingConfirmedEvent> valueDeser =
                new JsonDeserializer<>(BookingConfirmedEvent.class);
        valueDeser.addTrustedPackages("com.library.*");
        valueDeser.ignoreTypeHeaders();

        DefaultKafkaConsumerFactory<String, BookingConfirmedEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(
                        Map.of(
                                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                                ConsumerConfig.GROUP_ID_CONFIG, "notification-booking-group",
                                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
                        ),
                        new StringDeserializer(),
                        valueDeser
                );

        ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setCommonErrorHandler(skipOnError());
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RenewalReminderEvent>
            reminderKafkaListenerContainerFactory() {

        JsonDeserializer<RenewalReminderEvent> valueDeser =
                new JsonDeserializer<>(RenewalReminderEvent.class);
        valueDeser.addTrustedPackages("com.library.*");
        valueDeser.ignoreTypeHeaders();

        DefaultKafkaConsumerFactory<String, RenewalReminderEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(
                        Map.of(
                                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                                ConsumerConfig.GROUP_ID_CONFIG, "notification-reminder-group",
                                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
                        ),
                        new StringDeserializer(),
                        valueDeser
                );

        ConcurrentKafkaListenerContainerFactory<String, RenewalReminderEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setCommonErrorHandler(skipOnError());
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BroadcastNotificationEvent>
            broadcastKafkaListenerContainerFactory() {

        JsonDeserializer<BroadcastNotificationEvent> valueDeser =
                new JsonDeserializer<>(BroadcastNotificationEvent.class);
        valueDeser.addTrustedPackages("*");
        valueDeser.ignoreTypeHeaders();

        DefaultKafkaConsumerFactory<String, BroadcastNotificationEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(
                        Map.of(
                                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                                ConsumerConfig.GROUP_ID_CONFIG, "notification-broadcast-group",
                                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
                        ),
                        new StringDeserializer(),
                        valueDeser
                );

        ConcurrentKafkaListenerContainerFactory<String, BroadcastNotificationEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setCommonErrorHandler(skipOnError());
        return factory;
    }
}
