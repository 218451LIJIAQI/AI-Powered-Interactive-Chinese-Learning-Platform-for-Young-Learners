package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.List;

import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.QuizSubmissionAnswerRequest;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.util.TextUtils;

/**
 * Server-side quiz scoring (FRLRN07). Option-based questions are scored by the chosen option
 * id against the single correct option. Older options with the same displayed label share its grade.
 * Typed questions (FILL_BLANK, REARRANGE_SENTENCE) ignore case, width and whitespace when comparing
 * text and preserve pinyin tones.
 */
public final class QuizScoring {

    public static final String OPTION_REQUIRED_MESSAGE =
            "Choose one of the listed options for every multiple-choice question.";
    public static final String ANSWER_REQUIRED_MESSAGE = "Answer is required.";

    private QuizScoring() {
    }

    public static boolean isOptionBased(QuizQuestion question) {
        return question.getQuestionType() != null && question.getQuestionType().isOptionBased();
    }

    public static QuizOption correctOption(List<QuizOption> options) {
        return options.stream().filter(QuizOption::isCorrectOption).findFirst().orElse(null);
    }

    /**
     * Validates one answer before it is saved. Returns the selected option, or null for a typed answer.
     *
     * @throws ResponseStatusException 400 when an option-based question has no valid option id or a
     *         short-answer question has no text
     */
    public static QuizOption validate(QuizQuestion question, List<QuizOption> options, QuizSubmissionAnswerRequest answer) {
        if (isOptionBased(question)) {
            return options.stream().filter(option -> option.getId().equals(answer.optionId())).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, OPTION_REQUIRED_MESSAGE));
        }
        if (TextUtils.normalizeAnswerText(answer.answerText()).isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, ANSWER_REQUIRED_MESSAGE);
        }
        return null;
    }

    /** Grades a selected ID using only this question's stored options, or checks the learner's typed answer. */
    public static boolean isCorrect(QuizQuestion question, List<QuizOption> options, Long selectedOptionId, String answerText) {
        if (isOptionBased(question)) {
            QuizOption correct = correctOption(options);
            if (correct == null || selectedOptionId == null) {
                return false;
            }
            if (correct.getId().equals(selectedOptionId)) {
                return true;
            }
            String label = TextUtils.stripFormSpaces(correct.getOptionText());
            return !label.isEmpty() && options.stream().anyMatch(option -> selectedOptionId.equals(option.getId())
                    && label.equals(TextUtils.stripFormSpaces(option.getOptionText())));
        }
        String expected = TextUtils.normalizeAnswerText(question.getCorrectAnswerText());
        return !expected.isEmpty() && expected.equals(TextUtils.normalizeAnswerText(answerText));
    }

    /** Whole percentage rounded down, so 79.6 % never meets an "at least 80 %" rule (SDD 5.5). */
    public static int percentage(long score, long maxScore) {
        return maxScore <= 0 ? 0 : (int) (score * 100L / maxScore);
    }
}
