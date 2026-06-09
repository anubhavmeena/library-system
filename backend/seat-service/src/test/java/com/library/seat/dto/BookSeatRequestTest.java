package com.library.seat.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookSeatRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private BookSeatRequest validRequest() {
        BookSeatRequest r = new BookSeatRequest();
        r.setSeatNumber("A1");
        r.setMembershipId(UUID.randomUUID().toString());
        r.setShift("MORNING");
        r.setStartDate("2025-01-01");
        r.setEndDate("2025-01-31");
        return r;
    }

    @Test
    void allFieldsPresent_noViolations() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    void blankSeatNumber_violationReported() {
        BookSeatRequest r = validRequest();
        r.setSeatNumber("");
        Set<ConstraintViolation<BookSeatRequest>> violations = validator.validate(r);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("seatNumber"));
    }

    @Test
    void nullMembershipId_violationReported() {
        BookSeatRequest r = validRequest();
        r.setMembershipId(null);
        assertThat(validator.validate(r))
                .anyMatch(v -> v.getPropertyPath().toString().equals("membershipId"));
    }

    @Test
    void blankShift_violationReported() {
        BookSeatRequest r = validRequest();
        r.setShift("   ");
        assertThat(validator.validate(r))
                .anyMatch(v -> v.getPropertyPath().toString().equals("shift"));
    }

    @Test
    void nullStartDate_violationReported() {
        BookSeatRequest r = validRequest();
        r.setStartDate(null);
        assertThat(validator.validate(r))
                .anyMatch(v -> v.getPropertyPath().toString().equals("startDate"));
    }

    @Test
    void nullEndDate_violationReported() {
        BookSeatRequest r = validRequest();
        r.setEndDate(null);
        assertThat(validator.validate(r))
                .anyMatch(v -> v.getPropertyPath().toString().equals("endDate"));
    }

    @Test
    void allFieldsNull_fiveViolations() {
        assertThat(validator.validate(new BookSeatRequest())).hasSize(5);
    }
}
