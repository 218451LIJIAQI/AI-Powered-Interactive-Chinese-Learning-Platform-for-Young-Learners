package com.example.aichinese.dto.admin.activity;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;
import com.example.aichinese.entity.enums.ContentStatus;

/**
 * Aggregate-root response payload returned by the admin matching-game
 * endpoints under {@code /api/v1/admin/activities/matching-games}.
 *
 * <p>Mirrors {@link AdminMatchingGameRequest} plus server-computed
 * audit metadata ({@link #id}, {@link #status},
 * {@link #createdByUserId}, {@link #createdByDisplayName},
 * {@link #createdAt}, {@link #updatedAt}). The embedded
 * {@link #items} list is emitted in canonical
 * {@code ORDER BY sort_order ASC, created_at ASC} so the admin
 * preview matches what the student sees pre-shuffle.</p>
 *
 * <p>The canonical-form constructor null-coalesces both {@link #tags}
 * and {@link #items} to empty lists and wraps non-{@code null} lists
 * in unmodifiable defensive copies. This matches the frontend types
 * ({@code tags: string[]}, {@code items: AdminMatchingGameItemResponse[]} —
 * neither expects {@code null}) and prevents downstream callers from
 * mutating a cached response.</p>
 *
 * <p>See {@link AdminActivitySummaryResponse} for the shared metadata
 * vocabulary contract.</p>
 */
public record AdminMatchingGameResponse(
        Long id,
        String title,
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
        LocalDateTime updatedAt,
        List<AdminMatchingGameItemResponse> items) {

    public AdminMatchingGameResponse {
        tags = tags == null ? List.of() : List.copyOf(tags);
        items = items == null ? List.of() : List.copyOf(items);
    }
}
