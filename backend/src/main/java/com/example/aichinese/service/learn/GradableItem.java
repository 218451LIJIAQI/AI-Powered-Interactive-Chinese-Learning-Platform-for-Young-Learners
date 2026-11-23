package com.example.aichinese.service.learn;

import static com.example.aichinese.entity.enums.LearnQuestionType.HANZI;
import static com.example.aichinese.entity.enums.LearnQuestionType.LISTEN_BUILD;
import static com.example.aichinese.entity.enums.LearnQuestionType.LISTEN_CHOOSE;
import static com.example.aichinese.entity.enums.LearnQuestionType.MEANING;
import static com.example.aichinese.entity.enums.LearnQuestionType.PINYIN;
import static com.example.aichinese.entity.enums.LearnQuestionType.TRANSLATE_BUILD;

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

    /** An item whose options (or tiles, for a sentence) are stored as one JSON column, as {@link #choicesJson} writes. */
    static GradableItem stored(AnswerKind answerKind, LearnQuestionType type, String prompt, String hanzi, String pinyin,
            String meaning, String audioText, String choicesJson, String answer, String explanation) {
        boolean build = answerKind == AnswerKind.BUILD;
        return new GradableItem(answerKind, type, prompt, hanzi, pinyin, meaning, audioText,
                build ? List.of() : LearnAnswers.readOptions(choicesJson),
                build ? LearnAnswers.readTiles(choicesJson) : List.of(),
                answer, explanation);
    }

    public boolean isCorrect(String given) {
        return LearnAnswers.matches(answer, given);
    }

    /** The same item with options that show no pinyin (a typing word's candidates: see PracticeCatalog.Typing). */
    GradableItem withoutOptionPinyin() {
        List<ChoiceOption> plain = options.stream().map(option -> new ChoiceOption(option.text(), null)).toList();
        return new GradableItem(answerKind, type, prompt, hanzi, pinyin, meaning, audioText, plain, tiles, answer,
                explanation);
    }

    /** The choices column of a mistake snapshot. */
    public String choicesJson() {
        return answerKind == AnswerKind.BUILD ? LearnAnswers.writeTiles(tiles) : LearnAnswers.writeOptions(options);
    }

    public QuestionView view(Long id) {
        // What each Learn question type shows (see LearnQuestionType); an item with its own prompt shows everything.
        boolean all = type == null;
        boolean showHanzi = all || type == MEANING || type == PINYIN;
        boolean showPinyin = all || type == MEANING;
        boolean showMeaning = all || type == HANZI || type == TRANSLATE_BUILD;
        boolean showAudio = all || type == MEANING || type == LISTEN_CHOOSE || type == LISTEN_BUILD;
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

    /**
     * The verdict on {@code given}, with the right answer as the student reads it. The pinyin shown is the right
     * option's own when it has one: the item's pinyin is the pinyin of the word asked about, which is another word
     * when a question shows Chinese and its answers are Chinese too ("大 (dà)": the answer is 小, xiǎo).
     */
    public AnswerCheckResponse check(String given) {
        String answerPinyin = LearnAnswers.pinyinOf(options, answer);
        String shownPinyin = answerPinyin != null ? answerPinyin : pinyin;
        return new AnswerCheckResponse(isCorrect(given), shown(answer), shownPinyin, meaning, explanation);
    }

    /**
     * An answer to this item (the right one or one given) as the student reads it, in the lesson verdict, the Mistakes
     * tab and the Mistakes review alike. A built sentence shows as written when its tiles make the hanzi ("你叫什么名字？",
     * a sound drill's spaced syllables), other tiles as {@link LearnAnswers#joinTiles} reads them.
     */
    public String shown(String text) {
        return shown(answerKind, hanzi, text);
    }

    /** Formats an answer without needing to parse a mistake row's stored choices JSON. */
    public static String shown(AnswerKind answerKind, String hanzi, String text) {
        if (answerKind != AnswerKind.BUILD || text == null) {
            return text;
        }
        String joined = LearnAnswers.joinTiles(text);
        return hanzi != null && LearnAnswers.matches(joined, hanzi) ? hanzi : joined;
    }
}
