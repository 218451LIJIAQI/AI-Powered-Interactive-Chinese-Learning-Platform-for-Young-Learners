package com.example.aichinese.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminInviteRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email) {

    public AdminInviteRequest {
        if (email != null) {
            email = email.strip();
        }
    }
}
