package com.example.aichinese.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Authenticated password change (FRAUTH08): the current password is verified first. */
public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required.")
        @Size(max = 72, message = "Current password must be at most 72 characters.")
        String currentPassword,

        @NotBlank(message = "New password is required.")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters.")
        String newPassword,

        @NotBlank(message = "Confirm password is required.")
        @Size(max = 72, message = "Confirm password must be at most 72 characters.")
        String confirmPassword) {

    @JsonIgnore
    @AssertTrue(message = "Password and confirm password must be at most 72 bytes when encoded as UTF-8")
    public boolean isPasswordsWithinBcryptByteLimit() {
        return AuthDtoValidation.isWithinBcryptByteLimit(newPassword)
                && AuthDtoValidation.isWithinBcryptByteLimit(confirmPassword)
                && AuthDtoValidation.isWithinBcryptByteLimit(currentPassword);
    }

    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=***REDACTED***"
                + ", newPassword=***REDACTED***"
                + ", confirmPassword=***REDACTED***]";
    }
}