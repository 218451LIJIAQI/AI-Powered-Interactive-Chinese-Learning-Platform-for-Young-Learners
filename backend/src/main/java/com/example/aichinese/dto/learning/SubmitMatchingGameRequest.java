package com.example.aichinese.dto.learning;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record SubmitMatchingGameRequest(
        @Valid
        @NotEmpty(message = "At least one submitted pair is required.")
        List<MatchingPairSubmissionRequest> pairs) {
}
