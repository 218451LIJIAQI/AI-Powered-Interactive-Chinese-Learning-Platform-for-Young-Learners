package com.example.aichinese.dto.auth;

import static com.example.aichinese.dto.DtoNormalization.redactValue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Authenticated password change (FRAUTH08): the current password is verified first. */
public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required.")
        @MaxUtf8Bytes
        String currentPassword,

        @NotBlank(message = "New password is required.")
        @Size(min = 8, message = "Password must be at least 8 characters.")
        @MaxUtf8Bytes
        String newPassword,

        @NotBlank(message = "Confirm password is required.")
        @MaxUtf8Bytes
        String confirmPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=" + redactValue(currentPassword)
                + ", newPassword=" + redactValue(newPassword)
                + ", confirmPassword=" + redactValue(confirmPassword) + "]";
    }
}