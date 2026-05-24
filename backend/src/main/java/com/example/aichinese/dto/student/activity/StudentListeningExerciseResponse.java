package com.example.aichinese.dto.student.activity;

import java.util.List;
import java.util.Objects;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;

/**
 * Detail payload for one published listening exercise, returned by
 * {@code GET /api/v1/students/activities/listening-exercises/{id}}.
 *
 * <h2>{@link #questionPayload}</h2>
 * <p>Opaque JSON document encoding the comprehension prompts and the
 * acceptable answers — emitted verbatim from the underlying
 * {@code listening_exercises.question_payload} column. The front-end
 * is the canonical schema owner; the backend treats it as a string
 * blob. Authoring rules and the cap (65535 chars) live on the admin
 * side ({@code AdminListeningExerciseRequest}).</p>
 *
 * <h2>{@link #audioUrl}</h2>
 * <p>The downloadable/streamable audio asset that the learner listens
 * to. The publication gate refuses to publish a listening exercise
 * with a missing audio URL, so for any payload returned by this
 * endpoint the field will be set.</p>
 *
 * <p>Only {@link com.example.aichinese.entity.enums.ContentStatus#PUBLISHED}
 * exercises reach this endpoint. {@link #completed} is pre-computed
 * server-side; see {@link StudentActivitySummaryResponse} for the
 * shared student-side metadata vocabulary contract.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces the wire-format contract so a
 * malformed listening exercise never reaches the client:</p>
 * <ul>
 *   <li>{@link #id} and {@link #title} are required — they map to NOT
 *       NULL schema columns and drive the URL and the card heading.</li>
 *   <li>{@link #audioUrl} and {@link #questionPayload} are required —
 *       they are the entire reason a listening exercise exists, and
 *       the publication gate guarantees both are set on every payload
 *       this endpoint emits.</li>
 *   <li>{@link #tags} is null-coalesced to {@link List#of()} and
 *       wrapped in {@link List#copyOf(java.util.Collection)} so the
 *       wire format is always {@code []} (matching the frontend type
 *       {@code tags: string[]}) and a downstream caller cannot mutate
 *       a cached response.</li>
 * </ul>
 */
public record StudentListeningExerciseResponse(
        Long id,
        String title,
        String audioUrl,
        String questionPayload,
        Integer hskLevel,
        AgeBand ageBand,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        boolean completed) {

    /**
     * Defensive canonical constructor: enforces non-null on
     * {@code id}, {@code title}, {@code audioUrl} and
     * {@code questionPayload} (the four fields the publication gate
     * promises every listening exercise has) and normalises
     * {@link #tags} to an unmodifiable defensive copy so external
     * mutation cannot leak into an already-serialised response.
     */
    public StudentListeningExerciseResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(audioUrl, "audioUrl must not be null");
        Objects.requireNonNull(questionPayload, "questionPayload must not be null");
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
