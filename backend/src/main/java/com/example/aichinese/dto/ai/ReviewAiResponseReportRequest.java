package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.redactLength;
import static com.example.aichinese.dto.DtoNormalization.strip;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewAiResponseReportRequest(
        @Schema(allowableValues = { "RESOLVED", "DISMISSED" })
        @NotBlank(message = "Review status is required.")
        @Size(max = 30, message = "Review status must be 30 characters or fewer.")
        String status,

        @NotBlank(message = "Reviewer note is required.")
        @Size(max = 1000, message = "Reviewer note must be 1000 characters or fewer.")
        String reviewerNote) {

    public ReviewAiResponseReportRequest {
        status = strip(status);
        reviewerNote = strip(reviewerNote);
    }

    @Override
    public String toString() {
        return "ReviewAiResponseReportRequest["
                + "status=" + status
                + ", reviewerNote=" + redactLength(reviewerNote)
                + "]";
    }
}
