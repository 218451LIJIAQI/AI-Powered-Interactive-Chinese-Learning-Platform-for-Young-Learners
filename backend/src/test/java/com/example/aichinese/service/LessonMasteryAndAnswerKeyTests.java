package com.example.aichinese.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.StoryQuestion;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LessonMastery;

/**
 * Unit rules for the D25 mastery indicator, the roster "needs support" rule and the server-side
 * activity answer keys (no answers are ever sent to students before an attempt).
 */
class LessonMasteryAndAnswerKeyTests {

    @Test
    void masteryFollowsProgressAndBestQuizPercentage() {
        assertThat(LessonMasteryCalculator.mastery(false, false, null)).isEqualTo(LessonMastery.NOT_STARTED);
        assertThat(LessonMasteryCalculator.mastery(true, false, null)).isEqualTo(LessonMastery.IN_PROGRESS);
        assertThat(LessonMasteryCalculator.mastery(false, false, 40)).isEqualTo(LessonMastery.IN_PROGRESS);
        assertThat(LessonMasteryCalculator.mastery(true, true, null)).isEqualTo(LessonMastery.COMPLETED);
        assertThat(LessonMasteryCalculator.mastery(true, true, 79)).isEqualTo(LessonMastery.COMPLETED);
        assertThat(LessonMasteryCalculator.mastery(true, true, 80)).isEqualTo(LessonMastery.MASTERED);

        LessonProgress completed = new LessonProgress();
        completed.setCompleted(true);
        assertThat(LessonMasteryCalculator.mastery(completed, 100)).isEqualTo(LessonMastery.MASTERED);
        assertThat(LessonMasteryCalculator.mastery((LessonProgress) null, null)).isEqualTo(LessonMastery.NOT_STARTED);
    }

    @Test
    void bestQuizPercentageAveragesEachPublishedQuizBestAttemptAndIgnoresArchivedQuizzes() {
        Lesson lesson = new Lesson();
        lesson.setId(7L);
        Quiz first = quiz(1L, lesson, ContentStatus.PUBLISHED);
        Quiz second = quiz(2L, lesson, ContentStatus.PUBLISHED);
        Quiz archived = quiz(3L, lesson, ContentStatus.ARCHIVED);

        Map<Long, Integer> best = LessonMasteryCalculator.bestQuizPercentageByLesson(List.of(
                attempt(first, 4, 10),
                attempt(first, 9, 10),
                attempt(second, 7, 10),
                attempt(archived, 0, 10)));

        // first quiz best 90 %, second 70 % -> 80 %; the archived quiz does not count.
        assertThat(best).containsEntry(7L, 80);
    }

    @Test
    void rosterSupportReasonsUseLowQuizAverageAndInactivityOnly() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 18, 12, 0);

        assertThat(TeacherStudentService.supportReasons(85.0, now.minusDays(1), now)).isEmpty();
        assertThat(TeacherStudentService.supportReasons(null, now.minusDays(2), now)).isEmpty();
        assertThat(TeacherStudentService.supportReasons(45.0, now.minusDays(1), now))
                .containsExactly(TeacherStudentService.SUPPORT_LOW_QUIZ_SCORE);
        assertThat(TeacherStudentService.supportReasons(90.0, now.minusDays(15), now))
                .containsExactly(TeacherStudentService.SUPPORT_INACTIVE);
        assertThat(TeacherStudentService.supportReasons(30.0, now.minusDays(30), now))
                .containsExactly(TeacherStudentService.SUPPORT_LOW_QUIZ_SCORE, TeacherStudentService.SUPPORT_INACTIVE);
    }

    @Test
    void storyOptionsAcceptArraysObjectsAndLinesAndKeepTheKeyServerSide() {
        assertThat(ActivityAnswerKeys.parseStoryOptions("[\"University\",\"Home\"]"))
                .extracting(ActivityAnswerKeys.StoryOption::text)
                .containsExactly("University", "Home");
        assertThat(ActivityAnswerKeys.parseStoryOptions("Rice\nTea"))
                .extracting(ActivityAnswerKeys.StoryOption::text)
                .containsExactly("Rice", "Tea");

        List<ActivityAnswerKeys.StoryOption> flagged = ActivityAnswerKeys.parseStoryOptions(
                "[{\"text\":\"老师好\"},{\"text\":\"再见\",\"correct\":true}]");
        StoryQuestion legacy = new StoryQuestion();
        assertThat(ActivityAnswerKeys.storyCorrectIndex(legacy, flagged)).isEqualTo(1);

        StoryQuestion withIndex = new StoryQuestion();
        withIndex.setCorrectOptionIndex(0);
        assertThat(ActivityAnswerKeys.storyCorrectIndex(withIndex, flagged)).isZero();
        withIndex.setCorrectOptionIndex(5);
        assertThat(ActivityAnswerKeys.storyCorrectIndex(withIndex, flagged)).isNull();
    }

    @Test
    void listeningPayloadIsParsedIntoQuestionsOrAFreeResponsePrompt() {
        ActivityAnswerKeys.ListeningKey structured = ActivityAnswerKeys.parseListening(
                "{\"prompt\":\"Listen.\",\"questions\":[{\"question\":\"What did you hear?\","
                        + "\"options\":[\"你好\",\"再见\"],\"answer\":\"你好\"}]}");
        assertThat(structured.structured()).isTrue();
        assertThat(structured.answerable()).isTrue();
        assertThat(structured.prompt()).isEqualTo("Listen.");
        assertThat(structured.questions().get(0).options()).containsExactly("你好", "再见");

        ActivityAnswerKeys.ListeningKey wrongKey = ActivityAnswerKeys.parseListening(
                "{\"question\":\"Q\",\"options\":[\"一\",\"二\"],\"answer\":\"三\"}");
        assertThat(wrongKey.structured()).isTrue();
        assertThat(wrongKey.answerable()).as("the answer must be one of the options").isFalse();

        ActivityAnswerKeys.ListeningKey legacy = ActivityAnswerKeys.parseListening("Listen for greetings: 你好, 再见.");
        assertThat(legacy.structured()).isFalse();
        assertThat(legacy.prompt()).isEqualTo("Listen for greetings: 你好, 再见.");

        assertThat(ActivityAnswerKeys.answerMatches("你好", " 你好 ")).isTrue();
        assertThat(ActivityAnswerKeys.answerMatches("Greetings", "greetings")).isTrue();
        assertThat(ActivityAnswerKeys.answerMatches("你好", null)).isFalse();
    }

    private static Quiz quiz(Long id, Lesson lesson, ContentStatus status) {
        Quiz quiz = new Quiz();
        quiz.setId(id);
        quiz.setLesson(lesson);
        quiz.setContentStatus(status);
        return quiz;
    }

    private static QuizAttempt attempt(Quiz quiz, int score, int maxScore) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setSubmitted(true);
        attempt.setScore(score);
        attempt.setMaxScore(maxScore);
        return attempt;
    }
}
