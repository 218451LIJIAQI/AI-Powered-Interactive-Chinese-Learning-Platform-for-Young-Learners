package com.example.aichinese.dto.admin.activity;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;
import com.example.aichinese.entity.enums.ContentStatus;

/**
 * Aggregate-root response payload returned by the admin drag-drop
 * activity endpoints under
 * {@code /api/v1/admin/activities/drag-drop-activities}.
 *
 * <p>Mirrors {@link AdminDragDropActivityRequest} field-for-field plus
 * server-computed audit metadata. The embedded {@link #items} list is
 * emitted in {@code ORDER BY sort_order ASC, created_at ASC}.</p>
 *
 * <p>The canonical-form constructor null-coalesces both {@link #tags}
 * and {@link #items} to empty lists and wraps non-{@code null} lists
 * in unmodifiable defensive copies. This matches the frontend types
 * ({@code tags: string[]}, {@code items: AdminDragDropItemResponse[]} —
 * neither expects {@code null}) and prevents downstream callers from
 * mutating a cached response.</p>
 *
 * <p>See {@link AdminActivitySummaryResponse} for the shared metadata
 * vocabulary contract.</p>
 */
public record AdminDragDropActivityResponse(
        Long id,
        String title,
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
        LocalDateTime updatedAt,
        List<AdminDragDropItemResponse> items) {

    public AdminDragDropActivityResponse {
        tags = tags == null ? List.of() : List.copyOf(tags);
        items = items == null ? List.of() : List.copyOf(items);
    }
}
