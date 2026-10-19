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

public record AdminTracingExerciseRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,

        @Pattern(
                regexp = "^(https?://\\S+)?$",
                message = "Worksheet URL must start with http:// or https:// and contain no whitespace.")
        @Size(max = 255, message = "Worksheet URL must not exceed 255 characters.")
        String worksheetUrl,

        @Size(max = 100, message = "Target character must not exceed 100 characters.")
        String targetCharacter,

        @Size(max = 255, message = "Pinyin must not exceed 255 characters.")
        String pinyin,

        @Size(max = 500, message = "Meaning must not exceed 500 characters.")
        String meaning,

        @Size(max = 1000, message = "Instructions must not exceed 1000 characters.")
        String instructions,

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

    public AdminTracingExerciseRequest {
        title = strip(title);
        worksheetUrl = stripToNull(worksheetUrl);
        targetCharacter = stripToNull(targetCharacter);
        pinyin = stripToNull(pinyin);
        meaning = stripToNull(meaning);
        instructions = stripToNull(instructions);
        topic = stripToNull(topic);
        tags = strippedStringListOrNull(tags);
    }
}
