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
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/admin/activities/drag-drop-activities}
 * (create) and
 * {@code PUT /api/v1/admin/activities/drag-drop-activities/{id}} (update).
 *
 * <p>Aggregate-root payload mirroring the matching-game pattern: a
 * single request encapsulates the activity header (title, instructions,
 * shared metadata) and its full set of {@link AdminDragDropItemRequest}
 * draggable cards. The {@link jakarta.validation.Valid} on
 * {@link #items} cascades Bean Validation into each card so a single
 * atomic 400 response surfaces every parent <em>and</em> child violation
 * — there is no partial-success path where the header would persist
 * while one item is rejected.</p>
 *
 * <p>Multiple items may share the same target zone (the front-end then
 * presents a multi-slot drop area). {@link #items} may be {@code null}
 * or empty during initial authoring; the publication gate refuses to
 * flip the status to
 * {@link com.example.aichinese.entity.enums.ContentStatus#PUBLISHED}
 * until at least one item exists. The upper {@code @Size(max=200)}
 * is a defensive bound preventing a single request from creating
 * unbounded child rows: realistic admin-authored activities carry well
 * under 30 cards, so 200 leaves substantial headroom while still
 * protecting the server from runaway payloads.</p>
 *
 * <p>{@link #instructions} maps onto the entity's
 * {@code instructions VARCHAR(1000)} column — the 1000-char cap is the
 * column ceiling, not a soft UX bound.</p>
 *
 * <p>See {@link AdminActivitySummaryResponse} for the shared metadata
 * vocabulary and {@link AdminFlashcardRequest} for the rationale
 * behind the tag-list cap and the {@code estimatedSeconds} upper bound.</p>
 *
 * @param title            activity title; required; max 200 chars
 * @param instructions     optional learner-facing instructions; max
 *                         1000 chars (DB column ceiling)
 * @param hskLevel         optional HSK band, {@code 1..6}
 * @param ageBand          optional target age cohort
 * @param difficulty       optional easy/medium/hard tier
 * @param topic            optional short topic label; max 100 chars
 * @param tags             optional list of free-form tags; up to 8
 *                         non-blank entries, each max 50 chars
 * @param xpReward         optional base XP, {@code 0..1000}
 * @param estimatedSeconds optional time-to-complete hint,
 *                         {@code 0..86400} (24 hours)
 * @param items            child draggable cards; nullable or empty
 *                         during drafting; at most 200 entries; each
 *                         element validated via {@link Valid}
 */
public record AdminDragDropActivityRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,

        @Size(max = 1000, message = "Instructions must not exceed 1000 characters.")
        String instructions,

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
        @Size(max = 200, message = "A drag-drop activity cannot contain more than 200 items.")
        List<@NotNull(message = "Drag-drop item must not be null.") AdminDragDropItemRequest> items) {

    public AdminDragDropActivityRequest {
        title = strip(title);
        instructions = stripToNull(instructions);
        topic = stripToNull(topic);
        tags = strippedStringListOrNull(tags);
        items = immutableListOrEmpty(items);
    }
}
