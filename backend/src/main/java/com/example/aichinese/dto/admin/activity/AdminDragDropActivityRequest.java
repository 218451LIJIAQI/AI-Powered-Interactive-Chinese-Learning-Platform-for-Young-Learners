package com.example.aichinese.dto.admin.activity;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;
import static com.example.aichinese.dto.DtoNormalization.strippedStringListOrNull;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.example.aichinese.entity.enums.LearningStage;

public record AdminDragDropActivityRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,

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

        @Valid
        @Size(max = 200, message = "A drag-drop activity cannot contain more than 200 items.")
        List<@NotNull(message = "Drag-drop item must not be null.") AdminDragDropItemRequest> items,
        LearningStage learningStage) {

    public AdminDragDropActivityRequest {
        title = strip(title);
        instructions = stripToNull(instructions);
        topic = stripToNull(topic);
        tags = strippedStringListOrNull(tags);
        items = immutableListOrEmpty(items);
    }
}
