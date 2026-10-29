package com.example.aichinese.service.learn;

import java.util.List;

import com.example.aichinese.dto.learn.AnswerCheckResponse;
import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.dto.learn.QuestionView;
import com.example.aichinese.entity.enums.AnswerKind;
import com.example.aichinese.entity.enums.LearnQuestionType;

/**
 * One question with its answer key, as the server grades it: a Learn question, a Practice item or a waiting mistake.
 * {@link #view} is what the student may see before answering; fields that would give the answer away for the
 * question type are left out.
 *
 * @param type Learn question type (the interface writes the instruction), or null when {@code prompt} is set
 * @param answer the right option text, or the tiles in order separated by spaces
 */
public record GradableItem(
        AnswerKind answerKind,
        LearnQuestionType type,
        String prompt,
        String hanzi,
        String pinyin,
        String meaning,
        String audioText,
        List<ChoiceOption> options,
        List<String> tiles,
        String answer,
        String explanation) {

    public GradableItem {
        options = options == null ? List.of() : List.copyOf(options);
        tiles = tiles == null ? List.of() : List.copyOf(tiles);
    }

    public boolean isCorrect(String given) {
        return LearnAnswers.matches(answer, given);
    }

    /** The choices column of a mistake snapshot. */
    public String choicesJson() {
        return answerKind == AnswerKind.BUILD ? LearnAnswers.writeTiles(tiles) : LearnAnswers.writeOptions(options);
    }

    public QuestionView view(Long id) {
        boolean showHanzi = true;
        boolean showPinyin = true;
        boolean showMeaning = true;
        boolean showAudio = true;
        if (type != null) {
            switch (type) {
                case MEANING -> showMeaning = false;
                case PINYIN -> {
                    showPinyin = false;
                    showMeaning = false;
                    showAudio = false;
                }
                case HANZI, TRANSLATE_BUILD -> {
                    showHanzi = false;
                    showPinyin = false;
                    showAudio = false;
                }
                case LISTEN_CHOOSE, LISTEN_BUILD -> {
                    showHanzi = false;
                    showPinyin = false;
                    showMeaning = false;
                }
            }
        }
        return new QuestionView(
                id,
                answerKind,
                type,
                prompt,
                showHanzi ? hanzi : null,
                showPinyin ? pinyin : null,
                showMeaning ? meaning : null,
                showAudio ? audioText : null,
                answerKind == AnswerKind.CHOICE ? options : List.of(),
                answerKind == AnswerKind.BUILD ? tiles : List.of());
    }

    /** The verdict on {@code given}, with the right answer as the student reads it. */
    public AnswerCheckResponse check(String given) {
        String shown = answerKind == AnswerKind.BUILD ? buildShown() : answer;
        String shownPinyin = pinyin != null ? pinyin : LearnAnswers.pinyinOf(options, answer);
        return new AnswerCheckResponse(isCorrect(given), shown, shownPinyin, meaning, explanation);
    }

    /** A built sentence shows as written: "你叫什么名字？" when the hanzi carries the punctuation. */
    private String buildShown() {
        String joined = LearnAnswers.joinTiles(answer);
        return hanzi != null && LearnAnswers.matches(joined, hanzi) ? hanzi : joined;
    }
}
