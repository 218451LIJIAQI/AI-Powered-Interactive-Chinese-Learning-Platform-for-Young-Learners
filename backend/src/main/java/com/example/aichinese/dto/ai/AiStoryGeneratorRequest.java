package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#trim() trims}
 * the four free-text fields so Bean Validation operates on the
 * canonical form (a whitespace-only {@code topic} correctly
 * fails {@link NotBlank @NotBlank} after trimming) and the
 * service layer's {@code AiInputSanitizer} pass is idempotent.
 * {@code null} is preserved so {@link NotBlank @NotBlank} renders
 * its friendly user-facing message rather than a low-level
 * {@link NullPointerException}.</p>
 */
@JsonPropertyOrder({ "topic", "level", "languagePreference", "lengthPreference" })
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

    /**
     * Defensive canonical constructor: trims the four free-text
     * fields so Bean Validation operates on the canonical form
     * and downstream sanitisation is idempotent. {@code null} is
     * preserved on every field so {@link NotBlank @NotBlank} can
     * render its friendly user-facing message rather than a
     * low-level {@link NullPointerException}.
     */
    public AiStoryGeneratorRequest {
        if (topic != null) {
            topic = topic.trim();
        }
        if (level != null) {
            level = level.trim();
        }
        if (languagePreference != null) {
            languagePreference = languagePreference.trim();
        }
        if (lengthPreference != null) {
            lengthPreference = lengthPreference.trim();
        }
    }
}
