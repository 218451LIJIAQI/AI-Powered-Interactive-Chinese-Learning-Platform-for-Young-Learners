package com.example.aichinese.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learning.QuizSubmissionAnswerRequest;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;

/**
 * Proposal Table 6.2 "quiz scoring logic": option-based questions are scored by option id, only
 * short-answer types use (normalised) text matching, and invalid answers are rejected before any
 * attempt is stored.
 */
class QuizScoringTests {

    private static final long CORRECT_OPTION_ID = 11L;
    private static final long WRONG_OPTION_ID = 12L;
    private static final long FOREIGN_OPTION_ID = 99L;

    @Test
    void optionBasedQuestionIsScoredByOptionIdNotByText() {
        QuizQuestion question = question("MULTIPLE_CHOICE", "nǐ hǎo");
        List<QuizOption> options = options();

        assertThat(QuizScoring.isCorrect(question, options, CORRECT_OPTION_ID, null)).isTrue();
        // Typing the correct text while choosing another option does not score: the option id decides.
        assertThat(QuizScoring.isCorrect(question, options, WRONG_OPTION_ID, "nǐ hǎo")).isFalse();
        assertThat(QuizScoring.isCorrect(question, options, null, "nǐ hǎo")).isFalse();
    }

    @Test
    void trueFalseAndToneQuestionsAreOptionBased() {
        assertThat(QuizScoring.isOptionBased(question("TRUE_FALSE", "TRUE"))).isTrue();
        assertThat(QuizScoring.isOptionBased(question("CHOOSE_TONE", "mā"))).isTrue();
        assertThat(QuizScoring.isOptionBased(question("FILL_BLANK", "hǎo"))).isFalse();
        assertThat(QuizScoring.isOptionBased(question("REARRANGE_SENTENCE", "我是学生"))).isFalse();
    }

    @Test
    void optionBasedQuestionRejectsMissingOrForeignOptionIds() {
        QuizQuestion question = question("CHOOSE_PINYIN", "nǐ hǎo");

        assertThatThrownBy(() -> QuizScoring.validate(question, options(), optionAnswer(null, "nǐ hǎo")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(QuizScoring.OPTION_REQUIRED_MESSAGE);
        assertThatThrownBy(() -> QuizScoring.validate(question, options(), optionAnswer(FOREIGN_OPTION_ID, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(QuizScoring.OPTION_REQUIRED_MESSAGE);
    }

    @Test
    void shortAnswerTextIsTrimmedCaseWidthAndWhitespaceInsensitive() {
        QuizQuestion fillBlank = question("FILL_BLANK", "Hao");
        QuizQuestion rearrange = question("REARRANGE_SENTENCE", "我 是 学生");

        assertThat(QuizScoring.isCorrect(fillBlank, List.of(), null, "  hao  ")).isTrue();
        // Full-width letters (common with Chinese IMEs) are normalised by NFKC.
        assertThat(QuizScoring.isCorrect(fillBlank, List.of(), null, "ＨＡＯ")).isTrue();
        assertThat(QuizScoring.isCorrect(rearrange, List.of(), null, "我   是 学生")).isTrue();
        // SDD 5.4: spaces are ignored, so the sentence typed without spaces is also correct.
        assertThat(QuizScoring.isCorrect(rearrange, List.of(), null, "我是学生")).isTrue();
        assertThat(QuizScoring.isCorrect(question("FILL_BLANK", "ni hao"), List.of(), null, "nihao")).isTrue();
        assertThat(QuizScoring.isCorrect(fillBlank, List.of(), null, "hǎo"))
                .as("tone marks are significant")
                .isFalse();
    }

    @Test
    void blankShortAnswerIsRejected() {
        QuizQuestion fillBlank = question("FILL_BLANK", "hao");

        assertThatThrownBy(() -> QuizScoring.validate(fillBlank, List.of(), new QuizSubmissionAnswerRequest(1L, null, "   ")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(QuizScoring.ANSWER_REQUIRED_MESSAGE);
    }

    @Test
    void percentageHandlesZeroMaxScoreAndRoundsDown() {
        assertThat(QuizScoring.percentage(0, 0)).isZero();
        assertThat(QuizScoring.percentage(5, 10)).isEqualTo(50);
        assertThat(QuizScoring.percentage(2, 3)).isEqualTo(66);
        // 79.59 % must not count as "at least 80 %" (Quiz Star bonus, Mastered).
        assertThat(QuizScoring.percentage(39, 49)).isEqualTo(79);
        assertThat(QuizScoring.percentage(10, 10)).isEqualTo(100);
    }

    private static QuizQuestion question(String type, String correctAnswerText) {
        QuizQuestion question = new QuizQuestion();
        question.setId(1L);
        question.setQuestionType(type);
        question.setQuestionText("Question");
        question.setCorrectAnswerText(correctAnswerText);
        question.setPoints(5);
        return question;
    }

    private static List<QuizOption> options() {
        return List.of(option(CORRECT_OPTION_ID, "nǐ hǎo", true), option(WRONG_OPTION_ID, "zài jiàn", false));
    }

    private static QuizOption option(long id, String text, boolean correct) {
        QuizOption option = new QuizOption();
        option.setId(id);
        option.setOptionText(text);
        option.setCorrectOption(correct);
        return option;
    }

    private static QuizSubmissionAnswerRequest optionAnswer(Long optionId, String text) {
        return new QuizSubmissionAnswerRequest(1L, optionId, text);
    }
}
