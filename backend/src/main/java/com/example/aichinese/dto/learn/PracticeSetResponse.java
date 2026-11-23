package com.example.aichinese.dto.learn;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.PracticeType;

/**
 * A Practice set ready to play, without answers. Items are keyed for checking; matching sets add the meanings to pair
 * (shuffled), tracing sets the strokes to draw.
 *
 * @param done every item was right in a previous run. Flashcards require a "Got it" rating for every card in that run,
 *             although the first complete review earns XP with any combination of ratings.
 * @param characterStrokes tracing: the strokes of each character in order, adding up to {@code strokeCount} ("Character
 *                         1 of 2 · 0 of 4 strokes"); null when the set does not say
 * @param runStartedAt the start of this practice run, sent with each hint request so its allowance is counted separately
 * @param runSignature the server's signature of this run, sent back with {@code runStartedAt}
 */
public record PracticeSetResponse(
        Long id,
        PracticeType type,
        LearningStage stage,
        int xpReward,
        boolean done,
        List<Item> items,
        List<String> matchingChoices,
        Integer strokeCount,
        List<Integer> characterStrokes,
        LocalDateTime runStartedAt,
        String runSignature) {

    public record Item(
            String key,
            String prompt,
            String hanzi,
            String pinyin,
            String meaning,
            String audioText,
            String example,
            String exampleMeaning,
            List<ChoiceOption> options,
            List<String> tiles) {
    }
}
