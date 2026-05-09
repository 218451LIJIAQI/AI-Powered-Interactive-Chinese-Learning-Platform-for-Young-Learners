package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateFlashcardRequest(
        @NotBlank(message = "Chinese text is required.")
        @Size(max = 500, message = "Chinese text must not exceed 500 characters.")
        String chineseText,
        @Size(max = 255, message = "Pinyin must not exceed 255 characters.")
        String pinyin,
        @NotBlank(message = "English meaning is required.")
        @Size(max = 500, message = "English meaning must not exceed 500 characters.")
        String englishMeaning,
        @Size(max = 500, message = "Bahasa Melayu meaning must not exceed 500 characters.")
        String malayMeaning,
        @Size(max = 255, message = "Image reference must not exceed 255 characters.")
        String imageUrl,
        @Size(max = 255, message = "Audio reference must not exceed 255 characters.")
        String audioUrl,
        @Size(max = 2000, message = "Extra payload must not exceed 2000 characters.")
        String extraPayload,
        @PositiveOrZero(message = "Display order must be greater than or equal to 0.")
        Integer displayOrder) {
}
