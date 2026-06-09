package com.library.notification.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class KafkaConfigTest {

    @Mock
    ConsumerFactory<String, Object> consumerFactory;

    private final KafkaConfig config = new KafkaConfig();

    @Test
    void bookingFactory_hasConcurrency3() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                config.bookingKafkaListenerContainerFactory(consumerFactory);

        assertThat(ReflectionTestUtils.getField(factory, "concurrency")).isEqualTo(3);
    }

    @Test
    void bookingFactory_hasBatchAckMode() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                config.bookingKafkaListenerContainerFactory(consumerFactory);

        assertThat(factory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.BATCH);
    }

    @Test
    void reminderFactory_hasConcurrency2() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                config.reminderKafkaListenerContainerFactory(consumerFactory);

        assertThat(ReflectionTestUtils.getField(factory, "concurrency")).isEqualTo(2);
    }

    @Test
    void reminderFactory_hasBatchAckMode() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                config.reminderKafkaListenerContainerFactory(consumerFactory);

        assertThat(factory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.BATCH);
    }

    @Test
    void twoFactories_areDistinctInstances() {
        var booking = config.bookingKafkaListenerContainerFactory(consumerFactory);
        var reminder = config.reminderKafkaListenerContainerFactory(consumerFactory);

        assertThat(booking).isNotSameAs(reminder);
    }
}
