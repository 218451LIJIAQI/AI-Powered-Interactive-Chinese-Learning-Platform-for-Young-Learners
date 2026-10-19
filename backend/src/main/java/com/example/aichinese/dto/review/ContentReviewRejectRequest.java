package com.example.aichinese.dto.review;

import static com.example.aichinese.dto.DtoNormalization.strip;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin rejection of submitted content; the reason is shown to the teacher. */
@JsonPropertyOrder({ "reason" })
public record ContentReviewRejectRequest(
        @NotBlank(message = "Rejection reason is required.")
        @Size(max = 1000, message = "Rejection reason must be 1000 characters or fewer.")
        String reason) {

    public ContentReviewRejectRequest {
        reason = strip(reason);
    }
}