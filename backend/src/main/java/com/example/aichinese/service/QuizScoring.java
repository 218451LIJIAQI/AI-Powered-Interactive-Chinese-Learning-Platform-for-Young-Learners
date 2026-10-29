package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learning.QuizSubmissionAnswerRequest;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.enums.QuizQuestionType;

/**
 * Server-side quiz scoring (FRLRN07). Option-based questions are scored by the chosen option
 * id against the single correct option; only short-answer types (FILL_BLANK, REARRANGE_SENTENCE)
 * use text matching (Unicode NFKC, all whitespace removed, case-insensitive; tone marks are
 * significant).
 */
public final class QuizScoring {

    public static final String OPTION_REQUIRED_MESSAGE =
            "Choose one of the listed options for every multiple-choice question.";
    public static final String ANSWER_REQUIRED_MESSAGE = "Answer is required.";

    private QuizScoring() {
    }

    public static boolean isOptionBased(QuizQuestion question) {
        return QuizQuestionType.tryFrom(question.getQuestionType())
                .map(QuizQuestionType::isOptionBased)
                .orElse(false);
    }

    public static QuizOption correctOption(List<QuizOption> options) {
        return options.stream().filter(QuizOption::isCorrectOption).findFirst().orElse(null);
    }

    /**
     * Validates one answer before it is saved (a rejected answer stores nothing).
     *
     * @throws ResponseStatusException 400 when an option-based question has no valid option id or a
     *         short-answer question has no text
     */
    public static void validate(QuizQuestion question, List<QuizOption> options, QuizSubmissionAnswerRequest answer) {
        if (isOptionBased(question)) {
            if (findOption(options, answer.optionId()) == null) {
                throw new ResponseStatusException(BAD_REQUEST, OPTION_REQUIRED_MESSAGE);
            }
        } else if (normalizeText(answer.answerText()).isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, ANSWER_REQUIRED_MESSAGE);
        }
    }

    /** Whether a saved answer is right: the chosen option is the correct one, or the typed text matches. */
    public static boolean isCorrect(QuizQuestion question, List<QuizOption> options, Long selectedOptionId, String answerText) {
        if (isOptionBased(question)) {
            QuizOption correct = correctOption(options);
            return correct != null && correct.getId().equals(selectedOptionId);
        }
        String expected = normalizeText(question.getCorrectAnswerText());
        return !expected.isEmpty() && expected.equals(normalizeText(answerText));
    }

    /** Whole percentage rounded down, so 79.6 % never meets an "at least 80 %" rule (SDD 5.5). */
    public static int percentage(int score, int maxScore) {
        return maxScore <= 0 ? 0 : (int) (score * 100L / maxScore);
    }

    /**
     * NFKC (full-width to half-width), all whitespace removed, lower-cased: typed answers are compared
     * ignoring letter case, character width and spaces (SDD 5.4), so "我是学生" matches "我 是 学生".
     */
    public static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return normalized.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static QuizOption findOption(List<QuizOption> options, Long optionId) {
        return options.stream().filter(option -> option.getId().equals(optionId)).findFirst().orElse(null);
    }
}
