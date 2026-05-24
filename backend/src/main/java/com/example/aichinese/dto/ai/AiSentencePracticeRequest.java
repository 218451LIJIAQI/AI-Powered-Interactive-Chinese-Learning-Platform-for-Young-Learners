package com.example.aichinese.dto.ai;

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
 */
public record AiSentencePracticeRequest(
        @NotBlank(message = "Sentence is required.")
        @Size(max = 1000, message = "Sentence must not exceed 1000 characters.")
        String sentence,
        @Size(max = 50, message = "Learner level must not exceed 50 characters.")
        String learnerLevel,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {
}
