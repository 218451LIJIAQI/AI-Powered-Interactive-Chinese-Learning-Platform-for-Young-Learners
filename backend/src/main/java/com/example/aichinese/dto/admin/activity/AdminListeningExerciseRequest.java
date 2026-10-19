package com.example.aichinese.dto.admin.activity;

import static com.example.aichinese.dto.DtoNormalization.strippedStringListOrNull;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.example.aichinese.entity.enums.LearningStage;

public record AdminListeningExerciseRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,

        @Pattern(
                regexp = "^(https?://\\S+)?$",
                message = "Audio URL must start with http:// or https:// and contain no whitespace.")
        @Size(max = 255, message = "Audio URL must not exceed 255 characters.")
        String audioUrl,

        @Size(max = 200_000, message = "Question payload is too large.")
        String questionPayload,

        @Min(value = 1, message = "HSK level must be between 1 and 6.")
        @Max(value = 6, message = "HSK level must be between 1 and 6.")
        Integer hskLevel,

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
        LearningStage learningStage) {

    public AdminListeningExerciseRequest {
        title = strip(title);
        audioUrl = stripToNull(audioUrl);
        questionPayload = stripToNull(questionPayload);
        topic = stripToNull(topic);
        tags = strippedStringListOrNull(tags);
    }
}
