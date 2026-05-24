package com.example.aichinese.dto.student.activity;

import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;

/**
 * Detail payload for one published drag-and-drop activity, returned
 * by {@code GET /api/v1/students/activities/drag-drop-activities/{id}}.
 *
 * <p>Aggregate-root response that bundles the activity header,
 * optional admin-authored {@link #instructions}, and the full set of
 * {@link StudentDragDropItemResponse} draggable cards in a single
 * round trip. {@link #items} is emitted in the canonical authoring
 * order ({@code ORDER BY sort_order ASC, created_at ASC}); the
 * front-end may shuffle on each play.</p>
 *
 * <p>Only {@link com.example.aichinese.entity.enums.ContentStatus#PUBLISHED}
 * activities with at least one item reach this endpoint —
 * {@code StudentActivityLibraryService.requirePublishedDragDropActivity}
 * raises a 404 otherwise. {@link #completed} is pre-computed
 * server-side; see {@link StudentActivitySummaryResponse} for the
 * shared student-side metadata vocabulary contract.</p>
 */
public record StudentDragDropActivityResponse(
        Long id,
        String title,
        String instructions,
        Integer hskLevel,
        AgeBand ageBand,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        boolean completed,
        List<StudentDragDropItemResponse> items) {
}
