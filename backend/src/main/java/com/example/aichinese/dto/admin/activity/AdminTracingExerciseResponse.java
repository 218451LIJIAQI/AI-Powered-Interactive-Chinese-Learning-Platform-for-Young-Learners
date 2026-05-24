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
 * <p>The canonical-form constructor null-coalesces {@link #tags} to an
 * empty list and wraps the supplied list in an unmodifiable defensive
 * copy, matching the frontend type {@code tags: string[]} (never
 * {@code null}) and protecting against accidental downstream mutation.</p>
 *
 * <p>See {@link AdminActivitySummaryResponse} for the shared metadata
 * vocabulary contract.</p>
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
