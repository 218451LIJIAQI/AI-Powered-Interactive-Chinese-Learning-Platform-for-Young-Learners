package com.example.aichinese.service.learn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.dto.learn.HintDto.Step;
import com.example.aichinese.entity.enums.AnswerKind;
import com.example.aichinese.entity.enums.LearnQuestionType;

/**
 * Hints written from an item's own answer key, so a hint is never wrong and never gives the whole answer before the
 * last rung of its ladder. Each kind of item has a ladder from a light nudge to a strong one:
 * <ul>
 *   <li>a choice: rule out one wrong answer, then a clue (the explanation with the answer left out, or the first
 *   sound or letter of the answer), then only two answers left;</li>
 *   <li>listening: the first sound to listen for, then rule out one wrong answer, then two left;</li>
 *   <li>a sentence of tiles: the first word, the words that do not belong (or the second word), then all but the
 *   last word in order;</li>
 *   <li>pinyin typing: the first sound and how many syllables, the initials of every syllable, then the pinyin to
 *   type;</li>
 *   <li>matching: the pair of the word the student is stuck on;</li>
 *   <li>tracing: the strokes to write, then the stroke-order rules.</li>
 * </ul>
 * A student gets one hint per item ({@link #single}): the rung their level reaches, with the answers the lighter rungs
 * rule out; a ladder shorter than that gives its last rung.
 */
final class HintBuilder {

    private static final Pattern HAN = Pattern.compile("\\p{IsHan}");
    /** Where a clue leaves the answer out: "菜单 (càidān) is the ___." */
    private static final String BLANK = "___";

    private HintBuilder() {
    }

    /** The ladder of a choice or a sentence item; listening choices lead with the sound to listen for. */
    static List<Step> ladder(GradableItem item) {
        if (item.answerKind() == AnswerKind.BUILD) {
            return sentenceLadder(item);
        }
        // A Learn listening question, or a Practice listening question (audio and no characters).
        boolean listening = item.type() == LearnQuestionType.LISTEN_CHOOSE
                || (item.type() == null && item.audioText() != null && item.hanzi() == null);
        return choiceLadder(item, listening);
    }

    /** Pinyin typing: the sound of the answer, never the characters. */
    static List<Step> typingLadder(GradableItem item) {
        List<String> syllables = Pinyin.syllables(item.pinyin());
        if (syllables.isEmpty()) {
            return List.of();
        }
        List<String> initials = syllables.stream().map(HintBuilder::firstSound).toList();
        List<Step> rungs = new ArrayList<>();
        rungs.add(new Step("FIRST_SOUND", List.of(), List.of(), initials.get(0), syllables.size(), List.of()));
        if (syllables.size() > 1) {
            rungs.add(new Step("INITIALS", initials, List.of(), null, syllables.size(), List.of()));
        }
        rungs.add(new Step("TYPE_THIS", List.of(), List.of(), Pinyin.typed(item.pinyin()), syllables.size(), List.of()));
        return rungs;
    }

    /** Matching: the pair of the word the student is stuck on. */
    static List<Step> pairLadder(GradableItem item) {
        return List.of(new Step("PAIR", List.of(item.hanzi(), item.answer()),
                pinyinList(item.pinyin()), null, null, List.of()));
    }

    /** Tracing: how many strokes, then the order rules that hold for every character. */
    static List<Step> tracingLadder(String characters, Integer strokeCount) {
        return List.of(
                new Step("STROKE_COUNT", List.of(characters), List.of(), null, strokeCount, List.of()),
                new Step("STROKE_ORDER", List.of(), List.of(), null, null, List.of()));
    }

    /**
     * An item's one hint: rung {@code depth} of its ladder (the last one when the ladder is shorter), with every answer
     * the rungs up to it rule out. Null for an empty ladder.
     */
    static Step single(List<Step> ladder, int depth) {
        int reached = Math.min(ladder.size(), depth);
        if (reached < 1) {
            return null;
        }
        Step selected = ladder.get(reached - 1);
        if (reached == 1) {
            return selected;
        }
        List<String> ruledOut = ladder.subList(0, reached).stream()
                .flatMap(rung -> rung.ruledOut().stream())
                .distinct()
                .toList();
        return new Step(selected.kind(), selected.words(), selected.pinyin(), selected.text(), selected.count(), ruledOut);
    }

