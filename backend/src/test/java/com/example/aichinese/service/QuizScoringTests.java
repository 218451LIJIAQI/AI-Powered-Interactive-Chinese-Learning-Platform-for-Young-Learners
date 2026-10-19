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

        QuizScoring.ScoredAnswer correct = QuizScoring.score(question, options, optionAnswer(CORRECT_OPTION_ID, null));
        QuizScoring.ScoredAnswer wrong = QuizScoring.score(question, options, optionAnswer(WRONG_OPTION_ID, "nǐ hǎo"));

        assertThat(correct.correct()).isTrue();
        assertThat(correct.selectedOption().getId()).isEqualTo(CORRECT_OPTION_ID);
        assertThat(correct.studentAnswerText()).isEqualTo("nǐ hǎo");
        // Typing the correct text while choosing another option does not score: the option id decides.
        assertThat(wrong.correct()).isFalse();
        assertThat(wrong.correctOption().getId()).isEqualTo(CORRECT_OPTION_ID);
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

        assertThat(QuizScoring.score(fillBlank, List.of(), new QuizSubmissionAnswerRequest(1L, "  hao  ")).correct()).isTrue();
        // Full-width letters (common with Chinese IMEs) are normalised by NFKC.
        assertThat(QuizScoring.score(fillBlank, List.of(), new QuizSubmissionAnswerRequest(1L, "ＨＡＯ")).correct()).isTrue();
        assertThat(QuizScoring.score(rearrange, List.of(), new QuizSubmissionAnswerRequest(1L, "我   是 学生")).correct()).isTrue();
        assertThat(QuizScoring.score(fillBlank, List.of(), new QuizSubmissionAnswerRequest(1L, "hǎo")).correct())
                .as("tone marks are significant")
                .isFalse();
    }

    @Test
    void blankShortAnswerIsRejected() {
        QuizQuestion fillBlank = question("FILL_BLANK", "hao");

        assertThatThrownBy(() -> QuizScoring.validate(fillBlank, List.of(), new QuizSubmissionAnswerRequest(1L, "   ")))
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
        return new QuizSubmissionAnswerRequest(1L, optionId, text, null);
    }
}
