package com.library.notification.config;

import com.library.notification.dto.BookingConfirmedEvent;
import com.library.notification.dto.BroadcastNotificationEvent;
import com.library.notification.dto.PaymentReceiptEvent;
import com.library.notification.dto.RenewalReminderEvent;
import com.library.notification.dto.SeatAssistanceEvent;
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
    public ConcurrentKafkaListenerContainerFactory<String, SeatAssistanceEvent>
            seatAssistanceKafkaListenerContainerFactory() {

        JsonDeserializer<SeatAssistanceEvent> valueDeser =
                new JsonDeserializer<>(SeatAssistanceEvent.class);
        valueDeser.addTrustedPackages("com.library.*");
        valueDeser.ignoreTypeHeaders();

        DefaultKafkaConsumerFactory<String, SeatAssistanceEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(
                        Map.of(
                                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                                ConsumerConfig.GROUP_ID_CONFIG, "notification-seat-assistance-group",
                                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
                        ),
                        new StringDeserializer(),
                        valueDeser
                );

        ConcurrentKafkaListenerContainerFactory<String, SeatAssistanceEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setCommonErrorHandler(skipOnError());
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentReceiptEvent>
            receiptKafkaListenerContainerFactory() {

        JsonDeserializer<PaymentReceiptEvent> valueDeser =
                new JsonDeserializer<>(PaymentReceiptEvent.class);
        valueDeser.addTrustedPackages("com.library.*");
        valueDeser.ignoreTypeHeaders();

        DefaultKafkaConsumerFactory<String, PaymentReceiptEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(
                        Map.of(
                                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                                ConsumerConfig.GROUP_ID_CONFIG, "notification-receipt-group",
                                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
                        ),
                        new StringDeserializer(),
                        valueDeser
                );

        ConcurrentKafkaListenerContainerFactory<String, PaymentReceiptEvent> factory =
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
