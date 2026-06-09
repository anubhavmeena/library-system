package com.library.notification.service;

import com.library.notification.entity.NotificationLog;
import com.library.notification.enums.Channel;
import com.library.notification.enums.DeliveryStatus;
import com.library.notification.repository.NotificationLogRepository;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    NotificationLogRepository logRepository;

    @InjectMocks
    EmailService emailService;

    @BeforeEach
    void setup() {
        // Dev mode by default — no real SendGrid calls
        ReflectionTestUtils.setField(emailService, "sendgridApiKey", "");
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@test.com");
        ReflectionTestUtils.setField(emailService, "fromName", "Test Library");
    }

    // ── Dev mode (blank API key) ───────────────────────────────────────────────

    @Test
    void sendText_devMode_savesLogWithSentStatus() {
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);

        emailService.sendText("user@test.com", "Subject", "Body", UUID.randomUUID().toString(), "BOOKING_CONFIRMED");

        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(captor.getValue().getChannel()).isEqualTo(Channel.EMAIL);
        assertThat(captor.getValue().getRecipient()).isEqualTo("user@test.com");
        assertThat(captor.getValue().getEvent()).isEqualTo("BOOKING_CONFIRMED");
    }

    @Test
    void sendText_devMode_userIdParsedToUuid() {
        String userId = UUID.randomUUID().toString();
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);

        emailService.sendText("u@t.com", "S", "B", userId, "E");

        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(UUID.fromString(userId));
    }

    @Test
    void sendText_nullUserId_savedWithNullUserId() {
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);

        emailService.sendText("admin@test.com", "Subject", "Body", null, "ADMIN_BOOKING_ALERT");

        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
    }

    @Test
    void sendText_bodyExactly1000Chars_notTruncated() {
        String body = "A".repeat(1000);
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);

        emailService.sendText("u@t.com", "S", body, null, "E");

        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).hasSize(1000);
    }

    @Test
    void sendText_bodyOver1000Chars_truncatedTo1000() {
        String body = "B".repeat(1500);
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);

        emailService.sendText("u@t.com", "S", body, null, "E");

        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).hasSize(1000);
    }

    @Test
    void sendText_nullBody_savedAsEmpty() {
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);

        emailService.sendText("u@t.com", "S", null, null, "E");

        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).isEqualTo("");
    }

    @Test
    void sendHtml_devMode_channelIsEmail() {
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);

        emailService.sendHtml("u@t.com", "S", "<html><body>Hi</body></html>", null, "E");

        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getChannel()).isEqualTo(Channel.EMAIL);
        assertThat(captor.getValue().getStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void saveLog_dbFailure_swallowed() {
        doThrow(new RuntimeException("DB down")).when(logRepository).save(any());

        // EmailService must not rethrow — consumer cannot crash
        assertThatCode(() -> emailService.sendText("u@t.com", "S", "B", null, "E"))
                .doesNotThrowAnyException();
    }

    // ── SendGrid configured ───────────────────────────────────────────────────

    @Test
    void sendText_sendgridConfigured_success_savedAsSent() throws IOException {
        ReflectionTestUtils.setField(emailService, "sendgridApiKey", "SG.test-key");

        try (MockedConstruction<SendGrid> mocked = mockConstruction(SendGrid.class, (mock, ctx) -> {
            Response resp = new Response();
            resp.setStatusCode(202);
            when(mock.api(any())).thenReturn(resp);
        })) {
            emailService.sendText("user@test.com", "Subject", "Body text",
                    UUID.randomUUID().toString(), "BOOKING_CONFIRMED");

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(DeliveryStatus.SENT);
            assertThat(mocked.constructed()).hasSize(1);
        }
    }

    @Test
    void sendText_sendgridConfigured_http4xx_savedAsFailed() throws IOException {
        ReflectionTestUtils.setField(emailService, "sendgridApiKey", "SG.test-key");

        try (MockedConstruction<SendGrid> ignored = mockConstruction(SendGrid.class, (mock, ctx) -> {
            Response resp = new Response();
            resp.setStatusCode(400);
            resp.setBody("Bad Request");
            when(mock.api(any())).thenReturn(resp);
        })) {
            emailService.sendText("user@test.com", "Subject", "Body text", null, "BOOKING_CONFIRMED");

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(captor.getValue().getErrorMessage()).contains("400");
        }
    }

    @Test
    void sendText_sendgridConfigured_ioException_savedAsFailed() throws IOException {
        ReflectionTestUtils.setField(emailService, "sendgridApiKey", "SG.test-key");

        try (MockedConstruction<SendGrid> ignored = mockConstruction(SendGrid.class, (mock, ctx) ->
                when(mock.api(any())).thenThrow(new IOException("Network error")))) {

            // Must NOT throw — notification failure must not crash the consumer
            assertThatCode(() -> emailService.sendText("user@test.com", "Subject", "Body text",
                    null, "BOOKING_CONFIRMED"))
                    .doesNotThrowAnyException();

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(captor.getValue().getErrorMessage()).contains("Network error");
        }
    }

    @Test
    void sendHtml_sendgridConfigured_usesHtmlBody() throws IOException {
        ReflectionTestUtils.setField(emailService, "sendgridApiKey", "SG.test-key");
        String htmlBody = "<h1>Confirmed</h1>";

        try (MockedConstruction<SendGrid> ignored = mockConstruction(SendGrid.class, (mock, ctx) -> {
            Response resp = new Response();
            resp.setStatusCode(202);
            when(mock.api(any())).thenReturn(resp);
        })) {
            emailService.sendHtml("user@test.com", "Subject", htmlBody, null, "BOOKING_CONFIRMED");

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository).save(captor.capture());
            // htmlBody is the logBody for HTML sends
            assertThat(captor.getValue().getMessage()).isEqualTo(htmlBody);
            assertThat(captor.getValue().getStatus()).isEqualTo(DeliveryStatus.SENT);
        }
    }
}
