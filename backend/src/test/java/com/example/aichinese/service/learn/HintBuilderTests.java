package com.example.aichinese.service.learn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.dto.learn.HintDto;
import com.example.aichinese.entity.enums.AnswerKind;
import com.example.aichinese.entity.enums.LearnQuestionType;

/** Hint ladders are written from the answer key: never wrong, and the answer is never ruled out or named early. */
class HintBuilderTests {

    private static GradableItem quiz(String explanation) {
        List<ChoiceOption> options = List.of(
                new ChoiceOption("Bill", null), new ChoiceOption("Menu", null),
                new ChoiceOption("Rice", null), new ChoiceOption("Cup", null),
                new ChoiceOption("MENU", null)); // Both spellings earn credit.
        return new GradableItem(AnswerKind.CHOICE, null, "What does 菜单 mean?", "菜单", "càidān", null, null, options,
                List.of(), "Menu", explanation);
    }

    @Test
    void aChoiceRulesOutOneAnswerThenGivesAClueThenLeavesTwo() {
        List<HintDto.Step> ladder = HintBuilder.ladder(quiz("菜单 (càidān) is the menu at a restaurant."));
        assertThat(ladder).extracting(HintDto.Step::kind).containsExactly("RULE_OUT", "CLUE", "TWO_LEFT");
        assertThat(ladder.get(0).ruledOut()).containsExactly("Cup");
        assertThat(ladder.get(1).text()).isEqualTo("菜单 (càidān) is the ___ at a restaurant.");
        assertThat(ladder.get(2).words()).containsExactly("Bill", "Menu");
        assertThat(ladder.get(2).ruledOut()).containsExactlyInAnyOrder("Cup", "Rice");
    }

    /** A hint is one step: the rung the level reaches, with what the lighter rungs rule out. */
    @Test
    void aSingleHintIsTheRungOfTheLevelsDepthWithTheAnswersRuledOutBeforeIt() {
        GradableItem item = quiz("菜单 (càidān) is the menu at a restaurant.");
        HintDto.Step deep = HintBuilder.single(HintBuilder.ladder(item), 2);
        assertThat(deep.kind()).isEqualTo("CLUE");
        assertThat(deep.text()).isEqualTo("菜单 (càidān) is the ___ at a restaurant.");
        assertThat(deep.ruledOut()).containsExactly("Cup");

        HintDto.Step nudge = HintBuilder.single(HintBuilder.ladder(item), 1);
        assertThat(nudge.kind()).isEqualTo("RULE_OUT");

        // An answer ruled out by two rungs is named once.
        assertThat(HintBuilder.single(HintBuilder.ladder(item), 3).ruledOut()).containsExactlyInAnyOrder("Cup", "Rice");

        // A ladder shorter than the depth gives its last rung; an empty one gives none.
        assertThat(HintBuilder.single(HintBuilder.pairLadder(item), 2).kind()).isEqualTo("PAIR");
        assertThat(HintBuilder.single(List.of(), 2)).isNull();
    }

    @Test
    void aLevelGetsOnlyTheRungsItsDepthReaches() {
        List<HintDto.Step> ladder = HintBuilder.ladder(quiz(null));
        assertThat(HintBuilder.single(ladder, 1).kind()).isEqualTo("RULE_OUT");
        // Without an explanation, the clue is the answer's first letter.
        HintDto.Step clue = HintBuilder.single(ladder, 2);
        assertThat(clue.kind()).isEqualTo("FIRST_LETTER");
        assertThat(clue.words()).containsExactly("M");
        String deseret = "𐐨𐑌";
        GradableItem supplementary = new GradableItem(AnswerKind.CHOICE, null, "Choose a word", null, null,
                null, null, List.of(new ChoiceOption(deseret, null), new ChoiceOption("other", null)),
                List.of(), deseret, null);
        HintDto.Step initial = HintBuilder.single(HintBuilder.ladder(supplementary), 1);
        assertThat(initial.kind()).isEqualTo("FIRST_LETTER");
        assertThat(initial.words()).containsExactly("𐐀");
    }

    @Test
    void anExplanationThatDoesNotNameTheAnswerIsNotUsedAsAClue() {
        GradableItem item = quiz("You read it on the wall of a café.");
        assertThat(HintBuilder.ladder(item).get(1).kind()).isEqualTo("FIRST_LETTER");
    }

    /** An answer or its pinyin written with a capital beyond ASCII ("CAFÉ", "Ài") is still left out of the clue. */
    @Test
    void theClueLeavesOutTheAnswerAndItsPinyinWhateverTheirCase() {
        assertThat(HintBuilder.maskedExplanation("CAFÉ is where you drink a coffee.", new ChoiceOption("Café", null)))
                .isEqualTo("___ is where you drink a coffee.");
        assertThat(HintBuilder.maskedExplanation("Ài (爱) is what you feel for your family.", new ChoiceOption("爱", "ài")))
                .isEqualTo("___ (___) is what you feel for your family.");
    }

    @Test
    void listeningStartsWithTheSoundToListenFor() {
        List<ChoiceOption> options = List.of(
                new ChoiceOption("一杯茶", "yì bēi chá"), new ChoiceOption("一碗米饭", "yì wǎn mǐfàn"),
                new ChoiceOption("两杯咖啡", "liǎng bēi kāfēi"));
        GradableItem item = new GradableItem(AnswerKind.CHOICE, null, "Which phrase did you hear?", null, null, null,
                "两杯咖啡", options, List.of(), "两杯咖啡", null);
        List<HintDto.Step> ladder = HintBuilder.ladder(item);
        // Three answers: once one is ruled out two are left, so there is no third rung.
        assertThat(ladder).extracting(HintDto.Step::kind).containsExactly("FIRST_SOUND", "RULE_OUT");
        assertThat(ladder.get(0).text()).isEqualTo("l");
    }

