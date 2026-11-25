package com.example.aichinese.dto.admin;

import static com.example.aichinese.dto.DtoNormalization.redactLength;
import static com.example.aichinese.dto.DtoNormalization.stripMultiline;

import com.example.aichinese.util.AiInputSanitizer;

import jakarta.validation.constraints.Size;

/**
 * Reactivating an account runs at once and needs no reason: it is easy to take back (deactivate again). A reason given
 * is saved to the audit log; a blank one is none.
 */
public record AdminReactivationRequest(
        @Size(max = 500, message = "The reason must be 500 characters or fewer.")
        String reason) {

    public AdminReactivationRequest {
        reason = AiInputSanitizer.sanitizeUnbounded(stripMultiline(reason));
    }

    @Override
    public String toString() {
        return "AdminReactivationRequest[reason=" + redactLength(reason) + "]";
    }
}
