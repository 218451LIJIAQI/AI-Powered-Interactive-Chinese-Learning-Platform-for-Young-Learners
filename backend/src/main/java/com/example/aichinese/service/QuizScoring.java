package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learning.QuizSubmissionAnswerRequest;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.enums.QuizQuestionType;

/**
 * Server-side quiz scoring (FR-QUIZ-02/03). Option-based questions are scored by the chosen option
 * id against the single correct option; only short-answer types (FILL_BLANK, REARRANGE_SENTENCE)
 * use text matching (Unicode NFKC, trimmed, whitespace collapsed, case-insensitive; tone marks are
 * significant).
 */
public final class QuizScoring {

    public static final String OPTION_REQUIRED_MESSAGE =
            "Choose one of the listed options for every multiple-choice question.";
    public static final String ANSWER_REQUIRED_MESSAGE = "Answer is required.";

    private QuizScoring() {
    }

    public static boolean isOptionBased(QuizQuestion question) {
        return QuizQuestionType.tryFrom(question == null ? null : question.getQuestionType())
                .map(QuizQuestionType::isOptionBased)
                .orElse(false);
    }

    public static QuizOption correctOption(List<QuizOption> options) {
        if (options == null) {
            return null;
        }
        return options.stream().filter(QuizOption::isCorrectOption).findFirst().orElse(null);
    }

    /**
     * Validates one answer without touching the database (a rejected submission stores nothing).
     *
     * @throws ResponseStatusException 400 when an option-based question has no valid option id or a
     *         short-answer question has no text
     */
    public static void validate(QuizQuestion question, List<QuizOption> options, QuizSubmissionAnswerRequest answer) {
        if (isOptionBased(question)) {
            if (answer.optionId() == null || findOption(options, answer.optionId()) == null) {
                throw new ResponseStatusException(BAD_REQUEST, OPTION_REQUIRED_MESSAGE);
            }
        } else if (normalizeText(answer.answerText()).isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, ANSWER_REQUIRED_MESSAGE);
        }
    }

    /** Scores a validated answer. */
    public static ScoredAnswer score(QuizQuestion question, List<QuizOption> options, QuizSubmissionAnswerRequest answer) {
        validate(question, options, answer);
        if (isOptionBased(question)) {
            QuizOption selected = findOption(options, answer.optionId());
            QuizOption correct = correctOption(options);
            boolean isCorrect = correct != null && Objects.equals(correct.getId(), selected.getId());
            return new ScoredAnswer(isCorrect, selected.getOptionText(), selected, correct);
        }
        String submitted = answer.answerText() == null ? "" : answer.answerText().strip();
        boolean isCorrect = !normalizeText(question.getCorrectAnswerText()).isEmpty()
                && normalizeText(submitted).equals(normalizeText(question.getCorrectAnswerText()));
        return new ScoredAnswer(isCorrect, submitted, null, null);
    }

    /** Whole percentage rounded down, so 79.6 % never meets an "at least 80 %" rule (SDD 5.5). */
    public static int percentage(int score, int maxScore) {
        return maxScore <= 0 ? 0 : (int) (score * 100L / maxScore);
    }

    /** NFKC (full-width to half-width), trimmed, inner whitespace collapsed, lower-cased. */
    public static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return normalized.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static QuizOption findOption(List<QuizOption> options, Long optionId) {
        if (options == null || optionId == null) {
            return null;
        }
        return options.stream().filter(option -> optionId.equals(option.getId())).findFirst().orElse(null);
    }

    /** Result of scoring one answer. */
    public record ScoredAnswer(
            boolean correct,
            String studentAnswerText,
            QuizOption selectedOption,
            QuizOption correctOption) {
    }
}
