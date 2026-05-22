package com.example.aichinese.dto.admin.activity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One left/right pair inside an admin-managed matching game. Replayed as
 * part of the parent {@link AdminMatchingGameRequest} so the whole game can
 * be edited in a single transaction.
 */
public record AdminMatchingGameItemRequest(
        @NotBlank(message = "Left text is required.")
        @Size(max = 500, message = "Left text must not exceed 500 characters.")
        String leftText,
        @NotBlank(message = "Right text is required.")
        @Size(max = 500, message = "Right text must not exceed 500 characters.")
        String rightText,
        @Size(max = 255, message = "Left image URL must not exceed 255 characters.")
        String leftImageUrl,
        @Size(max = 255, message = "Right image URL must not exceed 255 characters.")
        String rightImageUrl,
        @Size(max = 255, message = "Pinyin must not exceed 255 characters.")
        String pinyin,
        @PositiveOrZero(message = "Sort order must be greater than or equal to 0.")
        Integer sortOrder) {
}
