package com.example.aichinese.dto.admin;

import static com.example.aichinese.dto.DtoNormalization.strip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Deactivating or reactivating an account always needs a reason; it is saved to the audit log. */
public record AdminUserStatusRequest(
        @NotBlank(message = "A reason is required.")
        @Size(max = 500, message = "The reason must be 500 characters or fewer.")
        String reason) {

    public AdminUserStatusRequest {
        reason = strip(reason);
    }
}
