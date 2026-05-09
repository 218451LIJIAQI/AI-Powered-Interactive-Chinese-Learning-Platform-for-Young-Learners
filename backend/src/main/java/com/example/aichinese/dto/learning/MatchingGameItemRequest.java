package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record MatchingGameItemRequest(
        @NotBlank(message = "Left text is required.")
        @Size(max = 500, message = "Left text must not exceed 500 characters.")
        String leftText,
        @NotBlank(message = "Right text is required.")
        @Size(max = 500, message = "Right text must not exceed 500 characters.")
        String rightText,
        @Size(max = 255, message = "Left image reference must not exceed 255 characters.")
        String leftImageUrl,
        @Size(max = 255, message = "Right image reference must not exceed 255 characters.")
        String rightImageUrl,
        @Size(max = 255, message = "Pinyin must not exceed 255 characters.")
        String pinyin,
        @PositiveOrZero(message = "Display order must be greater than or equal to 0.")
        Integer displayOrder) {
}
