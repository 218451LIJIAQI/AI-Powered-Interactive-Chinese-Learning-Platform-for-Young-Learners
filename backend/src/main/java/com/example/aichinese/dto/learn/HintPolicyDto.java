package com.example.aichinese.dto.learn;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrNull;
import static com.example.aichinese.dto.DtoNormalization.strip;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The hint rules per level, as the admin edits them on AI Settings (A5.05). A hint is one step; how much it says follows
 * the level and is not an admin setting ({@code HintPolicyService}).
 */
public final class HintPolicyDto {

    private HintPolicyDto() {
    }

    /**
     * One level's rule.
     *
     * @param tier {@code HSK1}, {@code HSK2} or {@code HSK3}
     * @param delaySeconds how long a student is stuck before a hint is offered
     * @param perSet hints a set may use; 0 means every question may have one
     */
    @Schema(name = "HintPolicyLevel")
    public record Level(
            @NotBlank(message = "Level is required.")
            @Size(max = 20, message = "Level is required.")
            String tier,
            @NotNull(message = "Offered after is required.")
            @Min(value = 5, message = "Offer a hint after 5 to 120 seconds.")
            @Max(value = 120, message = "Offer a hint after 5 to 120 seconds.")
            Integer delaySeconds,
            @NotNull(message = "Hints per set is required.")
            @Min(value = 0, message = "Hints per set is 0 (every question) to 10.")
            @Max(value = 10, message = "Hints per set is 0 (every question) to 10.")
            Integer perSet) {

        public Level {
            tier = strip(tier);
        }
    }

    public record Response(List<Level> levels) {
    }

    public record UpdateRequest(
            @NotNull(message = "Add a rule for each level.")
            @Size(min = 3, max = 3, message = "Add a rule for each level.")
            List<@NotNull(message = "Add a rule for each level.") @Valid Level> levels) {

        public UpdateRequest {
            levels = immutableListOrNull(levels);
        }
    }
}
