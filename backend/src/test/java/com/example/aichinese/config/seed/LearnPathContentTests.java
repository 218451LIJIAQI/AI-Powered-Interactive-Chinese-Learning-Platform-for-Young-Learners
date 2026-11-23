package com.example.aichinese.config.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.aichinese.config.seed.LearnPathContent.LessonSpec;
import com.example.aichinese.config.seed.LearnPathContent.Sentence;
import com.example.aichinese.config.seed.LearnPathContent.UnitSpec;
import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.LearnQuestionType;
import com.example.aichinese.entity.enums.PublishStatus;
import com.example.aichinese.service.learn.LearnAnswers;

/** The path against the prototype's "Library · Curriculum": its shape, and exercises built only from what was taught. */
class LearnPathContentTests {

    private static final List<UnitSpec> PATH = LearnPathContent.units();

    @Test
    void thePathHasTheUnitsAndLessonCountsOfTheCurriculum() {
        assertThat(PATH).extracting(unit -> unit.title() + " · " + unit.titleZh()).containsExactly(
                "Pinyin and tones · 拼音",
                "Characters and first words · 汉字",
                "First sentences · 是 不 吗",
                "Questions and counting · 什么 个",
                "Time and completed actions · 了 以后",
                "Comparing · 比 更 最",
                "Actions and experience · 正在 过",
                "Abilities and reasons · 会 能 因为");
        // Lessons (reviews included) and, apart, the optional bonus chests, as the path counts them.
        assertThat(PATH).extracting(LearnPathContentTests::lessonCount).containsExactly(7, 5, 7, 6, 5, 5, 5, 5);
        assertThat(PATH).extracting(unit -> kindCount(unit, LearnLessonKind.BONUS)).containsExactly(1, 1, 1, 0, 1, 1, 0, 1);
        assertThat(PATH).extracting(UnitSpec::level).containsExactly(1, 1, 1, 1, 2, 2, 2, 2);
        assertThat(PATH).extracting(UnitSpec::status).containsExactly(
                PublishStatus.PUBLISHED, PublishStatus.PUBLISHED, PublishStatus.PUBLISHED, PublishStatus.PUBLISHED,
                PublishStatus.PUBLISHED, PublishStatus.PUBLISHED, PublishStatus.DRAFT, PublishStatus.DRAFT);
        assertThat(PATH.stream().mapToInt(LearnPathContentTests::lessonCount).sum()).isEqualTo(45);
        assertThat(PATH.subList(0, 4).stream().mapToInt(LearnPathContentTests::lessonCount).sum()).isEqualTo(25);
        assertThat(PATH.get(3).lessons().getLast().kind()).isEqualTo(LearnLessonKind.CHECKPOINT);
    }

    @Test
    void levelOneLessonsAreThoseOfTheTable() {
        assertThat(titles(0)).containsExactly("The four tones · 声调", "Simple finals · a o e i u ü",
                "Initials 1 · b p m f and more", "Initials 2 · zh ch sh and more", "Bonus chest",
                "Compound finals · ai ei ao ou", "Nasal finals · an en ang eng", "Unit 1 review");
        assertThat(titles(1)).containsExactly("Strokes and order · 笔画", "First words · 你好 谢谢 再见", "Pronouns · 我 你 他",
                "Numbers 1–10 · 一 二 三", "Bonus chest", "Unit 2 review");
        assertThat(titles(2)).containsExactly("Word order · 我 喝 水", "To be, to be called · 是 叫", "Saying no · 不",
                "Questions · 吗 呢", "Have and my · 有 没 的", "Want · 想 要", "Bonus chest", "Unit 3 review");
        assertThat(titles(3)).containsExactly("Describing · 很 + adjective", "Question words · 什么 谁 哪儿 在",
                "Numbers and time · 11–99", "Measure words · 个 杯 本", "How many · 几 多少", "Unit 4 review", "Level 1 checkpoint");
    }

