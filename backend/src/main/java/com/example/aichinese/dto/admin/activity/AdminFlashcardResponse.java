package com.example.aichinese.dto.admin.activity;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;
import com.example.aichinese.entity.enums.ContentStatus;

/**
 * Full detail payload returned by the admin flashcard endpoints
 * ({@code GET}, {@code POST}, {@code PUT} under
 * {@code /api/v1/admin/activities/flashcards}).
 *
 * <p>Mirrors {@link AdminFlashcardRequest} field-for-field, plus the
 * audit metadata that only the server can compute:</p>
 * <ul>
 *   <li>{@link #id} — assigned by the database on insert.</li>
 *   <li>{@link #status} — current
 *       {@link com.example.aichinese.entity.enums.ContentStatus}; only
 *       {@code PUBLISHED} cards reach the student library.</li>
 *   <li>{@link #createdByUserId} / {@link #createdByDisplayName} — the
 *       admin who authored the card; the display name is denormalised
 *       so the UI does not need a follow-up user-lookup call.</li>
 *   <li>{@link #createdAt} / {@link #updatedAt} — managed by the
 *       {@code BaseEntity} lifecycle hooks.</li>
 * </ul>
 *
 * <p>The canonical-form constructor null-coalesces {@link #tags} to an
 * empty list and wraps the supplied list in an unmodifiable defensive
 * copy, matching the frontend type {@code tags: string[]} (never
 * {@code null}) and protecting against accidental downstream mutation.</p>
 *
 * <p>See {@link AdminActivitySummaryResponse} for the shared metadata
 * vocabulary that this DTO conforms to.</p>
 */
public record AdminFlashcardResponse(
        Long id,
        String title,
        String frontText,
        String backText,
        String pinyin,
        String englishMeaning,
        String malayMeaning,
        String imageUrl,
        String audioUrl,
        Integer sortOrder,
        String extraPayload,
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

    public AdminFlashcardResponse {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
