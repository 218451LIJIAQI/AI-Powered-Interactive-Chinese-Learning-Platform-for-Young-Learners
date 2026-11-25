package com.example.aichinese.service.learn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.dto.learn.PracticeItemData;
import com.example.aichinese.dto.learn.PracticeSetRequest;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.enums.AnswerKind;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.DragDropItemRepository;
import com.example.aichinese.repository.FlashcardItemRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.service.learn.PracticeCatalog.Item;
import com.example.aichinese.service.learn.PracticeCatalog.SetContent;

import jakarta.persistence.EntityManager;

/** Playability, authoring validation and Mistakes-review question generation rules of {@link PracticeCatalog}. */
class PracticeCatalogTests {

    private final MatchingGameItemRepository matchingItemRepository = mock(MatchingGameItemRepository.class);
    private final PracticeCatalog catalog = new PracticeCatalog(
            mock(EntityManager.class),
            mock(FlashcardItemRepository.class),
            mock(DragDropItemRepository.class),
            matchingItemRepository);

    @Test
    void matchingRequiresAtLeastTwoDistinctPairsToBePlayable() {
        Item onePair = item("1", "你好", "nǐ hǎo", "hello");
        Item secondPair = item("2", "谢谢", "xièxie", "thank you");
        Item duplicateMeaning = item("3", "您好", "nín hǎo", "Hello!");

        assertThat(catalog.isPlayable(PracticeType.MATCHING, new SetContent(List.of(onePair), null, null))).isFalse();
        assertThat(catalog.isPlayable(PracticeType.MATCHING, new SetContent(List.of(onePair, duplicateMeaning), null, null)))
                .isFalse();
        assertThat(catalog.isPlayable(PracticeType.MATCHING, new SetContent(List.of(onePair, secondPair), null, null)))
                .isTrue();
    }

    @Test
    void matchingItemsCapDistractorChoicesToThreeAndShuffleDeterministically() {
        MatchingGame game = new MatchingGame();
        game.setId(9L);
        List<MatchingGameItem> pairs = List.of(
                pair(1L, "一", "yī", "one"),
                pair(2L, "二", "èr", "two"),
                pair(3L, "三", "sān", "three"),
                pair(4L, "四", "sì", "four"),
                pair(5L, "五", "wǔ", "five"),
                pair(6L, "六", "liù", "six"));
        when(matchingItemRepository.findInDisplayOrder(9L)).thenReturn(pairs);

        List<Item> items = catalog.content(PracticeType.MATCHING, game).items();
        List<Item> repeated = catalog.content(PracticeType.MATCHING, game).items();

        assertThat(items).hasSize(6);
        for (int index = 0; index < items.size(); index++) {
            Item item = items.get(index);
            List<String> texts = item.gradable().options().stream().map(ChoiceOption::text).toList();
            List<String> repeatedTexts = repeated.get(index).gradable().options().stream().map(ChoiceOption::text).toList();
            assertThat(texts).hasSize(4).contains(item.data().meaning()).doesNotHaveDuplicates().isEqualTo(repeatedTexts);
        }
    }

    @Test
    void flashcardValidationRequiresAtLeastTwoDistinctMeanings() {
        PracticeItemData first = item("10", "你好", "nǐ hǎo", "hello").data();
        PracticeItemData sameMeaning = item("11", "您好", "nín hǎo", "Hello!").data();
        var oneCard = new PracticeSetRequest("Solo", LearningStage.GUIDED, 1, List.of(first), null, List.of());
        var duplicateMeaning = new PracticeSetRequest(
                "Dup", LearningStage.GUIDED, 1, List.of(first, sameMeaning), null, List.of());

        assertThatThrownBy(() -> catalog.update(PracticeType.FLASHCARDS, new Flashcard(), oneCard))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure ->
                        assertThat(failure.getReason()).isEqualTo("Add at least 2 cards with different meanings."));
        assertThatThrownBy(() -> catalog.update(PracticeType.FLASHCARDS, new Flashcard(), duplicateMeaning))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure ->
                        assertThat(failure.getReason()).isEqualTo("Add at least 2 cards with different meanings."));
    }

    @Test
    void tracingValidationEnforcesPerCharacterStrokeBoundsEvenWithoutPerCharacterList() {
        PracticeItemData twoChars = item("0", "你好", "nǐ hǎo", "hello").data();
        var tooFew = new PracticeSetRequest("Trace", LearningStage.GUIDED, 1, List.of(twoChars), 1, List.of());
        var tooMany = new PracticeSetRequest("Trace", LearningStage.GUIDED, 1, List.of(twoChars), 121, List.of());

        assertThatThrownBy(() -> catalog.update(PracticeType.TRACING, new TracingExercise(), tooFew))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure ->
                        assertThat(failure.getReason()).isEqualTo("Each character needs 1 to 60 strokes."));
        assertThatThrownBy(() -> catalog.update(PracticeType.TRACING, new TracingExercise(), tooMany))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure ->
                        assertThat(failure.getReason()).isEqualTo("Each character needs 1 to 60 strokes."));
    }

    @Test
    void multiMeaningFlashcardSetBuildsChoiceQuestionWithAudioText() {
        Item first = item("10", "你好", "nǐ hǎo", "hello");
        Item second = item("11", "谢谢", "xièxie", "thank you");
        GradableItem question = PracticeCatalog.flashcardQuestion(
                new SetContent(List.of(first, second), null, null), first);

        assertThat(question.answerKind()).isEqualTo(AnswerKind.CHOICE);
        assertThat(question.options()).hasSize(2);
        assertThat(question.audioText()).isEqualTo("你好");
    }

    private static Item item(String key, String hanzi, String pinyin, String meaning) {
        return new Item(key, new PracticeItemData(hanzi, pinyin, meaning, null, null, null, null,
                List.of(), null, null, null), null);
    }

    private static MatchingGameItem pair(long id, String hanzi, String pinyin, String meaning) {
        MatchingGameItem item = new MatchingGameItem();
        item.setId(id);
        item.setLeftText(hanzi);
        item.setPinyin(pinyin);
        item.setRightText(meaning);
        return item;
    }
}
