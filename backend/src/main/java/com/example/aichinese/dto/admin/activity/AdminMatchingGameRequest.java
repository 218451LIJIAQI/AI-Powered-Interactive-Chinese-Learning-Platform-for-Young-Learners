package com.example.aichinese.dto.admin.activity;

import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/admin/activities/matching-games}
 * (create) and {@code PUT /api/v1/admin/activities/matching-games/{id}}
 * (update).
 *
 * <p>Aggregate-root payload: a single request encapsulates both the
 * matching-game header (title, metadata) and its full set of
 * {@link AdminMatchingGameItemRequest} child pairs. The
 * {@link jakarta.validation.Valid} on {@link #items} cascades Bean
 * Validation into each pair so the whole structure is rejected
 * atomically when any child fails its constraints — the admin never
 * sees a "header saved, items rejected" partial-failure state.</p>
 *
 * <p>{@link #items} may be {@code null} or empty during initial
 * authoring (admin saves the header first, then adds pairs); the
 * student-facing publication gate ({@code AdminActivityService}'s
 * status-change validator) refuses to flip a game to
 * {@link com.example.aichinese.entity.enums.ContentStatus#PUBLISHED}
 * until at least two pairs exist. The upper {@code @Size(max=200)}
 * is a defensive bound preventing a single request from creating
 * unbounded child rows: realistic admin-authored games carry under 20
 * pairs, so 200 is more than ample while still protecting the server
 * from runaway payloads.</p>
 *
 * <p>See {@link AdminActivitySummaryResponse} for the shared metadata
 * vocabulary and {@link AdminFlashcardRequest} for the rationale
 * behind the tag-list cap and the {@code estimatedSeconds} upper bound.</p>
 *
 * @param title            game title; required; max 200 chars
 * @param hskLevel         optional HSK band, {@code 1..6}
 * @param ageBand          optional target age cohort
 * @param difficulty       optional easy/medium/hard tier
 * @param topic            optional short topic label; max 100 chars
 * @param tags             optional list of free-form tags; up to 8
 *                         non-blank entries, each max 50 chars
 * @param xpReward         optional base XP, {@code 0..1000}
 * @param estimatedSeconds optional time-to-complete hint, {@code 0..86400}
 * @param items            child left/right pairs; nullable or empty
 *                         during drafting; at most 200 entries; each
 *                         element validated via {@link Valid}
 */
public record AdminMatchingGameRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,

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
        @Size(max = 200, message = "A matching game cannot contain more than 200 pairs.")
        List<AdminMatchingGameItemRequest> items) {
}
