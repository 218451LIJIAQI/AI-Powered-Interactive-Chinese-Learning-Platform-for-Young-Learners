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
 * <h2>Field origin</h2>
 * <p>The first eleven components ({@link #title}…{@link #extraPayload})
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
 * vocabulary that this DTO conforms to.</p>
 *
 * @param id                    database-assigned primary key
 * @param title                 optional deck/card title
 * @param frontText             at-a-glance card prompt
 * @param backText              full explanation
 * @param pinyin                optional pinyin scaffold
 * @param englishMeaning        optional English gloss
 * @param malayMeaning          optional Bahasa Melayu gloss
 * @param imageUrl              optional image URL
 * @param audioUrl              optional audio URL
 * @param sortOrder             authoring order
 * @param extraPayload          optional opaque JSON
 * @param status                editorial lifecycle state
 * @param hskLevel              HSK 1-6 band; nullable
 * @param ageBand               target age cohort; nullable
 * @param difficulty            easy/medium/hard tier; nullable
 * @param topic                 free-form short topic label; nullable
 * @param tags                  free-form tags; never {@code null}
 *                              (empty list when none); unmodifiable
 * @param xpReward              base XP on completion
 * @param estimatedSeconds      UX hint; nullable
 * @param createdByUserId       admin who authored the card; nullable
 *                              for legacy/seed data
 * @param createdByDisplayName  display name for the author user id
 * @param createdAt             creation timestamp
 * @param updatedAt             last-modified timestamp
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