    // Ladders

    private static List<Step> choiceLadder(GradableItem item, boolean listening) {
        List<ChoiceOption> options = item.options();
        int answerIndex = LearnAnswers.indexOf(options, item.answer());
        if (answerIndex < 0) {
            return List.of();
        }
        ChoiceOption answer = options.get(answerIndex);
        List<String> wrong = options.stream().map(ChoiceOption::text)
                .filter(text -> !item.isCorrect(text)).distinct().toList();
        Step ruleOut = null;
        if (wrong.size() >= 2) {
            // The last wrong answer: the same one every time, so a second look gives the same first step.
            String out = wrong.getLast();
            ruleOut = new Step("RULE_OUT", List.of(out), pinyinList(LearnAnswers.pinyinOf(options, out)), null, null,
                    List.of(out));
        }
        Step clue = clue(item, answer, listening);
        List<Step> rungs = new ArrayList<>(listening ? Arrays.asList(clue, ruleOut) : Arrays.asList(ruleOut, clue));
        rungs.removeIf(Objects::isNull);
        if (rungs.isEmpty()) {
            Step fallback = fallbackClue(answer, listening);
            if (fallback != null) {
                rungs.add(fallback);
            }
        }
        if (wrong.size() >= 3) {
            // Keep the answer and the first wrong one; rule out the rest.
            List<String> rest = wrong.subList(1, wrong.size());
            String firstWrong = wrong.getFirst();
            List<String> left = answerIndex == 0
                    ? Arrays.asList(answer.text(), firstWrong)
                    : Arrays.asList(firstWrong, answer.text());
            rungs.add(new Step("TWO_LEFT", left, List.of(), null, null, rest));
        }
        return rungs;
    }

    /** Fallback rung for a valid 2-option Hanzi question that has neither pinyin nor a maskable explanation. */
    private static Step fallbackClue(ChoiceOption answer, boolean listening) {
        String text = answer.text() == null ? "" : answer.text().strip();
        if (text.codePointCount(0, text.length()) >= 2) {
            String first = Character.toString(text.codePointAt(0));
            return new Step("FIRST_LETTER", List.of(first), List.of(), null, text.codePointCount(0, text.length()),
                    List.of());
        }
        if (listening) {
            return new Step("LISTEN_SLOW", List.of(), List.of(), null, null, List.of());
        }
        return null;
    }

    /** The middle step of a choice: the explanation without the answer, else the answer's first sound or letter. */
    private static Step clue(GradableItem item, ChoiceOption answer, boolean listening) {
        String extraMaskPinyin = item.type() == LearnQuestionType.HANZI ? item.pinyin() : null;
        String masked = maskedExplanation(
                item.explanation(), answer, item.hanzi(), item.pinyin(), item.meaning(), extraMaskPinyin);
        String soundSource = answer.pinyin();
        if ((soundSource == null || soundSource.isBlank()) && (listening || item.type() == LearnQuestionType.HANZI)) {
            // Unit 1 Learn listening options carry pinyin directly in option text (or on the item) with no separate
            // pinyin field; Practice listening sets (type == null) often have English options that are not pinyin.
            soundSource = item.pinyin() != null && !item.pinyin().isBlank()
                    ? item.pinyin()
                    : (listening && item.type() == LearnQuestionType.LISTEN_CHOOSE
                            && answer.text() != null && !HAN.matcher(answer.text()).find() ? answer.text() : null);
        }
        List<String> syllables = Pinyin.syllables(soundSource);
        // Listening prefers the first sound to the explanation.
        if (masked != null && (!listening || syllables.isEmpty())) {
            return new Step("CLUE", List.of(), List.of(), masked, null, List.of());
        }
        if (!syllables.isEmpty()) {
            return new Step("FIRST_SOUND", List.of(), List.of(), firstSound(syllables.get(0)), syllables.size(),
                    List.of());
        }
        String text = answer.text() == null ? "" : answer.text().strip();
        if (text.isEmpty() || HAN.matcher(text).find()) {
            return null;
        }
        String firstLetter = Character.toString(text.codePointAt(0)).toUpperCase(Locale.ROOT);
        return new Step("FIRST_LETTER", List.of(firstLetter), List.of(), null,
                text.split("(?U)\\s+").length, List.of());
    }

