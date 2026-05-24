package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Question format for a single {@code QuizQuestion} row.
 *
 * <p>The format determines what the answer payload looks like and how
 * the auto-grader scores the response. Every <em>option-based</em>
 * question must carry at least one {@code QuizOption} child row (see
 * {@link #isOptionBased()} and {@code TeacherQuizService.persistQuizQuestion}).</p>
 *
 * <p>The remaining types either accept free-text input ({@link #FILL_BLANK})
 * or a structured payload ({@link #REARRANGE_SENTENCE}) and are
 * therefore graded against the question's {@code correctAnswer} field
 * directly rather than through option rows.</p>
 *
 * <p>This enum is parsed from teacher-authored DTOs and stored as a
 * {@code String} on {@code QuizQuestion}. The constant names appear in
 * JSON payloads, so they MUST NOT be renamed without a coordinated
 * frontend change.</p>
 */
public enum QuizQuestionType {

    /** Pick the single correct answer from a list of options. */
    MULTIPLE_CHOICE,

    /** Choose between "true" and "false". */
    TRUE_FALSE,

    /** Type the missing word(s) into a blank in the prompt. */
    FILL_BLANK,

    /** Match each Chinese word to its English meaning from a list. */
    MATCH_WORD_MEANING,

    /** Match each Chinese word to its illustrating image from a list. */
    MATCH_WORD_IMAGE,

    /** Listen to an audio clip and choose the correct option. */
    LISTEN_CHOOSE,

    /** Drag-and-drop sentence fragments into the correct order. */
    REARRANGE_SENTENCE,

    /** Choose the correct pinyin spelling for the given character. */
    CHOOSE_PINYIN,

    /** Choose the correct tone mark for the given pinyin. */
    CHOOSE_TONE,

    /** Choose the correct meaning for the given character. */
    CHARACTER_MEANING;

    /**
     * Question types that require at least one {@code QuizOption} child
     * row. Preserved from the original implementation; consumed by
     * {@code TeacherQuizService.persistQuizQuestion} via
     * {@link #isOptionBased()}.
     */
    private static final Set<QuizQuestionType> OPTION_BASED_TYPES = Set.of(
            MULTIPLE_CHOICE,
            TRUE_FALSE,
            MATCH_WORD_MEANING,
            MATCH_WORD_IMAGE,
            LISTEN_CHOOSE,
            CHOOSE_PINYIN,
            CHOOSE_TONE,
            CHARACTER_MEANING);

    /** Question types that accept free-text input rather than option rows. */
    private static final Set<QuizQuestionType> FREE_TEXT_TYPES = Set.of(FILL_BLANK);

    /** Question types that accept a structured (non-text, non-option) payload. */
    private static final Set<QuizQuestionType> STRUCTURED_PAYLOAD_TYPES =
            Set.of(REARRANGE_SENTENCE);

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, QuizQuestionType> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    value -> value.name().toUpperCase(Locale.ROOT),
                    Function.identity()));

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised question type
     */
    public static Optional<QuizQuestionType> tryFrom(String value) {
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
     * <p>Behaviour preserved from the original implementation: {@code null}
     * or blank input throws "{@code Question type is required.}", invalid
     * input throws the canonical "Allowed values are …" message listing
     * every constant in declaration order. Callers that depend on the
     * exact wording (e.g. teacher-facing error toasts) keep working
     * unchanged.</p>
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised question type
     */
    public static QuizQuestionType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Question type is required.");
        }
        return tryFrom(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid question type. Allowed values are MULTIPLE_CHOICE, TRUE_FALSE, "
                        + "FILL_BLANK, MATCH_WORD_MEANING, MATCH_WORD_IMAGE, LISTEN_CHOOSE, "
                        + "REARRANGE_SENTENCE, CHOOSE_PINYIN, CHOOSE_TONE, or CHARACTER_MEANING."));
    }

    /**
     * @return {@code true} when this question type requires at least one
     *         {@code QuizOption} row to be persisted alongside it
     */
    public boolean isOptionBased() {
        return OPTION_BASED_TYPES.contains(this);
    }

    /** @return {@code true} when the answer is captured as free-text input. */
    public boolean isFreeText() {
        return FREE_TEXT_TYPES.contains(this);
    }

    /**
     * @return {@code true} when the answer is captured as a structured
     *         non-text payload (drag-and-drop ordering, etc.)
     */
    public boolean hasStructuredPayload() {
        return STRUCTURED_PAYLOAD_TYPES.contains(this);
    }
}
