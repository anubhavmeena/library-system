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
import java.util.List;
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

    @Value("${meta.whatsapp.template-name:library_notification}")
    private String metaTemplateName;

    @Value("${meta.whatsapp.language:en_US}")
    private String metaLanguage;

    @Value("${meta.whatsapp.receipt-template-name:payment_receipt}")
    private String receiptTemplateName;

    // "payment_receipt" was created under plain English ("en"), not English (US)
    // ("en_US") like the other template — Meta requires an exact language-code
    // match per template or the send fails with a template-not-found error, so
    // this is intentionally a separate property from metaLanguage above.
    @Value("${meta.whatsapp.receipt-language:en}")
    private String receiptLanguage;

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

                // Meta template params reject newlines, tabs, or 5+ consecutive spaces
                String param = message.replaceAll("[\n\r\t]", " ").replaceAll(" {5,}", "    ").trim();

                Map<String, Object> body = Map.of(
                        "messaging_product", "whatsapp",
                        "to", to,
                        "type", "template",
                        "template", Map.of(
                                "name", metaTemplateName,
                                "language", Map.of("code", metaLanguage),
                                "components", List.of(
                                        Map.of("type", "body",
                                               "parameters", List.of(
                                                       Map.of("type", "text", "text", param)
                                               )
                                        )
                                )
                        )
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

    // Sends the approved "payment_receipt" template. NOTE: despite the template's
    // body text claiming a receipt is "attached", the template as actually
    // registered in Meta has a static TEXT header (literal text "Document" —
    // apparently typed into the header field rather than the header FORMAT being
    // set to "Document" when the template was created), not a real document-media
    // header. Confirmed in production: sending a "document"-type header parameter
    // against this template gets rejected with Meta error 132012 ("header: Format
    // mismatch, expected TEXT, received DOCUMENT") — a 100% failure rate for every
    // payment-receipt WhatsApp send since this went live. A static-text header
    // with no {{}} placeholder takes no parameters, so the fix is to omit the
    // header component entirely, not to send it as text either. documentUrl/
    // documentFilename are kept as parameters purely for logging (what the
    // receipt link *would* have been) — until the template is recreated in Meta
    // with a genuine Document header format, the PDF cannot be attached via
    // WhatsApp at all; only the email copy carries a real attachment.
    // bodyParams must match the template's {{1}}..{{n}} order exactly — for
    // "payment_receipt" that's [name, amountPaid, invoiceId, paymentDate,
    // pendingAmount].
    public void sendDocumentTemplate(String mobile, String documentUrl, String documentFilename,
                                     List<String> bodyParams, String userId, String event) {
        String logMessage = "[payment_receipt] wouldBeAttachment=" + documentFilename + " params=" + bodyParams;
        DeliveryStatus status = DeliveryStatus.SENT;
        String errorMessage   = null;

        if (!metaEnabled) {
            log.info("[DEV] WhatsApp (payment_receipt template) → {} | Event: {} | {}", mobile, event, logMessage);
        } else {
            try {
                String to = mobile.replaceAll("[^0-9+]", "");
                to = to.startsWith("+") ? to.substring(1) : "91" + to;

                List<Object> bodyParameters = bodyParams.stream()
                        .map(WhatsAppService::sanitizeParam)
                        .<Object>map(p -> Map.of("type", "text", "text", p))
                        .toList();

                Map<String, Object> body = Map.of(
                        "messaging_product", "whatsapp",
                        "to", to,
                        "type", "template",
                        "template", Map.of(
                                "name", receiptTemplateName,
                                "language", Map.of("code", receiptLanguage),
                                "components", List.of(
                                        Map.of("type", "body", "parameters", bodyParameters)
                                )
                        )
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

                log.info("WhatsApp payment_receipt template sent to {} via Meta Cloud API | Event: {}", mobile, event);

            } catch (Exception e) {
                status       = DeliveryStatus.FAILED;
                errorMessage = e.getMessage();
                log.error("WhatsApp payment_receipt template send failed to {} | Event: {}: {}", mobile, event, e.getMessage());
            }
        }

        saveLog(userId, mobile, logMessage, event, status, errorMessage);
    }

    // Meta template params reject newlines, tabs, or 5+ consecutive spaces
    private static String sanitizeParam(String s) {
        if (s == null) return "";
        return s.replaceAll("[\n\r\t]", " ").replaceAll(" {5,}", "    ").trim();
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
