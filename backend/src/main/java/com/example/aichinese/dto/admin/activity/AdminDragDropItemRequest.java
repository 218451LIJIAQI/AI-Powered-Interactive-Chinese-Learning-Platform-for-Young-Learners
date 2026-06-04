package com.example.aichinese.dto.admin.activity;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

        @Pattern(
                regexp = "^(https?://.+)?$",
                message = "Image URL must start with http:// or https://.")
        @Size(max = 255, message = "Image URL must not exceed 255 characters.")
        String imageUrl,

        @PositiveOrZero(message = "Sort order must be non-negative.")
        @Max(value = 10_000, message = "Sort order must not exceed 10000.")
        Integer sortOrder) {

    public AdminDragDropItemRequest {
        dragText = strip(dragText);
        targetZone = strip(targetZone);
        pinyin = stripToNull(pinyin);
        imageUrl = stripToNull(imageUrl);
    }
}
