package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.strip;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "status", "reviewerNote" })
public record ReviewAiResponseReportRequest(
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

    private static String redactLength(String value) {
        return value == null ? "null" : "<redacted:" + value.length() + ">";
    }
}
