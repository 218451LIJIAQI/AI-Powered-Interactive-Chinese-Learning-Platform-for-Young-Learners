package com.example.aichinese.dto.student.activity;

import java.util.List;

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
}
