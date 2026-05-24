package com.example.aichinese.dto.student.activity;

import java.util.List;
import java.util.Objects;

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
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces the wire-format contract so a
 * malformed story payload never reaches the client:</p>
 * <ul>
 *   <li>{@link #id}, {@link #title} and {@link #storyText} are
 *       required — they map to NOT NULL schema columns and are the
 *       core content the screen renders.</li>
 *   <li>{@link #tags} and {@link #questions} are null-coalesced to
 *       {@link List#of()} and wrapped in
 *       {@link List#copyOf(java.util.Collection)} so the wire format
 *       always emits {@code []} (matching the frontend types
 *       {@code tags: string[]} /
 *       {@code questions: StudentStoryQuestionResponse[]}) and a
 *       downstream caller cannot mutate a cached response.</li>
 * </ul>
 *
 * @param id                underlying activity row id; never
 *                          {@code null}
 * @param title             human-readable activity title; never
 *                          {@code null} (NOT NULL schema column)
 * @param instructions      optional admin-authored guidance shown
 *                          above the story body; nullable
 * @param storyText         the story body the learner reads; never
 *                          {@code null} (NOT NULL schema column)
 * @param pinyin            optional pinyin transcription, full-text
 *                          blob; nullable
 * @param translation       optional translation, full-text blob;
 *                          nullable
 * @param imageUrl          optional cover/illustration; nullable
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
 * @param questions         comprehension prompts in canonical
 *                          authoring order; never {@code null}; the
 *                          wrapped list is unmodifiable
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

    /**
     * Defensive canonical constructor: enforces non-null on the three
     * required schema columns ({@code id}, {@code title},
     * {@code storyText}) and normalises both {@link #tags} and
     * {@link #questions} to unmodifiable defensive copies so external
     * mutation cannot leak into an already-serialised response.
     * {@code List.copyOf} additionally fails fast with
     * {@link NullPointerException} on {@code null} elements — an
     * intentional invariant given that the mapper only emits
     * well-formed children.
     */
    public StudentStoryActivityResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(storyText, "storyText must not be null");
        tags = tags == null ? List.of() : List.copyOf(tags);
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
