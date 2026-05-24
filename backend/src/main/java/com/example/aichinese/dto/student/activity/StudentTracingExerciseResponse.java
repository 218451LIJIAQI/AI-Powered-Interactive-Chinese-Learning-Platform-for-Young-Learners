package com.example.aichinese.dto.student.activity;

import java.util.List;
import java.util.Objects;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;

/**
 * Detail payload for one published tracing exercise, returned by
 * {@code GET /api/v1/students/activities/tracing-exercises/{id}}.
 *
 * <p>Carries everything the front-end needs to render a
 * character-tracing practice surface:</p>
 * <ul>
 *   <li>{@link #targetCharacter} — the Hanzi character the learner
 *       traces. Drives the on-screen practice prompt.</li>
 *   <li>{@link #worksheetUrl} — the SVG (or image) stroke-guide asset
 *       overlaid onto the practice canvas. The publication gate
 *       refuses to publish without this, so it is always set on this
 *       endpoint's response.</li>
 *   <li>{@link #pinyin}, {@link #meaning} — beginner scaffolding shown
 *       beside the practice surface.</li>
 *   <li>{@link #instructions} — optional admin-authored guidance
 *       (e.g. "Trace each stroke from top to bottom").</li>
 * </ul>
 *
 * <p>Only {@link com.example.aichinese.entity.enums.ContentStatus#PUBLISHED}
 * exercises reach this endpoint. {@link #completed} is pre-computed
 * server-side; see {@link StudentActivitySummaryResponse} for the
 * shared student-side metadata vocabulary contract.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces the wire-format contract so a
 * malformed tracing exercise never reaches the client:</p>
 * <ul>
 *   <li>{@link #id}, {@link #title}, {@link #worksheetUrl} and
 *       {@link #targetCharacter} are required — the first two are
 *       NOT NULL schema columns, the worksheet URL is guaranteed by
 *       the publication gate, and the target character is the entire
 *       reason a tracing exercise exists.</li>
 *   <li>{@link #tags} is null-coalesced to {@link List#of()} and
 *       wrapped in {@link List#copyOf(java.util.Collection)} so the
 *       wire format is always {@code []} (matching the frontend type
 *       {@code tags: string[]}) and a downstream caller cannot mutate
 *       a cached response.</li>
 * </ul>
 */
public record StudentTracingExerciseResponse(
        Long id,
        String title,
        String worksheetUrl,
        String targetCharacter,
        String pinyin,
        String meaning,
        String instructions,
        Integer hskLevel,
        AgeBand ageBand,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        boolean completed) {

    /**
     * Defensive canonical constructor: enforces non-null on the four
     * fields the publication gate promises every tracing exercise has
     * ({@code id}, {@code title}, {@code worksheetUrl},
     * {@code targetCharacter}) and normalises {@link #tags} to an
     * unmodifiable defensive copy so external mutation cannot leak
     * into an already-serialised response.
     */
    public StudentTracingExerciseResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(worksheetUrl, "worksheetUrl must not be null");
        Objects.requireNonNull(targetCharacter, "targetCharacter must not be null");
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
