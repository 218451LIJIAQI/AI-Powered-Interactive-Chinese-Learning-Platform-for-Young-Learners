package com.example.aichinese.dto.student.activity;

import java.util.List;
import java.util.Objects;

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
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces the wire-format contract:</p>
 * <ul>
 *   <li>{@link #id}, {@link #frontText} and {@link #backText} are
 *       required — the flashcard schema marks them NOT NULL because
 *       the prompt/reveal pair is the entire reason a flashcard
 *       exists.</li>
 *   <li>{@link #title} is intentionally <em>not</em> required — it is
 *       optional on the flashcard schema and the front-end falls back
 *       to {@link #frontText} for the card heading.</li>
 *   <li>{@link #tags} is null-coalesced to {@link List#of()} and
 *       wrapped in {@link List#copyOf(java.util.Collection)} so the
 *       wire format is always {@code []} (matching the frontend type
 *       {@code tags: string[]}) and a downstream caller cannot mutate
 *       a cached response.</li>
 * </ul>
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

    /**
     * Defensive canonical constructor: enforces non-null on the three
     * required schema columns ({@code id}, {@code frontText},
     * {@code backText}) and normalises {@link #tags} to an unmodifiable
     * defensive copy so external mutation cannot leak into an
     * already-serialised response. {@code List.copyOf} additionally
     * fails fast with {@link NullPointerException} on {@code null}
     * elements — an intentional invariant given that the mapper's
     * {@code splitTags} already filters them out.
     */
    public StudentFlashcardResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(frontText, "frontText must not be null");
        Objects.requireNonNull(backText, "backText must not be null");
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
