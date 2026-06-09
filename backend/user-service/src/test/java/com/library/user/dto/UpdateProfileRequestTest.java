package com.library.user.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateProfileRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void allFieldsNull_noViolations() {
        // All fields are optional — null is allowed
        assertThat(validator.validate(new UpdateProfileRequest())).isEmpty();
    }

    @Test
    void nameTooShort_oneChar_violationOnName() {
        UpdateProfileRequest r = new UpdateProfileRequest();
        r.setName("A"); // min=2

        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(r);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    void nameAtMinLength_twoChars_noViolation() {
        UpdateProfileRequest r = new UpdateProfileRequest();
        r.setName("Al");

        assertThat(validator.validate(r)).isEmpty();
    }

    @Test
    void nameAtMaxLength_100chars_noViolation() {
        UpdateProfileRequest r = new UpdateProfileRequest();
        r.setName("A".repeat(100));

        assertThat(validator.validate(r)).isEmpty();
    }

    @Test
    void nameTooLong_101chars_violationOnName() {
        UpdateProfileRequest r = new UpdateProfileRequest();
        r.setName("A".repeat(101));

        assertThat(validator.validate(r))
                .anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    void addressTooLong_501chars_violationOnAddress() {
        UpdateProfileRequest r = new UpdateProfileRequest();
        r.setAddress("X".repeat(501));

        assertThat(validator.validate(r))
                .anyMatch(v -> v.getPropertyPath().toString().equals("address"));
    }

    @Test
    void emailTooLong_256chars_violationOnEmail() {
        UpdateProfileRequest r = new UpdateProfileRequest();
        r.setEmail("a".repeat(256));

        assertThat(validator.validate(r))
                .anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void validRequest_noViolations() {
        UpdateProfileRequest r = new UpdateProfileRequest();
        r.setName("Alice");
        r.setAddress("123 Main St");
        r.setGender("Female");
        r.setDateOfBirth("1995-06-15");
        r.setEmail("alice@test.com");

        assertThat(validator.validate(r)).isEmpty();
    }
}
