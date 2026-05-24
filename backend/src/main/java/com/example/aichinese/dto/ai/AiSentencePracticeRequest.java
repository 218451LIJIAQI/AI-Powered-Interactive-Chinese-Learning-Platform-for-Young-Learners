package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for the AI sentence-practice feature
 * ({@code POST /api/v1/ai/sentence-practice}) — the student
 * submits a Chinese sentence they wrote and the AI returns
 * corrections, pinyin, translations, and a follow-up practice
 * task.
 *
 * <p>Drives the "Check my sentence" tutor surface. Critical for
 * scaffolding writing practice — the AI's role here is gentle
 * correction with explanation, not just marking right/wrong.
 * See {@link AiSentencePracticeResponse} for the structured
 * response shape.</p>
 *
 * <p>{@link #sentence} is the student's sentence (capped at 1000
 * chars; sanitised before forwarding to the provider).
 * {@link #learnerLevel} (e.g. {@code "BEGINNER"},
 * {@code "HSK 3"}) and {@link #languagePreference} let the AI
 * adjust correction depth and the language of the explanation.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#trim() trims}
 * the three free-text fields so Bean Validation operates on the
 * canonical form (a whitespace-only {@code sentence} correctly
 * fails {@link NotBlank @NotBlank} after trimming) and the
 * service layer's {@code AiInputSanitizer} pass is idempotent.
 * {@code null} is preserved so {@link NotBlank @NotBlank} renders
 * its friendly user-facing message rather than a low-level
 * {@link NullPointerException}.</p>
 */
@JsonPropertyOrder({ "sentence", "learnerLevel", "languagePreference" })
public record AiSentencePracticeRequest(
        @NotBlank(message = "Sentence is required.")
        @Size(max = 1000, message = "Sentence must not exceed 1000 characters.")
        String sentence,
        @Size(max = 50, message = "Learner level must not exceed 50 characters.")
        String learnerLevel,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {

    /**
     * Defensive canonical constructor: trims the three free-text
     * fields so Bean Validation operates on the canonical form
     * and downstream sanitisation is idempotent. {@code null} is
     * preserved on every field so {@link NotBlank @NotBlank} can
     * render its friendly user-facing message rather than a
     * low-level {@link NullPointerException}.
     */
    public AiSentencePracticeRequest {
        if (sentence != null) {
            sentence = sentence.trim();
        }
        if (learnerLevel != null) {
            learnerLevel = learnerLevel.trim();
        }
        if (languagePreference != null) {
            languagePreference = languagePreference.trim();
        }
    }
}
