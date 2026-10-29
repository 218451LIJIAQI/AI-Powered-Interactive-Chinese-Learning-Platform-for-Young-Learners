package com.example.aichinese.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Asks for an e-mail change; the current password proves it is the account owner. */
public record EmailChangeRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String newEmail,

        @NotBlank(message = "Current password is required.")
        @Size(max = 72, message = "Current password must be at most 72 characters.")
        String currentPassword) {

    public EmailChangeRequest {
        if (newEmail != null) {
            newEmail = newEmail.strip();
        }
    }

    @JsonIgnore
    @AssertTrue(message = "Password must be at most 72 bytes when encoded as UTF-8")
    public boolean isPasswordWithinBcryptByteLimit() {
        return AuthDtoValidation.isWithinBcryptByteLimit(currentPassword);
    }

    @Override
    public String toString() {
        return "EmailChangeRequest[newEmail=" + newEmail + ", currentPassword=***REDACTED***]";
    }
}
