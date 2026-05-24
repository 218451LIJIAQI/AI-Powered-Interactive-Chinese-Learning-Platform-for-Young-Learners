package com.example.aichinese.dto.student.activity;

import java.util.List;
import java.util.Objects;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;

/**
 * Detail payload for one published matching game, returned by
 * {@code GET /api/v1/students/activities/matching-games/{id}}.
 *
 * <p>Aggregate-root response that bundles the game header and its
 * full set of {@link StudentMatchingGameItemResponse} pairs in a
 * single round trip. {@link #items} is emitted in the canonical
 * authoring order
 * ({@code ORDER BY sort_order ASC, created_at ASC}); the front-end is
 * expected to shuffle on each play to prevent positional
 * memorisation.</p>
 *
 * <p>Only {@link com.example.aichinese.entity.enums.ContentStatus#PUBLISHED}
 * games with at least two items reach this endpoint —
 * {@code StudentActivityLibraryService.requirePublishedMatchingGame}
 * raises a 404 otherwise. {@link #completed} is pre-computed
 * server-side; see {@link StudentActivitySummaryResponse} for the
 * shared student-side metadata vocabulary contract.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces the wire-format contract so a
 * malformed detail payload never reaches the client:</p>
 * <ul>
 *   <li>{@link #id} and {@link #title} are required — they drive the
 *       URL the client navigates from and the card heading.</li>
 *   <li>{@link #tags} and {@link #items} are null-coalesced to
 *       {@link List#of()} and wrapped in
 *       {@link List#copyOf(java.util.Collection)} so the wire format
 *       always emits {@code []} (matching the frontend types
 *       {@code tags: string[]} /
 *       {@code items: StudentMatchingGameItemResponse[]}) and a
 *       downstream caller cannot mutate a cached response.</li>
 * </ul>
 *
 * @param id                underlying activity row id; never
 *                          {@code null}
 * @param title             human-readable activity title; never
 *                          {@code null} (NOT NULL schema column)
 * @param hskLevel          HSK 1-6 band; nullable
 * @param ageBand           target age cohort; nullable
 * @param difficulty        easy/medium/hard tier; nullable
 * @param topic             free-form short topic label; nullable
 * @param tags              free-form tags; never {@code null}
 *                          (empty list when none); the wrapped list
 *                          is unmodifiable
 * @param xpReward          base XP awarded on completion; nullable
 * @param estimatedSeconds  UX hint for "time to complete"; nullable
 * @param completed         {@code true} when this student has already
 *                          completed this activity at least once
 * @param items             matching pairs in canonical authoring
 *                          order; never {@code null}; the wrapped
 *                          list is unmodifiable
 */
public record StudentMatchingGameResponse(
        Long id,
        String title,
        Integer hskLevel,
        AgeBand ageBand,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        boolean completed,
        List<StudentMatchingGameItemResponse> items) {

    /**
     * Defensive canonical constructor: enforces non-null on the heading
     * fields ({@code id}, {@code title}) and normalises both
     * {@link #tags} and {@link #items} to unmodifiable defensive copies
     * so external mutation cannot leak into an already-serialised
     * response. {@code List.copyOf} additionally fails fast with
     * {@link NullPointerException} on {@code null} elements — an
     * intentional invariant given that the mapper only emits
     * well-formed children.
     */
    public StudentMatchingGameResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        tags = tags == null ? List.of() : List.copyOf(tags);
        items = items == null ? List.of() : List.copyOf(items);
    }
}
