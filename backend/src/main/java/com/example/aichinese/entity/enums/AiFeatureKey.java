package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    public static final Set<AiFeatureKey> TEXT_GENERATIVE = Collections.unmodifiableSet(EnumSet.of(
            CHAT_TUTOR, VOCABULARY_HELPER, SENTENCE_PRACTICE, QUIZ_GENERATOR,
            STORY_GENERATOR, LEARNING_RECOMMENDATION));

    /** Features that consume student-uploaded media (audio or image). */
    public static final Set<AiFeatureKey> MEDIA_FEEDBACK = Collections.unmodifiableSet(EnumSet.of(
            PRONUNCIATION_FEEDBACK, WRITING_FEEDBACK));

    /*
     * Startup-time exhaustiveness guard: every constant must belong to
     * exactly one of the two disjoint category sets.  Catches both
     * "forgotten classification" (constant in neither set) and
     * "duplicated classification" (constant in both sets) at class-load
     * time rather than at runtime, where the latter would manifest as
     * isTextGenerative() and isMediaFeedback() simultaneously true and
     * silently break quota routing / safety-screening dispatch.
     */
    static {
        EnumSet<AiFeatureKey> covered = EnumSet.copyOf(TEXT_GENERATIVE);
        covered.addAll(MEDIA_FEEDBACK);
        if (!covered.equals(EnumSet.allOf(AiFeatureKey.class))) {
            EnumSet<AiFeatureKey> missing = EnumSet.complementOf(covered);
            throw new AssertionError(
                    "AiFeatureKey category sets do not cover all constants; uncovered: " + missing);
        }
        // Defensive symmetry check: a feature must NOT be both text-
        // generative and media-feedback — those predicates are
        // advertised as mutually exclusive to the rest of the codebase.
        EnumSet<AiFeatureKey> overlap = EnumSet.copyOf(TEXT_GENERATIVE);
        overlap.retainAll(MEDIA_FEEDBACK);
        if (!overlap.isEmpty()) {
            throw new AssertionError(
                    "AiFeatureKey TEXT_GENERATIVE and MEDIA_FEEDBACK sets must be disjoint; overlap: "
                            + overlap);
        }
    }

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, AiFeatureKey> LOOKUP =
            EnumLookups.indexByName(AiFeatureKey.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised feature key
     */
    public static Optional<AiFeatureKey> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised feature key
     */
    public static AiFeatureKey fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "AI feature key is required.",
                () -> "Invalid AI feature key. Allowed values are CHAT_TUTOR, VOCABULARY_HELPER, "
                        + "SENTENCE_PRACTICE, QUIZ_GENERATOR, STORY_GENERATOR, "
                        + "LEARNING_RECOMMENDATION, PRONUNCIATION_FEEDBACK, or WRITING_FEEDBACK.");
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
