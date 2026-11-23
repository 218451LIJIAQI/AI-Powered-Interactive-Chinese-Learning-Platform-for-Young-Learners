package com.example.aichinese.service.learn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.entity.LearningMistake;
import com.example.aichinese.entity.enums.AnswerKind;
import com.example.aichinese.entity.enums.LearnQuestionType;

/** A waiting mistake is reviewed from its snapshot; an old typing snapshot must not give its answer away. */
class MistakeServiceGradableTests {

    private static final List<ChoiceOption> WITH_PINYIN =
            List.of(new ChoiceOption("谢谢", "xièxie"), new ChoiceOption("写写", null), new ChoiceOption("歇歇", null));

    @Test
    void aTypingMistakeSavedWhenCandidatesHadPinyinIsReviewedWithoutIt() {
        GradableItem item = MistakeService.gradable(mistake("TYPING#7#0"));

        assertThat(item.options()).extracting(ChoiceOption::text).containsExactly("谢谢", "写写", "歇歇");
        assertThat(item.options()).allMatch(option -> option.pinyin() == null);
        assertThat(item.isCorrect("谢谢")).isTrue();
    }

    @Test
    void otherMistakesKeepTheirOptionsAsSaved() {
        GradableItem item = MistakeService.gradable(mistake("LISTENING#7#0"));

        assertThat(item.options()).containsExactlyElementsOf(WITH_PINYIN);
    }

    private static LearningMistake mistake(String itemRef) {
        LearningMistake mistake = new LearningMistake();
        mistake.setItemRef(itemRef);
        mistake.setAnswerKind(AnswerKind.CHOICE);
        mistake.setQuestionKind(LearnQuestionType.HANZI);
        mistake.setHanzi("谢谢");
        mistake.setPinyin("xièxie");
        mistake.setMeaning("thank you");
        mistake.setChoices(LearnAnswers.writeOptions(WITH_PINYIN));
        mistake.setAnswer("谢谢");
        return mistake;
    }
}
