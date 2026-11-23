package com.example.aichinese.dto.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Ensures a string's UTF-8 encoding does not exceed {@link #value()} bytes (e.g. BCrypt's 72-byte password ceiling).
 * Reports violations directly on the annotated field rather than a synthetic {@code @AssertTrue} method name.
 */
@Documented
@Constraint(validatedBy = MaxUtf8Bytes.Validator.class)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int BCRYPT_MAX_PASSWORD_BYTES = 72;

    int value() default BCRYPT_MAX_PASSWORD_BYTES;

    String message() default "Password must be at most {value} bytes when encoded as UTF-8";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<MaxUtf8Bytes, String> {

        private int maxBytes;

        @Override
        public void initialize(MaxUtf8Bytes constraintAnnotation) {
            this.maxBytes = constraintAnnotation.value();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            // Each UTF-16 char encodes to at least 1 UTF-8 byte, so oversized strings fail without allocating.
            if (value.length() > maxBytes) {
                return false;
            }
            return value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
        }
    }
}
