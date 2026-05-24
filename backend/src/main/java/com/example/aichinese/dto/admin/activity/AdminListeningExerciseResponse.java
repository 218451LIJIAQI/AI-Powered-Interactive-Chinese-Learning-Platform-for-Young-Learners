package com.example.aichinese.dto.admin.activity;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;
import com.example.aichinese.entity.enums.ContentStatus;

/**
 * Full detail payload returned by the admin listening-exercise
 * endpoints under {@code /api/v1/admin/activities/listening-exercises}.
 *
 * <p>Mirrors {@link AdminListeningExerciseRequest} field-for-field
 * plus server-computed audit metadata. {@link #questionPayload} is
 * emitted verbatim — the admin UI re-parses it client-side to render
 * the prompts editor.</p>
 *
 * <h2>Field origin</h2>
 * <p>{@link #title}, {@link #audioUrl} and {@link #questionPayload}
 * mirror the request body. The remaining components ({@link #id},
 * {@link #status}, {@link #createdByUserId},
 * {@link #createdByDisplayName}, {@link #createdAt},
 * {@link #updatedAt}) are server-computed and are never accepted from
 * client input.</p>
 *
 * <p>The canonical-form constructor null-coalesces {@link #tags} to an
 * empty list and wraps the supplied list in an unmodifiable defensive
 * copy via {@link List#copyOf(java.util.Collection)}, matching the
 * frontend type {@code tags: string[]} (never {@code null}) and
 * protecting against accidental downstream mutation.
 * {@code List.copyOf} additionally fails fast with
 * {@link NullPointerException} on {@code null} elements — an
 * intentional invariant given that the mapper layer already filters
 * them out via {@code AdminActivityMapper.splitTags}.</p>
 *
 * <p>See {@link AdminActivitySummaryResponse} for the shared metadata
 * vocabulary contract.</p>
 *
 * @param id                    database-assigned primary key
 * @param title                 exercise title
 * @param audioUrl              optional audio URL
 * @param questionPayload       opaque JSON describing prompts and
 *                              answers; nullable during drafting
 * @param status                editorial lifecycle state
 * @param hskLevel              HSK 1-6 band; nullable
 * @param ageBand               target age cohort; nullable
 * @param difficulty            easy/medium/hard tier; nullable
 * @param topic                 free-form short topic label; nullable
 * @param tags                  free-form tags; never {@code null}
 *                              (empty list when none); unmodifiable
 * @param xpReward              base XP on completion
 * @param estimatedSeconds      UX hint; nullable
 * @param createdByUserId       admin who authored the row; nullable
 *                              for legacy/seed data
 * @param createdByDisplayName  display name for the author user id
 * @param createdAt             creation timestamp
 * @param updatedAt             last-modified timestamp
 */
public record AdminListeningExerciseResponse(
        Long id,
        String title,
        String audioUrl,
        String questionPayload,
        ContentStatus status,
        Integer hskLevel,
        AgeBand ageBand,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        Long createdByUserId,
        String createdByDisplayName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public AdminListeningExerciseResponse {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