    @Test
    void everyQuestionHasItsAnswerAmongDifferentOptions() {
        for (UnitSpec unit : PATH) {
            for (LessonSpec lesson : unit.lessons()) {
                List<LearnQuestionGenerator.Question> questions = LearnQuestionGenerator.questionsFor(unit, lesson, PATH);
                String where = lesson.title() + " of unit " + unit.number();
                assertThat(questions).as(where).isNotEmpty();
                if (lesson.kind() == LearnLessonKind.CHECKPOINT) {
                    assertThat(questions).as(where).hasSize(10);
                } else if (lesson.kind() == LearnLessonKind.REVIEW) {
                    assertThat(questions).as(where).hasSize(8);
                } else {
                    assertThat(questions.size()).as(where).isBetween(5, 8);
                }
                for (LearnQuestionGenerator.Question question : questions) {
                    assertThat(question.explanation()).as(where).isNotBlank();
                    assertThat(question.audioText()).as(where).isNotBlank();
                    if (question.type().buildsFromTiles()) {
                        List<String> tiles = LearnAnswers.readTiles(question.choices());
                        assertThat(tiles).as(where + ": " + question.answer())
                                .containsAll(LearnAnswers.tiles(question.answer())).doesNotHaveDuplicates();
                    } else {
                        List<String> options = LearnAnswers.readOptions(question.choices()).stream().map(ChoiceOption::text).toList();
                        assertThat(options).as(where + ": " + question.answer())
                                .contains(question.answer()).doesNotHaveDuplicates();
                        assertThat(options.size()).as(where + ": " + question.answer()).isBetween(2, 4);
                    }
                }
            }
        }
    }

    @Test
    void pronunciationLessonsAskOnlyForPinyinAndWhatYouHear() {
        UnitSpec unit = PATH.get(0);
        for (LessonSpec lesson : unit.lessons()) {
            List<LearnQuestionGenerator.Question> questions = LearnQuestionGenerator.questionsFor(unit, lesson, PATH);
            for (LearnQuestionGenerator.Question question : questions) {
                assertThat(question.type()).as(lesson.title())
                        .isIn(LearnQuestionType.PINYIN, LearnQuestionType.LISTEN_CHOOSE, LearnQuestionType.LISTEN_BUILD);
            }
            if (lesson.kind() == LearnLessonKind.LESSON || lesson.kind() == LearnLessonKind.BONUS) {
                assertThat(questions).as(lesson.title())
                        .extracting(question -> question.type() + ":" + question.hanzi())
                        .doesNotHaveDuplicates();
            }
        }
        LessonSpec initials = unit.lessons().get(2);
        LearnQuestionGenerator.Question sample = LearnQuestionGenerator.questionsFor(unit, initials, PATH).get(0);
        assertThat(sample.type()).isEqualTo(LearnQuestionType.PINYIN);
        assertThat(sample.hanzi()).isEqualTo("爸");
        assertThat(LearnAnswers.readOptions(sample.choices())).extracting(ChoiceOption::text).containsExactly("bà", "pà", "mà");
    }

    /** Every tile of every sentence is a word taught at or before that stop (excluding skipped bonus chests). */
    @Test
    void sentencesUseOnlyWhatWasTaught() {
        Set<Integer> mandatoryCharacters = new HashSet<>();
        for (UnitSpec unit : PATH) {
            for (LessonSpec lesson : unit.lessons()) {
                Set<Integer> allowedCharacters = new HashSet<>(mandatoryCharacters);
                lesson.words().forEach(word -> word.hanzi().codePoints().forEach(allowedCharacters::add));
                if (lesson.kind() != LearnLessonKind.BONUS) {
                    mandatoryCharacters.addAll(allowedCharacters);
                }
                for (Sentence sentence : lesson.sentences()) {
                    for (String tile : LearnAnswers.tiles(sentence.tiles())) {
                        String clean = tile.replace("，", "");
                        boolean latin = clean.chars().allMatch(Character::isLetter)
                                && clean.chars().noneMatch(letter -> Character.UnicodeScript.of(letter) == Character.UnicodeScript.HAN);
                        boolean known = clean.codePoints().allMatch(allowedCharacters::contains);
                        assertThat(latin || known).as("“" + tile + "” in " + sentence.tiles() + " (" + lesson.title() + ")").isTrue();
                    }
                }
            }
        }
    }

