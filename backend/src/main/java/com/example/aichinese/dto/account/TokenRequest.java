package com.example.aichinese.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A link token from an e-mail (an e-mail change confirmation, a password reset or an admin invite). */
public record TokenRequest(
        @NotBlank(message = "Reset token is required")
        @Size(max = 128, message = "Reset token must be at most 128 characters")
        String token) {

    @Override
    public String toString() {
        return "TokenRequest[token=***REDACTED***]";
    }
}
