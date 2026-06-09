package com.library.notification.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RenewalReminderEventTest {

    @Test
    void defaultConstructor_daysRemainingIsZero() {
        // int primitive — default is 0, not null
        assertThat(new RenewalReminderEvent().getDaysRemaining()).isZero();
    }

    @Test
    void defaultConstructor_referenceFieldsNull() {
        RenewalReminderEvent event = new RenewalReminderEvent();

        assertThat(event.getUserId()).isNull();
        assertThat(event.getMembershipId()).isNull();
        assertThat(event.getUserName()).isNull();
        assertThat(event.getUserMobile()).isNull();
        assertThat(event.getUserEmail()).isNull();
        assertThat(event.getSeatNumber()).isNull();
        assertThat(event.getExpiryDate()).isNull();
        assertThat(event.getEventType()).isNull();
    }

    @Test
    void settersAndGetters_allFieldsRoundtrip() {
        RenewalReminderEvent event = new RenewalReminderEvent();
        event.setUserId("user-789");
        event.setMembershipId("mem-101");
        event.setUserName("Charlie");
        event.setUserMobile("9123456789");
        event.setUserEmail("charlie@test.com");
        event.setSeatNumber("C5");
        event.setExpiryDate("2025-02-01");
        event.setDaysRemaining(7);
        event.setEventType("RENEWAL_REMINDER");

        assertThat(event.getUserId()).isEqualTo("user-789");
        assertThat(event.getMembershipId()).isEqualTo("mem-101");
        assertThat(event.getUserName()).isEqualTo("Charlie");
        assertThat(event.getUserMobile()).isEqualTo("9123456789");
        assertThat(event.getUserEmail()).isEqualTo("charlie@test.com");
        assertThat(event.getSeatNumber()).isEqualTo("C5");
        assertThat(event.getExpiryDate()).isEqualTo("2025-02-01");
        assertThat(event.getDaysRemaining()).isEqualTo(7);
        assertThat(event.getEventType()).isEqualTo("RENEWAL_REMINDER");
    }

    @Test
    void dataAnnotation_equalityBasedOnAllFields() {
        RenewalReminderEvent e1 = new RenewalReminderEvent();
        e1.setDaysRemaining(3);
        e1.setUserId("u1");

        RenewalReminderEvent e2 = new RenewalReminderEvent();
        e2.setDaysRemaining(3);
        e2.setUserId("u1");

        assertThat(e1).isEqualTo(e2);
    }

    @Test
    void dataAnnotation_notEqualWhenDaysDiffer() {
        RenewalReminderEvent e1 = new RenewalReminderEvent();
        e1.setDaysRemaining(3);

        RenewalReminderEvent e2 = new RenewalReminderEvent();
        e2.setDaysRemaining(7);

        assertThat(e1).isNotEqualTo(e2);
    }
}
