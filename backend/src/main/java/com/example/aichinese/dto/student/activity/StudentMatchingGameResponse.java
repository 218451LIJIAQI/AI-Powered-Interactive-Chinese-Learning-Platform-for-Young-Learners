package com.example.aichinese.dto.student.activity;

import java.util.List;

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
}
