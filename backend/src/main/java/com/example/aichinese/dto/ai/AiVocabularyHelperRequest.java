package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for the AI vocabulary helper feature
 * ({@code POST /api/v1/ai/vocabulary-helper}) — the student types
 * a word in any supported language and the AI returns the
 * Chinese form with multilingual scaffolding plus a tiny practice
 * exercise.
 *
 * <p>Drives the student's "What does this word mean?" lookup
 * tool. One of the most-used AI surfaces because it is the
 * obvious first lookup whenever a student encounters an
 * unfamiliar word in lesson content.</p>
 *
 * <h2>{@link #word}</h2>
 * <p>The query word — can be Chinese (looking up meaning), pinyin
 * (looking up the character), English / Bahasa Melayu (looking up
 * the Chinese form). The AI auto-detects when
 * {@link #inputLanguage} is omitted, but specifying it produces
 * better results for ambiguous inputs.</p>
 *
 * <h2>{@link #inputLanguage} / {@link #outputLanguage}</h2>
 * <p>Two-letter language codes ({@code "zh"} / {@code "en"} /
 * {@code "ms"} / {@code "py"} for pinyin) hinting how to
 * interpret the input and how to phrase the response. Both are
 * optional with sensible defaults.</p>
 *
 * <p>{@link #learnerLevel} (e.g. {@code "BEGINNER"},
 * {@code "HSK 3"}) and {@link #languagePreference} let the AI
 * adjust the explanation depth and the language of the
 * encouragement / practice prompt. See
 * {@link AiVocabularyHelperResponse} for the structured response.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#trim() trims}
 * all five short text fields so Bean Validation operates on the
 * canonical form (a whitespace-only {@code word} correctly fails
 * {@link NotBlank @NotBlank} after trimming) and the service
 * layer's {@code AiInputSanitizer} pass is idempotent.
 * {@code null} is preserved so {@link NotBlank @NotBlank} renders
 * its friendly user-facing message rather than a low-level
 * {@link NullPointerException}.</p>
 */
@JsonPropertyOrder({
        "word",
        "inputLanguage",
        "outputLanguage",
        "learnerLevel",
        "languagePreference"
})
public record AiVocabularyHelperRequest(
        @NotBlank(message = "Word is required.")
        @Size(max = 200, message = "Word must not exceed 200 characters.")
        String word,
        @Size(max = 20, message = "Input language must not exceed 20 characters.")
        String inputLanguage,
        @Size(max = 20, message = "Output language must not exceed 20 characters.")
        String outputLanguage,
        @Size(max = 50, message = "Learner level must not exceed 50 characters.")
        String learnerLevel,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {

    /**
     * Defensive canonical constructor: trims the five short text
     * fields so Bean Validation operates on the canonical form
     * and downstream sanitisation is idempotent. {@code null} is
     * preserved on every field so {@link NotBlank @NotBlank} can
     * render its friendly user-facing message rather than a
     * low-level {@link NullPointerException}.
     */
    public AiVocabularyHelperRequest {
        if (word != null) {
            word = word.trim();
        }
        if (inputLanguage != null) {
            inputLanguage = inputLanguage.trim();
        }
        if (outputLanguage != null) {
            outputLanguage = outputLanguage.trim();
        }
        if (learnerLevel != null) {
            learnerLevel = learnerLevel.trim();
        }
        if (languagePreference != null) {
            languagePreference = languagePreference.trim();
        }
    }
}
