package com.library.notification.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BookingConfirmedEventTest {

    @Test
    void defaultConstructor_allReferenceFieldsNull() {
        BookingConfirmedEvent event = new BookingConfirmedEvent();

        assertThat(event.getUserId()).isNull();
        assertThat(event.getUserName()).isNull();
        assertThat(event.getUserMobile()).isNull();
        assertThat(event.getUserEmail()).isNull();
        assertThat(event.getPlanName()).isNull();
        assertThat(event.getPlanType()).isNull();
        assertThat(event.getSeatNumber()).isNull();
        assertThat(event.getShift()).isNull();
        assertThat(event.getStartDate()).isNull();
        assertThat(event.getEndDate()).isNull();
        assertThat(event.getAmountPaid()).isNull();
        assertThat(event.getEventType()).isNull();
    }

    @Test
    void settersAndGetters_allFieldsRoundtrip() {
        BookingConfirmedEvent event = new BookingConfirmedEvent();
        event.setUserId("user-123");
        event.setMembershipId("mem-456");
        event.setUserName("Alice");
        event.setUserMobile("9876543210");
        event.setUserEmail("alice@test.com");
        event.setPlanName("Monthly Plan");
        event.setPlanType("FULL_DAY");
        event.setSeatNumber("A1");
        event.setShift("MORNING");
        event.setStartDate("2025-01-01");
        event.setEndDate("2025-01-31");
        event.setAmountPaid(new BigDecimal("600.00"));
        event.setEventType("BOOKING_CONFIRMED");

        assertThat(event.getUserId()).isEqualTo("user-123");
        assertThat(event.getMembershipId()).isEqualTo("mem-456");
        assertThat(event.getUserName()).isEqualTo("Alice");
        assertThat(event.getUserMobile()).isEqualTo("9876543210");
        assertThat(event.getUserEmail()).isEqualTo("alice@test.com");
        assertThat(event.getPlanName()).isEqualTo("Monthly Plan");
        assertThat(event.getPlanType()).isEqualTo("FULL_DAY");
        assertThat(event.getSeatNumber()).isEqualTo("A1");
        assertThat(event.getShift()).isEqualTo("MORNING");
        assertThat(event.getStartDate()).isEqualTo("2025-01-01");
        assertThat(event.getEndDate()).isEqualTo("2025-01-31");
        assertThat(event.getAmountPaid()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(event.getEventType()).isEqualTo("BOOKING_CONFIRMED");
    }

    @Test
    void dataAnnotation_equalityBasedOnAllFields() {
        BookingConfirmedEvent e1 = new BookingConfirmedEvent();
        e1.setUserId("u1");
        e1.setAmountPaid(new BigDecimal("500"));

        BookingConfirmedEvent e2 = new BookingConfirmedEvent();
        e2.setUserId("u1");
        e2.setAmountPaid(new BigDecimal("500"));

        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }

    @Test
    void dataAnnotation_toStringContainsUserId() {
        BookingConfirmedEvent event = new BookingConfirmedEvent();
        event.setUserId("user-abc");

        assertThat(event.toString()).contains("user-abc");
    }
}
