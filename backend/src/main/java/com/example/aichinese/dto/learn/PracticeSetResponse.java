package com.example.aichinese.dto.learn;

import java.util.List;

import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.PracticeType;

/**
 * A Practice set ready to play, without answers. Items are keyed for checking; matching sets add the meanings to pair
 * (shuffled), stories their lines, tracing sets the strokes to draw.
 */
public record PracticeSetResponse(
        Long id,
        PracticeType type,
        String title,
        LearningStage stage,
        Integer hskLevel,
        int xpReward,
        boolean done,
        List<Item> items,
        List<StoryLine> storyLines,
        List<String> matchingChoices,
        Integer strokeCount) {

    public PracticeSetResponse {
        items = items == null ? List.of() : List.copyOf(items);
        storyLines = storyLines == null ? List.of() : List.copyOf(storyLines);
        matchingChoices = matchingChoices == null ? List.of() : List.copyOf(matchingChoices);
    }

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

        public Item {
            options = options == null ? List.of() : List.copyOf(options);
            tiles = tiles == null ? List.of() : List.copyOf(tiles);
        }
    }
}
