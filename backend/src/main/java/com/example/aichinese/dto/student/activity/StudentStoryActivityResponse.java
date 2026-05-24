package com.example.aichinese.dto.student.activity;

import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;

/**
 * Detail payload for one published story activity, returned by
 * {@code GET /api/v1/students/activities/story-activities/{id}}.
 *
 * <p>Aggregate-root response that bundles the story body
 * ({@link #storyText}, optional {@link #pinyin} transcription and
 * {@link #translation}, optional {@link #imageUrl}), optional
 * admin-authored {@link #instructions}, and the full set of
 * {@link StudentStoryQuestionResponse} comprehension prompts in a
 * single round trip. {@link #questions} is emitted in the canonical
 * authoring order ({@code ORDER BY sort_order ASC, created_at ASC}).</p>
 *
 * <h2>Pinyin and translation</h2>
 * <p>Both are emitted as full-text blobs (matching the schema's
 * {@code TEXT} columns) — the front-end is responsible for any
 * word-level alignment with {@link #storyText} if it wants to render
 * inline ruby annotations or hover-translations.</p>
 *
 * <p>Only {@link com.example.aichinese.entity.enums.ContentStatus#PUBLISHED}
 * stories with at least one question reach this endpoint —
 * {@code StudentActivityLibraryService.requirePublishedStoryActivity}
 * raises a 404 otherwise. {@link #completed} is pre-computed
 * server-side; see {@link StudentActivitySummaryResponse} for the
 * shared student-side metadata vocabulary contract.</p>
 */
public record StudentStoryActivityResponse(
        Long id,
        String title,
        String instructions,
        String storyText,
        String pinyin,
        String translation,
        String imageUrl,
        Integer hskLevel,
        AgeBand ageBand,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        boolean completed,
        List<StudentStoryQuestionResponse> questions) {
}
