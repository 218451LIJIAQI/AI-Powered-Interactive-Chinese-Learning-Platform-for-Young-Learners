package com.example.aichinese.dto.student.activity;

import java.util.List;

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
}
