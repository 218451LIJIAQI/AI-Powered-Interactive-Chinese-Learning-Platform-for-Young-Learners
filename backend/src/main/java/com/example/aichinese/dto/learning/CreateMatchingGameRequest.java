package com.example.aichinese.dto.learning;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateMatchingGameRequest(
        @NotBlank(message = "Matching game title is required.")
        @Size(max = 200, message = "Matching game title must not exceed 200 characters.")
        String title,
        @Valid
        @NotEmpty(message = "At least one matching pair is required.")
        List<MatchingGameItemRequest> items) {
}
