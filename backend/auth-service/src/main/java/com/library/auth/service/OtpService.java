package com.library.auth.service;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OtpService {

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.phone-number:}")
    private String fromPhone;

    @Value("${sendgrid.api-key:}")
    private String sendGridApiKey;

    @Value("${notification.from-email:noreply@library.com}")
    private String fromEmail;

    @Value("${notification.from-name:Library System}")
    private String fromName;

    @PostConstruct
    public void init() {
        if (!accountSid.isBlank() && !authToken.isBlank()) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio initialized successfully");
        } else {
            log.warn("Twilio credentials not configured — OTP logged only (dev mode)");
        }
    }

    public void sendOtp(String contact, String contactType, String otp) {
        String message = String.format(
                "📚 Library OTP Verification\n\nYour OTP is: *%s*\n\nValid for 5 minutes. Do not share.",
                otp
        );
        if ("MOBILE".equalsIgnoreCase(contactType)) {
            sendSms(contact, message, otp);
        } else if ("EMAIL".equalsIgnoreCase(contactType)) {
            sendEmail(contact, message, otp);
        }
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