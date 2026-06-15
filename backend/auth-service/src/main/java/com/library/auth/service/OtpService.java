package com.library.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OtpService {

    // ── apitxt SMS ────────────────────────────────────────────────────────────
    @Value("${apitxt.auth-key:}")
    private String apitxtAuthKey;

    // ── Twilio SMS ────────────────────────────────────────────────────────────
    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.phone-number:}")
    private String fromPhone;

    // ── Meta WhatsApp Cloud API ───────────────────────────────────────────────
    @Value("${meta.whatsapp.token:}")
    private String metaToken;

    @Value("${meta.whatsapp.phone-number-id:}")
    private String metaPhoneNumberId;

    @Value("${meta.whatsapp.template-name:library_otp}")
    private String metaTemplateName;

    @Value("${meta.whatsapp.api-version:v19.0}")
    private String metaApiVersion;

    // ── SendGrid Email ────────────────────────────────────────────────────────
    @Value("${sendgrid.api-key:}")
    private String sendGridApiKey;

    @Value("${notification.from-email:noreply@library.com}")
    private String fromEmail;

    @Value("${notification.from-name:Library System}")
    private String fromName;

    @Autowired
    private ObjectMapper objectMapper;

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        httpClient = HttpClient.newHttpClient();

        if (!metaToken.isBlank() && !metaPhoneNumberId.isBlank()) {
            log.info("Meta WhatsApp Cloud API configured — will be used as primary OTP channel");
        } else {
            log.warn("Meta WhatsApp not configured — will fall back to SMS");
        }

        if (!apitxtAuthKey.isBlank()) {
            log.info("apitxt SMS configured — will be used as default SMS OTP channel");
        } else {
            log.warn("apitxt not configured — will fall back to Twilio SMS");
        }

        if (!accountSid.isBlank() && !authToken.isBlank()) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio SMS initialized as fallback OTP channel");
        } else {
            log.warn("Twilio credentials not configured — SMS fallback unavailable (dev mode)");
        }
    }

    public void sendOtp(String contact, String contactType, String otp) {
        if ("MOBILE".equalsIgnoreCase(contactType)) {
            if (!metaToken.isBlank() && !metaPhoneNumberId.isBlank()) {
                try {
                    sendWhatsAppMeta(contact, otp);
                    return;
                } catch (Exception e) {
                    log.warn("Meta WhatsApp failed for {}, falling back to Twilio SMS: {}", contact, e.getMessage());
                }
            }
            if (!apitxtAuthKey.isBlank()) {
                try {
                    sendApitxtSms(contact, otp);
                    return;
                } catch (Exception e) {
                    log.warn("apitxt failed for {}, falling back to Twilio: {}", contact, e.getMessage());
                }
            }
            String smsBody = String.format(
                    "Library OTP: %s\nValid for 5 minutes. Do not share.", otp);
            sendSms(contact, smsBody, otp);
        } else if ("EMAIL".equalsIgnoreCase(contactType)) {
            String emailBody = String.format(
                    "📚 Library OTP Verification\n\nYour OTP is: %s\n\nValid for 5 minutes. Do not share.", otp);
            sendEmail(contact, emailBody, otp);
        }
    }

    public boolean isLiveConfigured() {
        return (!metaToken.isBlank() && !metaPhoneNumberId.isBlank())
                || !apitxtAuthKey.isBlank()
                || (!accountSid.isBlank() && !authToken.isBlank());
    }

    private void sendWhatsAppMeta(String mobile, String otp) throws Exception {
        String to = mobile.startsWith("+") ? mobile.substring(1) : "91" + mobile;
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "template",
                "template", Map.of(
                        "name", metaTemplateName,
                        "language", Map.of("code", "en"),
                        "components", List.of(
                                Map.of("type", "body",
                                        "parameters", List.of(
                                                Map.of("type", "text", "text", otp)
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
        log.info("WhatsApp OTP sent to {} via Meta Cloud API", mobile);
    }

    private void sendApitxtSms(String mobile, String otp) throws Exception {
        String digits = mobile.startsWith("+91") ? mobile.substring(3)
                      : mobile.startsWith("+")   ? mobile.substring(1)
                      : mobile;
        String url = "https://apitxt.com/api/sendOTP"
                + "?authkey=" + apitxtAuthKey
                + "&mobile=91" + digits
                + "&otp=" + otp;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("apitxt HTTP error " + resp.statusCode() + ": " + resp.body());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(resp.body(), Map.class);
        Object status = body.get("status");
        if (!Integer.valueOf(200).equals(status) && !"200".equals(String.valueOf(status))) {
            throw new RuntimeException("apitxt error: " + resp.body());
        }
        log.info("SMS OTP sent to {} via apitxt (request_id={})", mobile, body.get("request_id"));
    }

    private void sendSms(String mobile, String message, String otp) {
        if (accountSid.isBlank()) {
            log.info("DEV MODE - OTP for mobile {}: {}", mobile, otp);
            return;
        }
        try {
            String formatted = mobile.startsWith("+") ? mobile : "+91" + mobile;
            Message msg = Message.creator(
                    new PhoneNumber(formatted),
                    new PhoneNumber(fromPhone),
                    message
            ).create();
            log.info("SMS sent to {}. SID: {}", mobile, msg.getSid());
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", mobile, e.getMessage());
            throw new RuntimeException("Failed to send OTP. Please try again.");
        }
    }

    private void sendEmail(String email, String message, String otp) {
        if (sendGridApiKey.isBlank()) {
            log.info("DEV MODE - OTP for email {}: {}", email, otp);
            return;
        }
        try {
            Mail mail = new Mail(
                    new Email(fromEmail, fromName),
                    "Your OTP Code",
                    new Email(email),
                    new Content("text/plain", message)
            );
            SendGrid sg = new SendGrid(sendGridApiKey);
            Request req = new Request();
            req.setMethod(Method.POST);
            req.setEndpoint("mail/send");
            req.setBody(mail.build());
            Response resp = sg.api(req);
            if (resp.getStatusCode() >= 400) {
                throw new RuntimeException("SendGrid error: " + resp.getStatusCode());
            }
            log.info("Email OTP sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send email OTP to {}: {}", email, e.getMessage());
            throw new RuntimeException("Failed to send OTP. Please try again.");
        }
    }
}