package com.example.aichinese.dto.admin;

import static com.example.aichinese.dto.DtoNormalization.redactValue;
import static com.example.aichinese.dto.DtoNormalization.strip;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminInviteRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email) {

    public AdminInviteRequest {
        email = strip(email);
    }

    @Override
    public String toString() {
        return "AdminInviteRequest[email=" + redactValue(email) + "]";
    }
}
