package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * One question-and-answer pair inside a
 * {@link SubmitQuizAttemptRequest}.
 *
 * <h2>{@link #answerText} encoding</h2>
 * <p>The answer is always a single string; how the front-end
 * encodes it depends on the parent question's
 * {@link com.example.aichinese.entity.enums.QuizQuestionType}:</p>
 * <ul>
 *   <li><strong>Option-based</strong> ({@code MULTIPLE_CHOICE},
 *       {@code TRUE_FALSE}, {@code MATCH_*}, {@code LISTEN_CHOOSE},
 *       {@code CHOOSE_PINYIN}, {@code CHOOSE_TONE},
 *       {@code CHARACTER_MEANING}) — the option {@code id} as a
 *       decimal string, or a comma-joined list of option ids for the
 *       multi-correct match types.</li>
 *   <li><strong>{@code FILL_BLANK}</strong> — the literal typed
 *       answer.</li>
 *   <li><strong>{@code REARRANGE_SENTENCE}</strong> — the ordered
 *       fragment ids serialised as the structured payload agreed
 *       between front-end and grader.</li>
 * </ul>
 *
 * <p>The 2000-char cap is a defensive upper bound that easily
 * accommodates every encoding above. {@link NotBlank} forces a
 * non-empty payload; the special "I gave up" case is handled by
 * <em>omitting</em> the answer from the parent
 * {@link SubmitQuizAttemptRequest#answers()} list, not by sending
 * a blank string.</p>
 */
public record QuizSubmissionAnswerRequest(
        @NotNull(message = "Question id is required.")
        @Positive(message = "Question id must be positive.")
        Long questionId,
        @NotBlank(message = "Answer is required.")
        @Size(max = 2000, message = "Answer must be 2000 characters or fewer.")
        String answerText) {
}
