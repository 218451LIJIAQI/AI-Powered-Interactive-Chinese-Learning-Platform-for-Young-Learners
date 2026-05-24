package com.example.aichinese.dto.student.activity;

import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;

/**
 * Detail payload for one published flashcard, returned by
 * {@code GET /api/v1/students/activities/flashcards/{id}}.
 *
 * <p>Contains everything a child learner needs to study the card:</p>
 * <ul>
 *   <li>{@link #frontText} / {@link #backText} — the prompt face and
 *       the reveal face. The renderer falls back to {@code frontText}
 *       as the heading when {@link #title} is blank (mirrors the admin
 *       authoring ergonomics — see {@code AdminFlashcardRequest}).</li>
 *   <li>{@link #pinyin}, {@link #englishMeaning},
 *       {@link #malayMeaning} — multilingual scaffolding for the two
 *       supported learner languages.</li>
 *   <li>{@link #imageUrl}, {@link #audioUrl} — optional media. The
 *       front-end auto-shows the image when present and renders an
 *       audio button when present.</li>
 *   <li>{@link #extraPayload} — opaque JSON forward-compatibility
 *       blob (radicals, stroke order, example sentences); the
 *       front-end is the canonical schema owner. Emitted verbatim.</li>
 * </ul>
 *
 * <p>Only {@link com.example.aichinese.entity.enums.ContentStatus#PUBLISHED}
 * flashcards reach this endpoint —
 * {@code StudentActivityLibraryService.requirePublishedFlashcard}
 * raises a 404 for any other status.</p>
 *
 * <p>{@link #completed} is pre-computed against the student's progress
 * log so the front-end can render a "Done" indicator without an extra
 * round trip; primitive {@code boolean} so the JSON always emits a
 * value. See {@link StudentActivitySummaryResponse} for the shared
 * student-side metadata vocabulary contract.</p>
 */
public record StudentFlashcardResponse(
        Long id,
        String title,
        String frontText,
        String backText,
        String pinyin,
        String englishMeaning,
        String malayMeaning,
        String imageUrl,
        String audioUrl,
        Integer sortOrder,
        String extraPayload,
        Integer hskLevel,
        AgeBand ageBand,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        boolean completed) {
}
