package com.library.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConfig {

    // Zero retries: log the poison-pill record and skip it immediately.
    // Deserialization failures are surfaced here via ErrorHandlingDeserializer.
    private DefaultErrorHandler skipOnError() {
        return new DefaultErrorHandler(
                (record, ex) -> log.error(
                        "Skipping unprocessable record — topic={} partition={} offset={} cause={}",
                        record.topic(), record.partition(), record.offset(), ex.getMessage()),
                new FixedBackOff(0L, 0L)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> bookingKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setCommonErrorHandler(skipOnError());
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> reminderKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setCommonErrorHandler(skipOnError());
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> broadcastKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setCommonErrorHandler(skipOnError());
        return factory;
    }
}