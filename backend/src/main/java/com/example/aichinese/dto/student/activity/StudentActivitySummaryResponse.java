package com.example.aichinese.dto.student.activity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.example.aichinese.dto.admin.activity.ActivityType;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;

/**
 * Single row of the student-facing activity library feed returned by
 * {@code GET /api/v1/students/activities}.
 *
 * <p>The feed unifies all six platform-activity tables behind one
 * paginated, filterable list (filtered server-side by HSK level, age
 * band, difficulty, topic, tag, free-text keyword, and an optional
 * {@code type} filter to scope to a single {@link ActivityType}). The
 * front-end uses {@link #type} to dispatch to the appropriate detail
 * endpoint when the student opens the card.</p>
 *
 * <h2>Why this is slimmer than the admin counterpart</h2>
 * <p>This DTO is the student-side cousin of
 * {@code AdminActivitySummaryResponse}. It deliberately omits the
 * admin-only audit metadata ({@code createdByUserId},
 * {@code createdByDisplayName}, {@code createdAt}, {@code status}):</p>
 * <ul>
 *   <li>Status is implicit — only
 *       {@link com.example.aichinese.entity.enums.ContentStatus#PUBLISHED}
 *       activities are ever surfaced through the student feed.</li>
 *   <li>Authorship is irrelevant to learners and is stripped to
 *       reduce payload size and avoid leaking staff identity.</li>
 *   <li>{@link #updatedAt} is kept because the feed is sorted by
 *       freshness ({@code ORDER BY updated_at DESC}) and clients
 *       display "Updated 2 days ago" labels.</li>
 * </ul>
 *
 * <h2>{@link #completed}</h2>
 * <p>Pre-computed by the server from the student's progress log so
 * the feed can render a "Done" badge without the client making N
 * follow-up requests. The lookup is keyed on
 * {@code (student_id, activity_source, activity_id)} via
 * {@code StudentLearningProgressService.isCompleted}. Primitive
 * {@code boolean} (not {@link Boolean}) so the JSON wire format
 * always emits {@code true} or {@code false}.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces the wire-format contract so a
 * malformed feed row never reaches the client:</p>
 * <ul>
 *   <li>{@link #id}, {@link #type}, {@link #title} and
 *       {@link #updatedAt} are required — they drive click-through
 *       navigation, freshness ordering and the card heading.
 *       {@code title} is guaranteed non-{@code null} by the mapper's
 *       {@code preferredTitle} fallback (frontText for flashcards,
 *       {@code "(untitled)"} as a final defensive default).</li>
 *   <li>{@link #tags} is null-coalesced to {@link List#of()} and
 *       wrapped in {@link List#copyOf(java.util.Collection)} so the
 *       wire format is always {@code []} (matching the frontend type
 *       {@code tags: string[]}) and a downstream caller cannot mutate
 *       a cached response.</li>
 * </ul>
 *
 * @param id                underlying activity row id; never
 *                          {@code null}
 * @param type              discriminator for click-through
 *                          navigation; never {@code null}
 * @param title             human-readable activity title; never
 *                          {@code null} (mapper supplies a fallback)
 * @param hskLevel          HSK 1-6 band; nullable
 * @param ageBand           target age cohort; nullable
 * @param difficulty        easy/medium/hard tier; nullable
 * @param topic             free-form short topic label; nullable
 * @param tags              free-form tags; never {@code null}
 *                          (empty list when none); the wrapped list
 *                          is unmodifiable
 * @param xpReward          base XP awarded on completion; nullable
 * @param estimatedSeconds  UX hint; nullable
 * @param updatedAt         last-modified timestamp; drives feed
 *                          freshness ordering and "updated" labels;
 *                          never {@code null}
 * @param completed         {@code true} when this student has already
 *                          completed this activity at least once
 */
public record StudentActivitySummaryResponse(
        Long id,
        ActivityType type,
        String title,
        Integer hskLevel,
        AgeBand ageBand,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        LocalDateTime updatedAt,
        boolean completed) {

    /**
     * Defensive canonical constructor: enforces non-null on the four
     * header fields the feed UI renders unconditionally
     * ({@code id}, {@code type}, {@code title}, {@code updatedAt}) and
     * normalises {@link #tags} to an unmodifiable defensive copy so
     * external mutation cannot leak into an already-serialised feed
     * row. {@code List.copyOf} additionally fails fast with
     * {@link NullPointerException} on {@code null} elements — an
     * intentional invariant given that the mapper's
     * {@code splitTags} already filters them out.
     */
    public StudentActivitySummaryResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
