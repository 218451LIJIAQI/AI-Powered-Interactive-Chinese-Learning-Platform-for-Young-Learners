package com.example.aichinese.dto.admin.activity;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;
import com.example.aichinese.entity.enums.ContentStatus;

/**
 * Full detail payload returned by the admin tracing-exercise endpoints
 * under {@code /api/v1/admin/activities/tracing-exercises}.
 *
 * <p>Mirrors {@link AdminTracingExerciseRequest} field-for-field plus
 * the server-computed audit metadata
 * ({@link #id}, {@link #status}, {@link #createdByUserId},
 * {@link #createdByDisplayName}, {@link #createdAt},
 * {@link #updatedAt}).</p>
 *
 * <h2>Field origin</h2>
 * <p>The first six components ({@link #title}…{@link #instructions})
 * mirror the request payload verbatim. The remaining components are
 * server-computed and are never accepted from client input.</p>
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
 * @param worksheetUrl          optional SVG/PNG stroke-guide URL
 * @param targetCharacter       optional Chinese character being traced
 * @param pinyin                optional pinyin scaffold
 * @param meaning               optional gloss
 * @param instructions          optional learner-facing instructions
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
public record AdminTracingExerciseResponse(
        Long id,
        String title,
        String worksheetUrl,
        String targetCharacter,
        String pinyin,
        String meaning,
        String instructions,
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

    public AdminTracingExerciseResponse {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
