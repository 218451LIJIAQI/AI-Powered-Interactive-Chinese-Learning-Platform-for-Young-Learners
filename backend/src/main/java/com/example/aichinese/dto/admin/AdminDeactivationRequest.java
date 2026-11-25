package com.example.aichinese.dto.admin;

import static com.example.aichinese.dto.DtoNormalization.redactLength;
import static com.example.aichinese.dto.DtoNormalization.stripMultiline;

import com.example.aichinese.util.AiInputSanitizer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Deactivating an account always needs a reason; it is saved to the audit log. */
public record AdminDeactivationRequest(
        @NotBlank(message = "A reason is required.")
        @Size(max = 500, message = "The reason must be 500 characters or fewer.")
        String reason) {

    public AdminDeactivationRequest {
        reason = AiInputSanitizer.sanitizeUnbounded(stripMultiline(reason));
    }

    @Override
    public String toString() {
        return "AdminDeactivationRequest[reason=" + redactLength(reason) + "]";
    }
}
