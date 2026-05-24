package com.example.aichinese.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for the AI story generator
 * ({@code POST /api/v1/ai/story-generator}) — generates a
 * short Chinese story with vocabulary list and comprehension
 * questions tailored to the student's level.
 *
 * <p>Drives the "Generate a story for me" tutor surface — one
 * of the platform's flagship Mandarin-immersion tools for child
 * learners. The structured response (see
 * {@link AiStoryGeneratorResponse}) bundles the story body,
 * inline pinyin, English/Bahasa Melayu translations, and
 * comprehension questions so the student gets a complete
 * mini-lesson in one call.</p>
 *
 * <h2>{@link #lengthPreference}</h2>
 * <p>Free-form short string ({@code "short"}, {@code "medium"},
 * {@code "long"}, or even something like {@code "100 words"}) —
 * the AI interprets it. Capped at 50 chars; the prompt template
 * normalises common values.</p>
 *
 * <p>{@link #topic} is the only required field. {@link #level}
 * (e.g. {@code "BEGINNER"}, {@code "HSK 2"}) tells the AI which
 * vocabulary tier to draw from. {@link #languagePreference}
 * controls the language of the encouragement / question
 * stems.</p>
 */
public record AiStoryGeneratorRequest(
        @NotBlank(message = "Topic is required.")
        @Size(max = 200, message = "Topic must not exceed 200 characters.")
        String topic,
        @Size(max = 100, message = "Level must not exceed 100 characters.")
        String level,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference,
        @Size(max = 50, message = "Length preference must not exceed 50 characters.")
        String lengthPreference) {
}
