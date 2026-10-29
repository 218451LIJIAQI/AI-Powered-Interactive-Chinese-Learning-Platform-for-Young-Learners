package com.example.aichinese.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email) {

    public ForgotPasswordRequest {
        if (email != null) {
            email = email.strip();
        }
    }
}
