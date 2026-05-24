package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stable identifier for each user-facing AI feature in the platform.
 *
 * <p>Used by:</p>
 * <ul>
 *   <li>{@code AiUsageLog} to attribute upstream cost and latency to the
 *       feature that triggered the call.</li>
 *   <li>{@code AiResponseReport} to record which feature produced a
 *       response a learner is reporting.</li>
 *   <li>The frontend's quota / banner system to disable individual
 *       features when a daily limit is reached.</li>
 * </ul>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code AiResponseReport} (and persisted as a string in
 * {@code AiUsageLog}). The constant names are part of the database
 * contract — they MUST NOT be renamed or removed without a paired
 * Flyway migration and a frontend feature-flag sweep.</p>
 */
public enum AiFeatureKey {

    /** Free-form Mandarin tutoring conversation with SparkBot. */
    CHAT_TUTOR,

    /** Vocabulary lookup that explains usage, pinyin, and example sentences. */
    VOCABULARY_HELPER,

    /** Generates practice sentences for a target word, grammar pattern, or HSK level. */
    SENTENCE_PRACTICE,

    /** Generates a topical quiz on demand for the student's self-practice. */
    QUIZ_GENERATOR,

    /** Generates an age-appropriate Mandarin story tailored to the learner. */
    STORY_GENERATOR,

    /** Recommends the next learning step based on recent performance. */
    LEARNING_RECOMMENDATION,

    /** Returns feedback on a recorded pronunciation attempt. */
    PRONUNCIATION_FEEDBACK,

    /** Returns feedback on a handwritten character submission. */
    WRITING_FEEDBACK;

    /**
     * Features whose primary output is free-form generated text, suitable
     * for downstream toxicity / age-appropriateness screening.
     */
    public static final Set<AiFeatureKey> TEXT_GENERATIVE = Set.of(
            CHAT_TUTOR, VOCABULARY_HELPER, SENTENCE_PRACTICE, QUIZ_GENERATOR,
            STORY_GENERATOR, LEARNING_RECOMMENDATION);

    /** Features that consume student-uploaded media (audio or image). */
    public static final Set<AiFeatureKey> MEDIA_FEEDBACK = Set.of(
            PRONUNCIATION_FEEDBACK, WRITING_FEEDBACK);

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, AiFeatureKey> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    value -> value.name().toUpperCase(Locale.ROOT),
                    Function.identity()));

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised feature key
     */
    public static Optional<AiFeatureKey> tryFrom(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(LOOKUP.get(trimmed.toUpperCase(Locale.ROOT)));
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised feature key
     */
    public static AiFeatureKey fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AI feature key is required.");
        }
        return tryFrom(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid AI feature key: " + value + "."));
    }

    /**
     * @return {@code true} when this feature primarily emits free-form
     *         generated text (and therefore needs textual safety screening)
     */
    public boolean isTextGenerative() {
        return TEXT_GENERATIVE.contains(this);
    }

    /** @return {@code true} when this feature consumes student-uploaded media. */
    public boolean isMediaFeedback() {
        return MEDIA_FEEDBACK.contains(this);
    }
}