    @Test
    void practiceListeningWithEnglishOptionsDoesNotTreatEnglishAsPinyin() {
        List<ChoiceOption> options = List.of(
                new ChoiceOption("Hot", null),
                new ChoiceOption("Cold", null),
                new ChoiceOption("Windy", null));
        GradableItem withExplanation = new GradableItem(AnswerKind.CHOICE, null, "What is the weather like?",
                null, null, null, "今天很热。", options, List.of(), "Hot", "热 (rè) means hot outside today.");
        List<HintDto.Step> explainedLadder = HintBuilder.ladder(withExplanation);
        assertThat(explainedLadder).extracting(HintDto.Step::kind).containsExactly("CLUE", "RULE_OUT");
        assertThat(explainedLadder.get(0).text()).isEqualTo("热 (rè) means ___ outside today.");

        GradableItem withoutExplanation = new GradableItem(AnswerKind.CHOICE, null, "What is the weather like?",
                null, null, null, "今天很热。", options, List.of(), "Hot", null);
        List<HintDto.Step> letterLadder = HintBuilder.ladder(withoutExplanation);
        assertThat(letterLadder).extracting(HintDto.Step::kind).containsExactly("FIRST_LETTER", "RULE_OUT");
        assertThat(letterLadder.get(0).words()).containsExactly("H");
    }

    @Test
    void learnListenChooseWithPinyinTextOptionsStillGivesFirstSound() {
        List<ChoiceOption> options = List.of(
                new ChoiceOption("shān", null),
                new ChoiceOption("sān", null),
                new ChoiceOption("shāng", null));
        GradableItem item = new GradableItem(AnswerKind.CHOICE, LearnQuestionType.LISTEN_CHOOSE, null,
                null, null, null, "山", options, List.of(), "shān", null);
        List<HintDto.Step> ladder = HintBuilder.ladder(item);
        assertThat(ladder).extracting(HintDto.Step::kind).containsExactly("FIRST_SOUND", "RULE_OUT");
        assertThat(ladder.get(0).text()).isEqualTo("sh");
    }

    @Test
    void aSentenceGivesItsFirstWordThenTheWordsThatDoNotBelongThenAllButTheLast() {
        GradableItem item = new GradableItem(AnswerKind.BUILD, LearnQuestionType.TRANSLATE_BUILD, null, "我去图书馆看看",
                "Wǒ qù túshūguǎn kànkan.", "I'll go have a look at the library.", null, List.of(),
                List.of("看", "吃饭", "去", "看", "我", "学校", "看", "图书馆"), "我 去 图书馆 看 看", null);
        List<HintDto.Step> ladder = HintBuilder.ladder(item);
        assertThat(ladder).extracting(HintDto.Step::kind).containsExactly("FIRST_TILE", "LEAVE_OUT", "ORDER_START");
        assertThat(ladder.get(0).words()).containsExactly("我");
        assertThat(ladder.get(1).words()).containsExactlyInAnyOrder("吃饭", "学校");
        assertThat(HintBuilder.single(ladder, 3).ruledOut()).containsExactlyInAnyOrder("吃饭", "学校");
        assertThat(ladder.get(2).words()).containsExactly("我", "去", "图书馆", "看");
    }

    @Test
    void typingGivesTheSoundsNeverTheCharacters() {
        GradableItem item = new GradableItem(AnswerKind.CHOICE, LearnQuestionType.HANZI, null, "谢谢", "xièxie",
                "Thank you", null, List.of(new ChoiceOption("谢谢", "xièxie"), new ChoiceOption("写写", null)),
                List.of(), "谢谢", null);
        List<HintDto.Step> ladder = HintBuilder.typingLadder(item);
        assertThat(ladder).extracting(HintDto.Step::kind).containsExactly("FIRST_SOUND", "INITIALS", "TYPE_THIS");
        assertThat(ladder.get(0).text()).isEqualTo("x");
        assertThat(ladder.get(0).count()).isEqualTo(2);
        assertThat(ladder.get(1).words()).containsExactly("x", "x");
        assertThat(ladder.get(2).text()).isEqualTo("xiexie");
        assertThat(ladder.toString()).doesNotContain("谢");
    }

    @Test
    void pinyinIsComparedAsItIsTyped() {
        assertThat(Pinyin.typed("Xiè xie")).isEqualTo("xiexie");
        assertThat(Pinyin.typed("lǜ")).isEqualTo("lv");
        assertThat(Pinyin.typed("nü3")).isEqualTo("nv");
        // "ü" typed or pasted as "u" with a combining diaeresis (and tone mark) is the same letter.
        assertThat(Pinyin.typed("nu\u0308\u030c")).isEqualTo("nv");
        assertThat(Pinyin.typed("Lu\u0308")).isEqualTo("lv");
        assertThat(Pinyin.syllables("duìbuqǐ")).containsExactly("dui", "bu", "qi");
        assertThat(Pinyin.syllables("shàngbān")).containsExactly("shang", "ban");
        assertThat(Pinyin.syllables("nǐ hǎo")).containsExactly("ni", "hao");
        assertThat(Pinyin.syllables("xi1an1")).containsExactly("xi", "an");
        assertThat(Pinyin.syllables("xian1")).containsExactly("xian");
        assertThat(Pinyin.initial("zhong")).isEqualTo("zh");
        assertThat(Pinyin.initial("ai")).isEmpty();
    }
}
