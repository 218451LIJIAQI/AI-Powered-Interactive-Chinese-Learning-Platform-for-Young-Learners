package com.example.aichinese.dto.admin.activity;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;
import static com.example.aichinese.dto.DtoNormalization.strippedStringListOrNull;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminStoryActivityRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,

        @Size(max = 1000, message = "Instructions must not exceed 1000 characters.")
        String instructions,

        @NotBlank(message = "Story text is required.")
        @Size(max = 200_000, message = "Story text is too long.")
        String storyText,

        @Size(max = 32_000, message = "Pinyin is too long.")
        String pinyin,

        @Size(max = 32_000, message = "Translation is too long.")
        String translation,

        @Pattern(
                regexp = "^(https?://\\S+)?$",
                message = "Image URL must start with http:// or https:// and contain no whitespace.")
        @Size(max = 255, message = "Image URL must not exceed 255 characters.")
        String imageUrl,

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
        Integer estimatedSeconds,

        @Valid
        @Size(max = 50, message = "A story activity cannot contain more than 50 questions.")
        List<@NotNull(message = "Story question must not be null.") AdminStoryQuestionRequest> questions) {

    public AdminStoryActivityRequest {
        title = strip(title);
        instructions = stripToNull(instructions);
        storyText = strip(storyText);
        pinyin = stripToNull(pinyin);
        translation = stripToNull(translation);
        imageUrl = stripToNull(imageUrl);
        topic = stripToNull(topic);
        tags = strippedStringListOrNull(tags);
        questions = immutableListOrEmpty(questions);
    }
}
