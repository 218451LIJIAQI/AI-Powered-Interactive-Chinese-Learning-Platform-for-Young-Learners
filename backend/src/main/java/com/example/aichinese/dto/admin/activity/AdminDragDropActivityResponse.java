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
 * emitted in {@code ORDER BY sort_order ASC, created_at ASC} (see
 * {@code DragDropItemRepository}) so admin and student renderers see
 * the same canonical ordering before optional shuffling.</p>
 *
 * <h2>Field origin</h2>
 * <p>The first three sections come straight from the matching
 * {@link AdminDragDropActivityRequest} fields; the audit block
 * ({@link #id}, {@link #status}, {@link #createdByUserId},
 * {@link #createdByDisplayName}, {@link #createdAt},
 * {@link #updatedAt}) is server-computed and is never accepted from
 * client input.</p>
 *
 * <p>The canonical-form constructor null-coalesces both {@link #tags}
 * and {@link #items} to empty lists and wraps non-{@code null} lists
 * in unmodifiable defensive copies via
 * {@link List#copyOf(java.util.Collection)}. This matches the
 * frontend types ({@code tags: string[]},
 * {@code items: AdminDragDropItemResponse[]} — neither expects
 * {@code null}) and prevents downstream callers from mutating a cached
 * response. {@code List.copyOf} additionally fails fast with
 * {@link NullPointerException} on {@code null} elements — an
 * intentional invariant given that the mapper only emits well-formed
 * children.</p>
 *
 * <p>See {@link AdminActivitySummaryResponse} for the shared metadata
 * vocabulary contract.</p>
 *
 * @param id                    database-assigned primary key
 * @param title                 activity title
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
 * @param items                 child draggable cards in canonical sort
 *                              order; never {@code null}; unmodifiable
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
