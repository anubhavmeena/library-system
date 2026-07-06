package com.library.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendOtpRequest {
    @NotBlank private String contact;     // mobile number or email
    @NotBlank private String contactType; // MOBILE or EMAIL

    // Optional channel override for MOBILE contacts — null/omitted means the
    // default routing (Meta WhatsApp, then apitxt SMS, then Twilio SMS).
    // "SMS" forces apitxt/Twilio SMS, skipping WhatsApp entirely — used by the
    // "Send via SMS instead" fallback after a WhatsApp OTP isn't received.
    private String channel;
}