    /**
     * The explanation with the answer (and its pinyin) replaced by {@link #BLANK}, when it names the answer and still
     * says something without it. Null otherwise: an explanation that does not name the answer may give it away in
     * other words, and one that is mostly the answer is no clue. Case is ignored beyond ASCII too, so "Café" or a
     * capital tone-marked "Ài" opening a sentence is masked like its lower-case spelling.
     */
    static String maskedExplanation(
            String explanation,
            ChoiceOption answer,
            String promptHanzi,
            String promptPinyin,
            String promptMeaning,
            String extraMaskPinyin) {
        if (explanation == null || explanation.isBlank() || answer.text() == null || answer.text().isBlank()) {
            return null;
        }
        Matcher answerInExplanation = anyCase(answer.text()).matcher(explanation);
        if (!answerInExplanation.find()) {
            return null;
        }
        String masked = answerInExplanation.replaceAll(BLANK);
        if (answer.pinyin() != null && !answer.pinyin().isBlank()) {
            masked = anyCase(answer.pinyin()).matcher(masked).replaceAll(BLANK);
        } else if (extraMaskPinyin != null && !extraMaskPinyin.isBlank()) {
            masked = anyCase(extraMaskPinyin).matcher(masked).replaceAll(BLANK);
        }
        // Exclude prompt fields already shown to the learner so vacuous templates ("茶 (chá) means “___”.") fall back
        // to FIRST_LETTER / FIRST_SOUND rather than returning an uninformative CLUE.
        String informative = masked.replace(BLANK, "");
        informative = removePromptField(informative, promptHanzi);
        informative = removePromptField(informative, promptPinyin);
        informative = removePromptField(informative, promptMeaning);
        String rest = informative.replaceAll("[\\s\\p{P}]", "");
        return rest.length() >= 8 ? masked.strip() : null;
    }

    private static String removePromptField(String text, String field) {
        if (field == null || field.isBlank()) {
            return text;
        }
        return anyCase(field).matcher(text).replaceAll("");
    }

    /** The text, stripped, as a case-insensitive literal with word boundaries on non-Han letter/digit edges. */
    private static Pattern anyCase(String text) {
        String stripped = text.strip();
        String pattern = Pattern.quote(stripped);
        if (!stripped.isEmpty()) {
            if (isWordEdge(stripped.codePointAt(0))) {
                pattern = "(?<![\\p{L}\\p{N}])" + pattern;
            }
            if (isWordEdge(stripped.codePointBefore(stripped.length()))) {
                pattern = pattern + "(?![\\p{L}\\p{N}])";
            }
        }
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static boolean isWordEdge(int codePoint) {
        return Character.isLetterOrDigit(codePoint) && Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.HAN;
    }

    private static List<Step> sentenceLadder(GradableItem item) {
        List<String> answer = LearnAnswers.tiles(item.answer());
        if (answer.size() < 2) {
            return List.of();
        }
        // The client disables tiles by text, so a spare copy of a required tile must stay available.
        List<String> extra = item.tiles().stream().filter(tile -> !answer.contains(tile)).distinct().toList();
        List<Step> rungs = new ArrayList<>();
        rungs.add(new Step("FIRST_TILE", List.of(answer.get(0)), List.of(), null, null, List.of()));
        if (!extra.isEmpty()) {
            rungs.add(new Step("LEAVE_OUT", extra, List.of(), null, null, extra));
        } else if (answer.size() > 3) {
            rungs.add(new Step("ORDER_START", List.copyOf(answer.subList(0, 2)), List.of(), null, null, List.of()));
        }
        if (answer.size() > 2) {
            rungs.add(new Step("ORDER_START", List.copyOf(answer.subList(0, answer.size() - 1)), List.of(), null, null,
                    List.of()));
        }
        return rungs;
    }

    /** A syllable's initial, or its first letter when it starts with a vowel. */
    private static String firstSound(String syllable) {
        String initial = Pinyin.initial(syllable);
        return initial.isEmpty() ? syllable.substring(0, 1) : initial;
    }

    private static List<String> pinyinList(String pinyin) {
        return pinyin == null || pinyin.isBlank() ? List.of() : List.of(pinyin);
    }
}
