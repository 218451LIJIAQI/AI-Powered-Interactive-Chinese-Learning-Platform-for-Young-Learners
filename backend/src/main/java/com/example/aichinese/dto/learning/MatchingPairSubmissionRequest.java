package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotNull;

public record MatchingPairSubmissionRequest(
        @NotNull(message = "Matching item id is required.")
        Long itemId,
        @NotNull(message = "Submitted right text is required.")
        String submittedRightText) {
}