    /** A distractor is never something taught after the stop (or in a skipped optional bonus chest). */
    @Test
    void distractorsComeOnlyFromWhatWasTaught() {
        Set<String> mandatoryHanzi = new HashSet<>();
        Set<String> mandatoryMeanings = new HashSet<>();
        Set<String> mandatoryPinyins = new HashSet<>();
        Map<String, Set<String>> mandatorySoundDrillPinyins = new HashMap<>();
        for (UnitSpec unit : PATH) {
            for (LessonSpec lesson : unit.lessons()) {
                Set<String> allowedHanzi = new HashSet<>(mandatoryHanzi);
                Set<String> allowedMeanings = new HashSet<>(mandatoryMeanings);
                Set<String> allowedPinyins = new HashSet<>(mandatoryPinyins);
                Map<String, Set<String>> allowedSoundDrillPinyins = new HashMap<>(mandatorySoundDrillPinyins);
                lesson.words().forEach(word -> {
                    allowedHanzi.add(word.hanzi());
                    allowedMeanings.add(word.meaning());
                    allowedPinyins.add(word.pinyin());
                    if (word.isSoundDrill()) {
                        Set<String> curated = new HashSet<>(word.wrongPinyin());
                        curated.add(word.pinyin());
                        allowedSoundDrillPinyins.put(word.hanzi(), curated);
                    }
                });
                lesson.sentences().forEach(sentence -> allowedMeanings.add(sentence.meaning()));
                if (lesson.kind() != LearnLessonKind.BONUS) {
                    mandatoryHanzi.addAll(allowedHanzi);
                    mandatoryMeanings.addAll(allowedMeanings);
                    mandatoryPinyins.addAll(allowedPinyins);
                    mandatorySoundDrillPinyins.putAll(allowedSoundDrillPinyins);
                }

                for (LearnQuestionGenerator.Question question : LearnQuestionGenerator.questionsFor(unit, lesson, PATH)) {
                    String context = unit.number() + ":" + lesson.title() + " (" + question.type() + ")";
                    if (question.type().buildsFromTiles()) {
                        if (!lesson.isPronunciation()) {
                            Set<String> answerTiles = new HashSet<>(LearnAnswers.tiles(question.answer()));
                            List<String> extraTiles = LearnAnswers.readTiles(question.choices()).stream()
                                    .filter(tile -> !answerTiles.contains(tile))
                                    .toList();
                            assertThat(extraTiles).as(context).allMatch(allowedHanzi::contains);
                        }
                        continue;
                    }
                    List<ChoiceOption> options = LearnAnswers.readOptions(question.choices());
                    assertThat(options.stream().filter(option -> option.pinyin() != null).map(ChoiceOption::text))
                            .as(context).allMatch(allowedHanzi::contains);
                    if (question.type() == LearnQuestionType.MEANING) {
                        assertThat(options).as(context).extracting(ChoiceOption::text).allMatch(allowedMeanings::contains);
                    } else if (question.type() == LearnQuestionType.PINYIN) {
                        Set<String> curated = allowedSoundDrillPinyins.get(question.hanzi());
                        Set<String> validPinyins = curated != null ? curated : allowedPinyins;
                        assertThat(options).as(context).extracting(ChoiceOption::text).allMatch(validPinyins::contains);
                    }
                }
            }
        }
    }

    @Test
    void aListeningQuestionNeverOffersAWordThatSoundsTheSame() {
        for (UnitSpec unit : PATH) {
            for (LessonSpec lesson : unit.lessons()) {
                for (LearnQuestionGenerator.Question question : LearnQuestionGenerator.questionsFor(unit, lesson, PATH)) {
                    if (question.type() == LearnQuestionType.LISTEN_CHOOSE) {
                        List<String> heard = LearnAnswers.readOptions(question.choices()).stream()
                                .map(option -> option.pinyin() != null ? option.pinyin() : option.text())
                                .toList();
                        assertThat(heard).as(lesson.title() + ": " + question.answer()).doesNotHaveDuplicates();
                    }
                }
            }
        }
    }

    /** A sentence reads as its tiles joined by the shared rule (LearnAnswers.joinTiles), then its punctuation. */
    @Test
    void aSentenceIsItsJoinedTilesAndItsPunctuation() {
        assertThat(new Sentence("我 叫 Amira", "。", null, null, null).hanzi()).isEqualTo("我叫 Amira。");
        assertThat(new Sentence("教室 在 2 楼", "。", null, null, null).hanzi()).isEqualTo("教室在2楼。");
        assertThat(new Sentence("你 叫 什么 名字", "？", null, null, null).hanzi()).isEqualTo("你叫什么名字？");
    }

    private static int lessonCount(UnitSpec unit) {
        return (int) unit.lessons().stream()
                .filter(lesson -> lesson.kind() == LearnLessonKind.LESSON || lesson.kind() == LearnLessonKind.REVIEW)
                .count();
    }

    private static int kindCount(UnitSpec unit, LearnLessonKind kind) {
        return (int) unit.lessons().stream().filter(lesson -> lesson.kind() == kind).count();
    }

    private static List<String> titles(int unit) {
        return PATH.get(unit).lessons().stream().map(LessonSpec::title).toList();
    }
}
