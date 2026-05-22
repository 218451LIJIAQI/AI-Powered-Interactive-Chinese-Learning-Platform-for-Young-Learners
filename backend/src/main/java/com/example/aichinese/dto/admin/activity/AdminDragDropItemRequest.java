package com.example.aichinese.dto.admin.activity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdminDragDropItemRequest(
        @NotBlank(message = "Drag text is required.")
        @Size(max = 500, message = "Drag text must not exceed 500 characters.")
        String dragText,
        @NotBlank(message = "Target zone is required.")
        @Size(max = 200, message = "Target zone must not exceed 200 characters.")
        String targetZone,
        @Size(max = 255, message = "Pinyin must not exceed 255 characters.")
        String pinyin,
        @Size(max = 255, message = "Image URL must not exceed 255 characters.")
        String imageUrl,
        @PositiveOrZero(message = "Sort order must be greater than or equal to 0.")
        Integer sortOrder) {
}
