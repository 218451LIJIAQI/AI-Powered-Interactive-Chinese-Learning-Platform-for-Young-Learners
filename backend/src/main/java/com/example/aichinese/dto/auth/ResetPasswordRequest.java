package com.example.aichinese.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Reset token is required")
        @Size(max = 128, message = "Reset token must be at most 128 characters")
        String token,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,
        @NotBlank(message = "Confirm password is required")
        @Size(max = 72, message = "Confirm password must be at most 72 characters")
        String confirmPassword) {

    public ResetPasswordRequest {
        if (token != null) {
            token = token.strip();
        }
    }

    @JsonIgnore
    @AssertTrue(message = "Password and confirm password must be at most 72 bytes when encoded as UTF-8")
    public boolean isPasswordsWithinBcryptByteLimit() {
        return AuthDtoValidation.isWithinBcryptByteLimit(password)
                && AuthDtoValidation.isWithinBcryptByteLimit(confirmPassword);
    }

    @Override
    public String toString() {
        return "ResetPasswordRequest[token=***REDACTED***"
                + ", password=***REDACTED***"
                + ", confirmPassword=***REDACTED***]";
    }
}
