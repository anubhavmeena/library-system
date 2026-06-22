package com.library.notification.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConfigTest {

    private final KafkaConfig config = new KafkaConfig();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
    }

    @Test
    void bookingFactory_hasConcurrency3() {
        ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                config.bookingKafkaListenerContainerFactory();

        assertThat(ReflectionTestUtils.getField(factory, "concurrency")).isEqualTo(3);
    }

    @Test
    void bookingFactory_hasBatchAckMode() {
        ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                config.bookingKafkaListenerContainerFactory();

        assertThat(factory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.BATCH);
    }

    @Test
    void reminderFactory_hasConcurrency2() {
        ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                config.reminderKafkaListenerContainerFactory();

        assertThat(ReflectionTestUtils.getField(factory, "concurrency")).isEqualTo(2);
    }

    @Test
    void reminderFactory_hasBatchAckMode() {
        ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                config.reminderKafkaListenerContainerFactory();

        assertThat(factory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.BATCH);
    }

    @Test
    void twoFactories_areDistinctInstances() {
        var booking = config.bookingKafkaListenerContainerFactory();
        var reminder = config.reminderKafkaListenerContainerFactory();

        assertThat(booking).isNotSameAs(reminder);
    }
}
