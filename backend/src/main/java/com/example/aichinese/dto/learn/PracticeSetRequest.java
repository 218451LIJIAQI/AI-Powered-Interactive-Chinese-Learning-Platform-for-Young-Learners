package com.example.aichinese.dto.learn;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;
import static com.example.aichinese.dto.DtoNormalization.strip;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.aichinese.entity.enums.LearningStage;

/**
 * An admin's new or edited Practice set. The type comes from the URL; each type checks its own items (see
 * {@link PracticeItemData}). A set's XP follows the type's rule (RewardRules.practiceSetXp), never the request.
 * {@code strokeCount} is for tracing, with optionally the strokes of each character ({@code characterStrokes}, one
 * number per character, adding up to {@code strokeCount}).
 */
public record PracticeSetRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,

        LearningStage stage,

        @Min(value = 1, message = "HSK level must be between 1 and 6.")
        @Max(value = 6, message = "HSK level must be between 1 and 6.")
        Integer hskLevel,

        @Size(max = 30, message = "A set has at most 30 items.")
        List<@NotNull(message = "Item is required.") @Valid PracticeItemData> items,

        @Min(value = 1, message = "Total strokes must be between 1 and 600.")
        @Max(value = 600, message = "Total strokes must be between 1 and 600.")
        Integer strokeCount,

        @Size(max = 10, message = "A tracing set has at most 10 characters.")
        List<@NotNull(message = "Strokes must be between 1 and 60.")
                @Min(value = 1, message = "Strokes must be between 1 and 60.")
                @Max(value = 60, message = "Strokes must be between 1 and 60.") Integer> characterStrokes) {

    public PracticeSetRequest {
        title = strip(title);
        items = immutableListOrEmpty(items);
        characterStrokes = immutableListOrEmpty(characterStrokes);
    }
}
