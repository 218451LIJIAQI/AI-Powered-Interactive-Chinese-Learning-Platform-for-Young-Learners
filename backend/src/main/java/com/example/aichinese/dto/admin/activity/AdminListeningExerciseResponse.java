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
 * <p>The canonical-form constructor null-coalesces {@link #tags} to an
 * empty list and wraps the supplied list in an unmodifiable defensive
 * copy, matching the frontend type {@code tags: string[]} (never
 * {@code null}) and protecting against accidental downstream mutation.</p>
 *
 * <p>See {@link AdminActivitySummaryResponse} for the shared metadata
 * vocabulary contract.</p>
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
