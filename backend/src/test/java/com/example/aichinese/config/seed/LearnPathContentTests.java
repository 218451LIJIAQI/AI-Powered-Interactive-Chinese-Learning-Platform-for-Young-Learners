package com.example.aichinese.config.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        assertThat(PATH).extracting(LearnPathContentTests::lessonCount).containsExactly(8, 6, 8, 6, 6, 6, 5, 6);
        assertThat(PATH).extracting(UnitSpec::level).containsExactly(1, 1, 1, 1, 2, 2, 2, 2);
        assertThat(PATH).extracting(UnitSpec::status).containsExactly(
                PublishStatus.PUBLISHED, PublishStatus.PUBLISHED, PublishStatus.PUBLISHED, PublishStatus.PUBLISHED,
                PublishStatus.PUBLISHED, PublishStatus.PUBLISHED, PublishStatus.DRAFT, PublishStatus.DRAFT);
        assertThat(PATH.stream().mapToInt(LearnPathContentTests::lessonCount).sum()).isEqualTo(51);
        assertThat(PATH.get(3).lessons().get(PATH.get(3).lessons().size() - 1).kind()).isEqualTo(LearnLessonKind.CHECKPOINT);
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
                        assertThat(tiles).as(where + ": " + question.answer()).containsAll(LearnAnswers.tiles(question.answer()));
                        assertThat(new HashSet<>(tiles)).as(where).hasSameSizeAs(tiles);
                    } else {
                        List<String> options = LearnAnswers.readOptions(question.choices()).stream().map(ChoiceOption::text).toList();
                        assertThat(options).as(where + ": " + question.answer()).contains(question.answer());
                        assertThat(options.size()).as(where + ": " + question.answer()).isBetween(2, 4);
                        assertThat(new HashSet<>(options)).as(where).hasSameSizeAs(options);
                    }
                }
            }
        }
    }

    @Test
    void pronunciationLessonsAskOnlyForPinyinAndWhatYouHear() {
        UnitSpec unit = PATH.get(0);
        for (LessonSpec lesson : unit.lessons()) {
            for (LearnQuestionGenerator.Question question : LearnQuestionGenerator.questionsFor(unit, lesson, PATH)) {
                assertThat(question.type()).as(lesson.title())
                        .isIn(LearnQuestionType.PINYIN, LearnQuestionType.LISTEN_CHOOSE, LearnQuestionType.LISTEN_BUILD);
            }
        }
        LessonSpec initials = unit.lessons().get(2);
        LearnQuestionGenerator.Question sample = LearnQuestionGenerator.questionsFor(unit, initials, PATH).get(0);
        assertThat(sample.type()).isEqualTo(LearnQuestionType.PINYIN);
        assertThat(sample.hanzi()).isEqualTo("爸");
        assertThat(LearnAnswers.readOptions(sample.choices())).extracting(ChoiceOption::text).containsExactly("bà", "pà", "mà");
    }

    /** Every tile of every sentence is a word taught at or before that stop, or built from taught characters. */
    @Test
    void sentencesUseOnlyWhatWasTaught() {
        Set<String> taught = new HashSet<>();
        for (UnitSpec unit : PATH) {
            for (LessonSpec lesson : unit.lessons()) {
                lesson.words().forEach(word -> taught.add(word.hanzi()));
                Set<String> characters = taught.stream()
                        .flatMap(word -> word.codePoints().mapToObj(Character::toString))
                        .collect(Collectors.toSet());
                for (Sentence sentence : lesson.sentences()) {
                    for (String tile : LearnAnswers.tiles(sentence.tiles())) {
                        String clean = tile.replace("，", "");
                        boolean latin = clean.chars().allMatch(Character::isLetter)
                                && clean.chars().noneMatch(letter -> Character.UnicodeScript.of(letter) == Character.UnicodeScript.HAN);
                        boolean known = taught.contains(clean)
                                || clean.codePoints().mapToObj(Character::toString).allMatch(characters::contains);
                        assertThat(latin || known).as("“" + tile + "” in " + sentence.tiles() + " (" + lesson.title() + ")").isTrue();
                    }
                }
            }
        }
    }

    /** A distractor is never something taught after the stop that asks the question. */
    @Test
    void distractorsComeOnlyFromWhatWasTaught() {
        Set<String> taughtSoFar = new HashSet<>();
        for (UnitSpec unit : PATH) {
            for (LessonSpec lesson : unit.lessons()) {
                lesson.words().forEach(word -> taughtSoFar.add(word.hanzi()));
                for (LearnQuestionGenerator.Question question : LearnQuestionGenerator.questionsFor(unit, lesson, PATH)) {
                    // Options with pinyin are words; a sound drill's options are its own pinyin.
                    assertThat(LearnAnswers.readOptions(question.choices()).stream()
                            .filter(option -> option.pinyin() != null)
                            .map(ChoiceOption::text))
                            .as(lesson.title()).allMatch(taughtSoFar::contains);
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
                        assertThat(new HashSet<>(heard)).as(lesson.title() + ": " + question.answer()).hasSameSizeAs(heard);
                    }
                }
            }
        }
    }

    private static int lessonCount(UnitSpec unit) {
        return (int) unit.lessons().stream().filter(lesson -> lesson.kind() != LearnLessonKind.CHECKPOINT).count();
    }

    private static List<String> titles(int unit) {
        return PATH.get(unit).lessons().stream().map(LessonSpec::title).toList();
    }
}
