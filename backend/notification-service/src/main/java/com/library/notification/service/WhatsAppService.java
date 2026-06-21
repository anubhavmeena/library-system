package com.library.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.notification.entity.NotificationLog;
import com.library.notification.enums.Channel;
import com.library.notification.enums.DeliveryStatus;
import com.library.notification.repository.NotificationLogRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private final NotificationLogRepository logRepository;
    private final ObjectMapper objectMapper;

    @Value("${meta.whatsapp.token:}")
    private String metaToken;

    @Value("${meta.whatsapp.phone-number-id:}")
    private String metaPhoneNumberId;

    @Value("${meta.whatsapp.api-version:v21.0}")
    private String metaApiVersion;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private boolean metaEnabled = false;

    @PostConstruct
    public void init() {
        if (!metaToken.isBlank() && !metaPhoneNumberId.isBlank()) {
            metaEnabled = true;
            log.info("Meta WhatsApp Cloud API configured — all WhatsApp notifications will use Meta");
        } else {
            log.warn("Meta WhatsApp not configured — WhatsApp messages will be logged only (dev mode)");
        }
    }

    public void send(String mobile, String message, String userId, String event) {
        DeliveryStatus status = DeliveryStatus.SENT;
        String errorMessage   = null;

        if (!metaEnabled) {
            log.info("[DEV] WhatsApp → {} | Event: {} | Message:\n{}", mobile, event, message);
        } else {
            try {
                String to = mobile.replaceAll("[^0-9+]", "");
                to = to.startsWith("+") ? to.substring(1) : "91" + to;

                Map<String, Object> body = Map.of(
                        "messaging_product", "whatsapp",
                        "to", to,
                        "type", "text",
                        "text", Map.of("body", message)
                );

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://graph.facebook.com/" + metaApiVersion + "/" + metaPhoneNumberId + "/messages"))
                        .header("Authorization", "Bearer " + metaToken)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() >= 400) {
                    throw new RuntimeException("Meta API error " + resp.statusCode() + ": " + resp.body());
                }

                log.info("WhatsApp sent to {} via Meta Cloud API | Event: {}", mobile, event);

            } catch (Exception e) {
                status       = DeliveryStatus.FAILED;
                errorMessage = e.getMessage();
                log.error("WhatsApp send failed to {} | Event: {}: {}", mobile, event, e.getMessage());
            }
        }

        saveLog(userId, mobile, message, event, status, errorMessage);
    }

    private void saveLog(String userId, String recipient, String message,
                         String event, DeliveryStatus status, String errorMessage) {
        try {
            NotificationLog entry = NotificationLog.builder()
                    .userId(userId != null ? UUID.fromString(userId) : null)
                    .channel(Channel.WHATSAPP)
                    .event(event)
                    .recipient(recipient)
                    .message(message)
                    .status(status)
                    .errorMessage(errorMessage)
                    .build();
            logRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to save WhatsApp notification log: {}", e.getMessage());
        }
    }
}
