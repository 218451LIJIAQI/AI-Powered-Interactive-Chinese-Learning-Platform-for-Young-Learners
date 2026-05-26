package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.strip;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * non-empty payload. The submission service requires <em>every</em>
 * quiz question to have a matching entry in the parent
 * {@link SubmitQuizAttemptRequest#answers()} list — partial
 * submissions are rejected with {@code 400 Bad Request} carrying
 * "Every quiz question requires an answer." (see
 * {@code StudentLearningService.submitQuizAttempt}). Clients that
 * want to model an "I gave up" interaction must therefore send an
 * explicit sentinel answer (e.g. a non-matching option id or a
 * blank-but-not-empty placeholder for free-text questions) rather
 * than omit the row.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly strips surrounding Unicode whitespace from
 * {@link #answerText} so Bean Validation sees the canonical form
 * (a paste with stray leading/trailing whitespace cannot inflate
 * the length past the cap or sneak past {@link NotBlank @NotBlank})
 * and so the scorer, the persisted
 * {@link com.example.aichinese.entity.QuizAttemptAnswer} row, and
 * the result DTO all see the same stripped value.</p>
 *
 * @param questionId target
 *                   {@link com.example.aichinese.entity.QuizQuestion}
 *                   identifier; required, positive
 * @param answerText encoded answer payload (see encoding rules
 *                   above); required, 1–2000 chars after stripping
 */
@JsonPropertyOrder({ "questionId", "answerText" })
public record QuizSubmissionAnswerRequest(
        @NotNull(message = "Question id is required.")
        @Positive(message = "Question id must be positive.")
        Long questionId,
        @NotBlank(message = "Answer is required.")
        @Size(max = 2000, message = "Answer must be 2000 characters or fewer.")
        String answerText) {

    /**
     * Defensive canonical constructor: strips {@link #answerText} so
     * Bean Validation sees the canonical form (preventing length-cap
     * bypass via leading/trailing whitespace) and matches the
     * service's downstream normalisation when persisting the
     * answer. {@code null} input is preserved so
     * {@link NotBlank @NotBlank} fires correctly.
     */
    public QuizSubmissionAnswerRequest {
        answerText = strip(answerText);
    }
}
