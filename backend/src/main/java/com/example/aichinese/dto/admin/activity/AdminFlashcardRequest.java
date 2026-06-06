package com.example.aichinese.dto.admin.activity;

import static com.example.aichinese.dto.DtoNormalization.strippedStringListOrNull;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdminFlashcardRequest(
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,

        @NotBlank(message = "Front text is required.")
        @Size(max = 500, message = "Front text must not exceed 500 characters.")
        String frontText,

        @NotBlank(message = "Back text is required.")
        @Size(max = 1000, message = "Back text must not exceed 1000 characters.")
        String backText,

        @Size(max = 255, message = "Pinyin must not exceed 255 characters.")
        String pinyin,

        @Size(max = 500, message = "English meaning must not exceed 500 characters.")
        String englishMeaning,

        @Size(max = 500, message = "Bahasa Melayu meaning must not exceed 500 characters.")
        String malayMeaning,

        @Pattern(
                regexp = AdminFlashcardRequest.HTTP_URL_PATTERN,
                message = "Image URL must start with http:// or https:// and contain no whitespace.")
        @Size(max = 255, message = "Image URL must not exceed 255 characters.")
        String imageUrl,

        @Pattern(
                regexp = AdminFlashcardRequest.HTTP_URL_PATTERN,
                message = "Audio URL must start with http:// or https:// and contain no whitespace.")
        @Size(max = 255, message = "Audio URL must not exceed 255 characters.")
        String audioUrl,

        @PositiveOrZero(message = "Sort order must be non-negative.")
        @Max(value = 10_000, message = "Sort order must not exceed 10000.")
        Integer sortOrder,

        @Size(max = 200_000, message = "Extra payload is too large.")
        String extraPayload,

        @Min(value = 1, message = "HSK level must be between 1 and 6.")
        @Max(value = 6, message = "HSK level must be between 1 and 6.")
        Integer hskLevel,

        AgeBand ageBand,

        ActivityDifficulty difficulty,

        @Size(max = 100, message = "Topic must not exceed 100 characters.")
        String topic,

        @Size(max = 8, message = "At most 8 tags are allowed.")
        List<
                @NotBlank(message = "Tag must not be blank.")
                @Size(max = 50, message = "Each tag must not exceed 50 characters.")
                String> tags,

        @Min(value = 0, message = "XP reward must be non-negative.")
        @Max(value = 1000, message = "XP reward must not exceed 1000.")
        Integer xpReward,

        @Min(value = 0, message = "Estimated seconds must be non-negative.")
        @Max(value = 86_400, message = "Estimated seconds must not exceed 86400 (24 hours).")
        Integer estimatedSeconds) {

    public AdminFlashcardRequest {
        title = stripToNull(title);
        frontText = strip(frontText);
        backText = strip(backText);
        pinyin = stripToNull(pinyin);
        englishMeaning = stripToNull(englishMeaning);
        malayMeaning = stripToNull(malayMeaning);
        imageUrl = stripToNull(imageUrl);
        audioUrl = stripToNull(audioUrl);
        extraPayload = stripToNull(extraPayload);
        topic = stripToNull(topic);
        tags = strippedStringListOrNull(tags);
    }

    static final String HTTP_URL_PATTERN = "^(https?://\\S+)?$";
}
