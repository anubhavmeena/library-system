package com.library.admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AdminServiceApplicationTest {

    // Prevent Kafka producer from attempting a real connection during context load
    @MockBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void contextLoads() {
        // Verifies that all Spring beans wire up without error
    }
}
