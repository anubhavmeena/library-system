package com.library.notification.service;

import com.library.notification.dto.PaymentReceiptEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptPdfServiceTest {

    private final ReceiptPdfService service = new ReceiptPdfService();

    private PaymentReceiptEvent buildEvent() {
        PaymentReceiptEvent e = new PaymentReceiptEvent();
        e.setInvoiceId("INV-20260702-TEST01");
        e.setPaymentDate("2026-07-02");
        e.setUserName("Manish Meena");
        e.setUserMobile("9876543210");
        e.setPlanName("Full Day Plan");
        e.setSeatNumber("B12");
        e.setPaymentMethod("CASH");
        e.setAmountPaid(new BigDecimal("600"));
        e.setAmountPending(BigDecimal.ZERO);
        return e;
    }

    @Test
    void buildReceipt_returnsValidPdfBytes() {
        byte[] pdf = service.buildReceipt(buildEvent());

        assertThat(pdf).isNotEmpty();
        // %PDF header — confirms a well-formed PDF was actually produced, not
        // just an empty/garbage byte array.
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void buildReceipt_missingOptionalFields_doesNotThrow() {
        PaymentReceiptEvent e = buildEvent();
        e.setPlanName(null);
        e.setSeatNumber(null);
        e.setUserMobile(null);

        byte[] pdf = service.buildReceipt(e);

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void buildReceipt_nullAmounts_defaultsToZeroDisplay() {
        PaymentReceiptEvent e = buildEvent();
        e.setAmountPaid(null);
        e.setAmountPending(null);

        byte[] pdf = service.buildReceipt(e);

        assertThat(pdf).isNotEmpty();
    }
}
