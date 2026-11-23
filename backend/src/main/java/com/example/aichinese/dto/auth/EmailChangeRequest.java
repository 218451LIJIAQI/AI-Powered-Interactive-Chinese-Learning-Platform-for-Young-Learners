package com.example.aichinese.dto.auth;

import static com.example.aichinese.dto.DtoNormalization.redactValue;
import static com.example.aichinese.dto.DtoNormalization.strip;

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
        @MaxUtf8Bytes
        String currentPassword) {

    public EmailChangeRequest {
        newEmail = strip(newEmail);
    }

    @Override
    public String toString() {
        return "EmailChangeRequest[newEmail=" + redactValue(newEmail)
                + ", currentPassword=" + redactValue(currentPassword) + "]";
    }
}
