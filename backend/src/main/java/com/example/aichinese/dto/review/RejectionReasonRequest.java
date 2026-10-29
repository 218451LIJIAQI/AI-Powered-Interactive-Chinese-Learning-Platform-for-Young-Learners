package com.example.aichinese.dto.review;

import static com.example.aichinese.dto.DtoNormalization.strip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An admin's rejection of a teacher application or of submitted content; the teacher sees the reason. */
public record RejectionReasonRequest(
        @NotBlank(message = "Rejection reason is required.")
        @Size(max = 1000, message = "Rejection reason must be 1000 characters or fewer.")
        String reason) {

    public RejectionReasonRequest {
        reason = strip(reason);
    }
}