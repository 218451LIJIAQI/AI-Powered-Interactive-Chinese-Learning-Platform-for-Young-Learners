package com.example.aichinese.config.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.example.aichinese.config.seed.DemoClassContent.LessonSpec;
import com.example.aichinese.config.seed.DemoClassContent.Option;
import com.example.aichinese.config.seed.DemoClassContent.Question;
import com.example.aichinese.config.seed.DemoClassContent.UnitSpec;
import com.example.aichinese.config.seed.DemoClassContent.Word;

/** Every demo class lesson against the prototype's lesson rule. */
class DemoClassContentTests {

    private static final List<List<UnitSpec>> CLASSES = List.of(
            DemoClassContent.CAMPUS_LIFE,
            DemoClassContent.THROUGH_FILM,
            DemoClassContent.EVERYDAY_CONVERSATIONS,
            DemoClassContent.TRAVEL,
            DemoClassContent.SHOPPING_AND_PRICES,
            DemoClassContent.STUDY_ABROAD,
            DemoClassContent.BASIC_WORK,
            DemoClassContent.SERVING_CUSTOMERS);

    @Test
    void everyClassHasTwoUnitsAndFourToSixLessons() {
        assertThat(CLASSES).allSatisfy(units -> {
            assertThat(units).hasSize(2);
            assertThat(units.stream().mapToInt(unit -> unit.lessons().size()).sum()).isBetween(4, 6);
        });
        assertThat(DemoClassContent.CAMPUS_LIFE.stream().mapToInt(unit -> unit.lessons().size()).sum()).isEqualTo(5);
        assertThat(DemoClassContent.THROUGH_FILM.stream().mapToInt(unit -> unit.lessons().size()).sum()).isEqualTo(6);
        assertThat(DemoClassContent.EVERYDAY_CONVERSATIONS.stream().mapToInt(unit -> unit.lessons().size()).sum()).isEqualTo(4);
    }

    @Test
    void everyLessonFollowsTheLessonRule() {
        lessons().forEach(lesson -> {
            String where = lesson.title();
            assertThat(lesson.video()).as(where).isNotBlank();
            assertThat(lesson.videoSeconds()).as(where).isBetween(240, 480);
            assertThat(lesson.phrases().size()).as(where).isBetween(3, 5);
            assertThat(lesson.phrases().stream().filter(DemoClassContentTests::isSentence).count()).as(where).isGreaterThanOrEqualTo(2);
            assertThat(lesson.flashcards()).as(where).isNotBlank();
            assertThat(lesson.trace()).as(where).hasSize(2);
            assertThat(lesson.trace()).as(where).allSatisfy(word -> assertThat(word.chinese().codePointCount(0, word.chinese().length())).isEqualTo(1));
            assertThat(lesson.matching()).as(where).isNotBlank();
            assertThat(lesson.pairs()).as(where).hasSize(4);
            assertThat(lesson.pairs()).as(where).allSatisfy(pair -> assertThat(pair.pinyin()).isNotBlank());

            List<Question> quiz = DemoClassContent.quiz(lesson);
            assertThat(quiz).as(where).hasSize(5);
            assertThat(quiz.stream().filter(question -> question.type().equals("LISTEN_CHOOSE")).count()).as(where).isEqualTo(1);
            quiz.forEach(question -> {
                assertThat(question.options().size()).as(where + ": " + question.text()).isBetween(3, 4);
                assertThat(question.options().stream().filter(Option::correct).count()).as(where + ": " + question.text()).isEqualTo(1);
                assertThat(question.options().stream().map(Option::text).distinct().count())
                        .as(where + ": " + question.text()).isEqualTo(question.options().size());
                assertThat(question.explanation()).as(where + ": " + question.text()).isNotBlank();
                assertThat(question.spoken() != null).as(where + ": " + question.text()).isEqualTo(question.type().equals("LISTEN_CHOOSE"));
            });
        });
    }

    @Test
    void theRightAnswerIsNotAlwaysInTheSamePlace() {
        lessons().forEach(lesson -> assertThat(DemoClassContent.quiz(lesson).stream()
                        .map(question -> question.options().indexOf(
                                question.options().stream().filter(Option::correct).findFirst().orElseThrow()))
                        .distinct()
                        .count())
                .as(lesson.title())
                .isGreaterThan(1));
    }

    @Test
    void meetingClassmatesIsTheLessonOfThePrototype() {
        LessonSpec lesson = DemoClassContent.MEETING_CLASSMATES;
        assertThat(lesson.video()).isEqualTo("Meeting a classmate");
        assertThat(lesson.videoSeconds()).isEqualTo(375);
        assertThat(lesson.phrases()).extracting(Word::chinese)
                .containsExactly("你叫什么名字？", "我叫 Amira。", "你是学生吗？", "我是大学生。", "认识你很高兴。");
        assertThat(lesson.flashcards()).isEqualTo("Classmate Phrases");
        assertThat(lesson.matching()).isEqualTo("Match the Answers");
        Question reply = lesson.quiz().get(0);
        assertThat(reply.options()).extracting(Option::correct).containsExactly(false, true, false, false);
        assertThat(reply.hint()).isNotBlank();
    }

    private static boolean isSentence(Word phrase) {
        return phrase.chinese().endsWith("。") || phrase.chinese().endsWith("？") || phrase.chinese().endsWith("！");
    }

    private static Stream<LessonSpec> lessons() {
        return Stream.concat(
                CLASSES.stream().flatMap(List::stream).flatMap(unit -> unit.lessons().stream()),
                Stream.of(DemoClassContent.LANTERN_FESTIVAL));
    }
}
