package com.example.aichinese.dto.admin.activity;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;
import com.example.aichinese.entity.enums.ContentStatus;

/**
 * Single row in the admin "all activities" cross-type browser returned
 * by {@code GET /api/v1/admin/activities}.
 *
 * <p>The browser unifies {@link ActivityType#FLASHCARD},
 * {@code MATCHING_GAME}, {@code LISTENING_EXERCISE},
 * {@code TRACING_EXERCISE}, {@code DRAG_DROP_ACTIVITY} and
 * {@code STORY_ACTIVITY} behind a single paginated, filterable list so
 * admins can audit the entire learning library from one screen
 * instead of opening six tabs. {@link #type} is the per-row
 * discriminator that tells the front-end which detail endpoint to call
 * when the admin clicks through.</p>
 *
 * <p>This is a deliberately compact <em>summary</em> — full type-specific
 * detail (front/back text, audio URL, questions, items, etc.) lives in
 * the dedicated detail responses ({@link AdminFlashcardResponse},
 * {@link AdminMatchingGameResponse}, etc.). Surfacing only the shared
 * metadata vocabulary keeps the cross-type list payload predictable
 * and small.</p>
 *
 * <h2>Shared metadata vocabulary</h2>
 * <p>Every activity request and response in this package shares the
 * same six metadata fields so the admin filter UI and the student
 * library filter UI are uniform across artefact types:</p>
 * <ul>
 *   <li>{@link #hskLevel} — HSK 1-6 difficulty band; {@code null} when
 *       unset.</li>
 *   <li>{@link #ageBand} — target age cohort.</li>
 *   <li>{@link #difficulty} — easy/medium/hard tier driving XP
 *       multiplier.</li>
 *   <li>{@link #topic} / {@link #tags} — free-form discoverability.</li>
 *   <li>{@link #xpReward} — base XP awarded on completion.</li>
 *   <li>{@link #estimatedSeconds} — UX hint for "time to complete".</li>
 * </ul>
 *
 * <p>{@link #createdByUserId} / {@link #createdByDisplayName} attribute
 * authorship; the admin "Edited by" column reads from
 * {@link #updatedAt} (set by the {@code BaseEntity} {@code @PreUpdate}
 * hook).</p>
 *
 * <h2>Title fallback</h2>
 * <p>For {@link ActivityType#FLASHCARD} rows the underlying entity's
 * {@code title} is optional; the mapper falls back to the card's
 * {@code frontText} so {@link #title} on this DTO is always
 * non-{@code null} and non-blank for healthy data. For all other
 * activity types {@code title} is a required entity column and is
 * therefore always populated.</p>
 *
 * <h2>Sort order</h2>
 * <p>The cross-type listing is emitted ordered by {@link #updatedAt}
 * descending (with stable {@code nulls last}) so admins see the most
 * recently edited content at the top — see
 * {@code AdminActivityService.listAllActivities}.</p>
 *
 * @param id                    underlying activity row id
 * @param type                  table discriminator for click-through
 *                              navigation
 * @param title                 human-readable activity title (falls back
 *                              to front-text for flashcards; see above)
 * @param status                editorial lifecycle state
 * @param hskLevel              HSK 1-6 band; nullable
 * @param ageBand               target age cohort; nullable
 * @param difficulty            easy/medium/hard tier; nullable
 * @param topic                 free-form short topic label; nullable
 * @param tags                  free-form tags; never {@code null}
 *                              (empty list when none); the wrapped list
 *                              is unmodifiable
 * @param xpReward              base XP on completion
 * @param estimatedSeconds      UX hint; nullable
 * @param createdByUserId       admin who authored the row; nullable
 *                              for legacy/seed data
 * @param createdByDisplayName  display name for the author user id
 * @param createdAt             creation timestamp
 * @param updatedAt             last-modified timestamp
 */
public record AdminActivitySummaryResponse(
        Long id,
        ActivityType type,
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
        LocalDateTime updatedAt) {

    /**
     * Canonical-form constructor that hardens the {@link #tags} contract:
     * a {@code null} list is normalised to an empty list (matching the
     * frontend type {@code tags: string[]} which never expects
     * {@code null}), and a non-{@code null} list is wrapped in an
     * unmodifiable defensive copy via {@link List#copyOf(java.util.Collection)}
     * so a downstream caller cannot mutate the cached response and
     * accidentally leak edits across requests. {@code List.copyOf}
     * additionally fails fast with {@link NullPointerException} on
     * {@code null} elements — an intentional invariant given that the
     * mapper layer already filters them out.
     */
    public AdminActivitySummaryResponse {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
