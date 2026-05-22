package com.example.aichinese.dto.admin.activity;

import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminStoryActivityRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,
        @Size(max = 1000, message = "Instructions must not exceed 1000 characters.")
        String instructions,
        @NotBlank(message = "Story text is required.")
        @Size(max = 65535, message = "Story text is too long.")
        String storyText,
        @Size(max = 65535, message = "Pinyin is too long.")
        String pinyin,
        @Size(max = 65535, message = "Translation is too long.")
        String translation,
        @Size(max = 255, message = "Image URL must not exceed 255 characters.")
        String imageUrl,
        @Min(value = 1, message = "HSK level must be between 1 and 6.")
        @Max(value = 6, message = "HSK level must be between 1 and 6.")
        Integer hskLevel,
        AgeBand ageBand,
        ActivityDifficulty difficulty,
        @Size(max = 100, message = "Topic must not exceed 100 characters.")
        String topic,
        List<@Size(max = 50, message = "Each tag must not exceed 50 characters.") String> tags,
        @Min(value = 0, message = "XP reward must be non-negative.")
        @Max(value = 1000, message = "XP reward must not exceed 1000.")
        Integer xpReward,
        @Min(value = 0, message = "Estimated seconds must be non-negative.")
        Integer estimatedSeconds,
        @Valid
        List<AdminStoryQuestionRequest> questions) {
}
