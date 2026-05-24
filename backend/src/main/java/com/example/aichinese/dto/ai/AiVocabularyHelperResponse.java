package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Structured AI vocabulary helper response returned by
 * {@code POST /api/v1/ai/vocabulary-helper}.
 *
 * <p>Returns a strict structured shape (rather than free-form
 * text) so the front-end can render a consistent flashcard-style
 * card without parsing prose. The AI is constrained by the
 * server-side prompt template to populate every field in the
 * student's preferred language; missing fields default to empty
 * strings rather than {@code null} so the UI never has to
 * null-check.</p>
 *
 * <h2>Bilingual scaffolding</h2>
 * <p>Both {@link #englishMeaning} and {@link #bahasaMelayuMeaning}
 * are returned regardless of the student's preferred language —
 * the platform supports both English and Malay learners and the
 * front-end displays whichever matches the user's preference.
 * Same dual-language pattern carries through to
 * {@link AiSentencePracticeResponse},
 * {@link AiStoryGeneratorResponse},
 * {@link AiStoryVocabularyItemResponse}.</p>
 *
 * <h2>{@link #imagePromptOrDescription}</h2>
 * <p>Human-readable description of an illustrative image (e.g.
 * "A red apple on a wooden table"). The platform does not yet
 * generate images — this field is reserved for a future
 * image-generation pass; today the front-end displays it as a
 * "Imagine..." prompt to engage visual memory.</p>
 *
 * <p>{@link #miniPracticeQuestion} is a one-shot self-check
 * question; {@link #encouragement} is a short motivational
 * blurb. {@link #status} mirrors the
 * {@link AiChatResponse#status} convention.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 */
@JsonPropertyOrder({
        "chineseWord",
        "pinyin",
        "englishMeaning",
        "bahasaMelayuMeaning",
        "simpleExplanation",
        "exampleSentence",
        "exampleSentencePinyin",
        "exampleSentenceTranslation",
        "imagePromptOrDescription",
        "miniPracticeQuestion",
        "encouragement",
        "status"
})
public record AiVocabularyHelperResponse(
        String chineseWord,
        String pinyin,
        String englishMeaning,
        String bahasaMelayuMeaning,
        String simpleExplanation,
        String exampleSentence,
        String exampleSentencePinyin,
        String exampleSentenceTranslation,
        String imagePromptOrDescription,
        String miniPracticeQuestion,
        String encouragement,
        String status) {
}
