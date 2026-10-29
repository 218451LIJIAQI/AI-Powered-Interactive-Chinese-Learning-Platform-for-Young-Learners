package com.example.aichinese.dto.learn;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.example.aichinese.entity.enums.LearningStage;

/**
 * An admin's new or edited Practice set. The type comes from the URL; each type checks its own items (see
 * {@link PracticeItemData}). {@code storyLines} is for stories, {@code strokeCount} for tracing.
 */
public record PracticeSetRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,

        LearningStage stage,

        @Min(value = 1, message = "HSK level must be between 1 and 6.")
        @Max(value = 6, message = "HSK level must be between 1 and 6.")
        Integer hskLevel,

        @Min(value = 0, message = "XP must be between 0 and 100.")
        @Max(value = 100, message = "XP must be between 0 and 100.")
        Integer xpReward,

        @Size(max = 30, message = "A set has at most 30 items.")
        List<@Valid PracticeItemData> items,

        @Size(max = 20, message = "A story has at most 20 lines.")
        List<@Valid StoryLine> storyLines,

        @Min(value = 1, message = "Strokes must be between 1 and 60.")
        @Max(value = 60, message = "Strokes must be between 1 and 60.")
        Integer strokeCount) {

    public PracticeSetRequest {
        title = title == null ? null : title.strip();
        items = items == null ? List.of() : List.copyOf(items);
        storyLines = storyLines == null ? List.of() : List.copyOf(storyLines);
    }
}
