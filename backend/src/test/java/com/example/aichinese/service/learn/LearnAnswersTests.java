package com.example.aichinese.service.learn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.entity.enums.AnswerKind;
import com.example.aichinese.entity.enums.LearnQuestionType;

/** How tiles read once joined: one rule for the verdict, the Mistakes tab and review, and the Learn path seed. */
class LearnAnswersTests {

    /** A space stays only next to a Latin letter. */
    @ParameterizedTest
    @CsvSource({
            "'教室 在 2 楼', 教室在2楼",
            "'教室　 在　2 楼', 教室在2楼",
            "'你 叫 什么 名字 ？', 你叫什么名字？",
            "'Good night', Good night",
            "'mā pà dú hē', mā pà dú hē",
            "'我 叫 Amira', 我叫 Amira"})
    void joinedTilesCloseUpExceptNextToALatinLetter(String tiles, String joined) {
        assertThat(LearnAnswers.joinTiles(tiles)).isEqualTo(joined);
    }

    /** An admin-written Drag & Drop item takes its hanzi from its joined tiles (PracticeCatalog), as built here. */
    @Test
    void aDragAndDropAnswerWithADigitReadsAsWritten() {
        String dragText = "教室 在 2 楼";
        GradableItem item = new GradableItem(AnswerKind.BUILD, LearnQuestionType.TRANSLATE_BUILD, null,
                LearnAnswers.joinTiles(dragText), null, "The classroom is on floor 2.", null, List.of(),
                LearnAnswers.tiles(dragText), dragText, null);

        assertThat(item.check("2 楼 在 教室").correctAnswer()).isEqualTo("教室在2楼");
        assertThat(item.shown("2 楼 在 教室")).isEqualTo("2楼在教室");
    }

    /** A question about 大 (dà) whose answers are Chinese too: the verdict reads 小 with its own pinyin. */
    @Test
    void theVerdictGivesTheRightAnswersOwnPinyin() {
        List<ChoiceOption> options = List.of(new ChoiceOption("小", "xiǎo"), new ChoiceOption("多", "duō"));
        GradableItem opposite = new GradableItem(AnswerKind.CHOICE, null, "Which is the opposite?", "大", "dà", null,
                null, options, List.of(), "小", null);
        assertThat(opposite.check("多").pinyin()).isEqualTo("xiǎo");
        // The right option is the one the answer key matches as answers are graded, punctuation aside.
        GradableItem punctuated = new GradableItem(AnswerKind.CHOICE, null, "Which is the opposite?", "大", "dà", null,
                null, options, List.of(), "小。", null);
        assertThat(punctuated.check("多").pinyin()).isEqualTo("xiǎo");

        // Answers without pinyin (meanings) keep the pinyin of the word asked about.
        GradableItem meaning = new GradableItem(AnswerKind.CHOICE, null, "What does 你好 mean?", "你好", "nǐ hǎo", null,
                null, List.of(new ChoiceOption("Hello", null), new ChoiceOption("Sorry", null)), List.of(), "Hello", null);
        assertThat(meaning.check("Hello").pinyin()).isEqualTo("nǐ hǎo");
    }

    @Test
    void answersMatchIgnoringCaseWidthSpacesAndPunctuationButNotTones() {
        assertThat(LearnAnswers.matches("Hello", " hello! ")).isTrue();
        assertThat(LearnAnswers.matches("我叫小美。", "我 叫 小美")).isTrue();
        assertThat(LearnAnswers.matches("我叫小美。", "我\ufeff叫小美")).isTrue();
        assertThat(LearnAnswers.matches("\ufeff", "\ufeff")).isFalse();
        assertThat(LearnAnswers.tiles("我\ufeff叫\u00a0小美")).containsExactly("我", "叫", "小美");
        assertThat(LearnAnswers.matches("ＡＢＣ", "abc")).isTrue();
        assertThat(LearnAnswers.matches("mā", "ma")).isFalse();
        assertThat(LearnAnswers.matches("", "")).isFalse();
        assertThat(LearnAnswers.matches("谢谢", null)).isFalse();
    }

    @Test
    void malformedOrNonArrayStoredJsonReturnsEmptyLists() {
        assertThat(LearnAnswers.readOptions("{not valid json")).isEmpty();
        assertThat(LearnAnswers.readOptions("{\"text\":\"not an array\"}")).isEmpty();
        assertThat(LearnAnswers.readTiles("{not valid json")).isEmpty();
        assertThat(LearnAnswers.readTiles("{\"tile\":\"not an array\"}")).isEmpty();
    }
}
