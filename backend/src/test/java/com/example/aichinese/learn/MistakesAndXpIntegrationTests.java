package com.example.aichinese.learn;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

import com.example.aichinese.entity.DailyStreak;
import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LearnQuestion;
import com.example.aichinese.entity.PointsTransaction;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.repository.DailyStreakRepository;
import com.example.aichinese.repository.LearnLessonRepository;
import com.example.aichinese.repository.LearnQuestionRepository;
import com.example.aichinese.repository.PointsTransactionRepository;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.learn.XpService;
import com.example.aichinese.support.ApiIntegrationTest;

/** The Mistakes review, the daily goal, the streak and the Progress page. */
class MistakesAndXpIntegrationTests extends ApiIntegrationTest {

    @Autowired
    private LearnLessonRepository lessonRepository;

    @Autowired
    private LearnQuestionRepository questionRepository;

    @Autowired
    private PointsTransactionRepository pointsTransactionRepository;

    @Autowired
    private DailyStreakRepository dailyStreakRepository;

    @Autowired
    private LearnClock clock;

    @Test
    void aReviewClearsTheMistakesAnsweredRightAndPaysOnceADay() throws Exception {
        String token = api.registerStudent(uniqueEmail("mistakes-review"));
        List<LearnQuestion> questions = questions("Hello and goodbye");
        for (LearnQuestion question : questions.subList(0, 3)) {
            mockMvc.perform(api.jsonPost(lessonPath(question) + "/check", token,
                            Map.of("questionId", question.getId(), "answer", "wrong")))
                    .andExpect(jsonPath("$.data.correct").value(false));
        }
        mockMvc.perform(api.authGet("/api/v1/users/me/nav-counts", token))
                .andExpect(jsonPath("$.data.mistakes").value(3));

        JsonNode review = api.data(mockMvc.perform(api.authGet("/api/v1/students/mistakes/review", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.waiting").value(3))
                .andExpect(jsonPath("$.data.questions.length()").value(3))
                .andExpect(jsonPath("$.data.context[0].yourAnswer").value("wrong"))
                .andExpect(jsonPath("$.data.context[0].fromLearn").value(true))
                .andReturn());
        assertThat(review.path("questions").toString()).doesNotContain("\"answer\"");

        List<Long> mistakeIds = new ArrayList<>();
        review.path("questions").forEach(question -> mistakeIds.add(question.path("id").asLong()));
        mockMvc.perform(api.jsonPost("/api/v1/students/mistakes/review/check", token,
                        Map.of("questionId", mistakeIds.get(0), "answer", questions.get(0).getAnswer())))
                .andExpect(jsonPath("$.data.correct").value(true));

        // Two right, one still wrong: two cleared, one left, +10 XP.
        List<Map<String, Object>> answers = List.of(
                Map.of("questionId", mistakeIds.get(0), "answer", questions.get(0).getAnswer()),
                Map.of("questionId", mistakeIds.get(1), "answer", questions.get(1).getAnswer()),
                Map.of("questionId", mistakeIds.get(2), "answer", "still wrong"));
        mockMvc.perform(api.jsonPost("/api/v1/students/mistakes/review/complete", token, Map.of("answers", answers)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answered").value(3))
                .andExpect(jsonPath("$.data.cleared").value(2))
                .andExpect(jsonPath("$.data.left").value(1))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(10));
        mockMvc.perform(api.authGet("/api/v1/students/mistakes", token))
                .andExpect(jsonPath("$.data.all").value(1));

        // A second review the same day still clears, but review XP is paid once a day.
        mockMvc.perform(api.jsonPost("/api/v1/students/mistakes/review/complete", token, Map.of("answers", List.of(
                        Map.of("questionId", mistakeIds.get(2), "answer", questions.get(2).getAnswer())))))
                .andExpect(jsonPath("$.data.cleared").value(1))
                .andExpect(jsonPath("$.data.left").value(0))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(0));
    }

    @Test
    void studentsOnlySeeTheirOwnMistakes() throws Exception {
        String owner = api.registerStudent(uniqueEmail("mistakes-owner"));
        String other = api.registerStudent(uniqueEmail("mistakes-other"));
        LearnQuestion question = questions("Hello and goodbye").get(0);
        mockMvc.perform(api.jsonPost(lessonPath(question) + "/check", owner,
                Map.of("questionId", question.getId(), "answer", "wrong")));
        Long mistakeId = api.data(mockMvc.perform(api.authGet("/api/v1/students/mistakes", owner)).andReturn())
                .path("page").path("items").get(0).path("id").asLong();

        mockMvc.perform(api.authGet("/api/v1/students/mistakes", other))
                .andExpect(jsonPath("$.data.all").value(0));
        mockMvc.perform(api.jsonPost("/api/v1/students/mistakes/review/check", other,
                        Map.of("questionId", mistakeId, "answer", question.getAnswer())))
                .andExpect(status().isNotFound());
        mockMvc.perform(api.jsonPost("/api/v1/students/mistakes/review/complete", other, Map.of("answers", List.of(
                        Map.of("questionId", mistakeId, "answer", question.getAnswer())))))
                .andExpect(jsonPath("$.data.cleared").value(0));
        mockMvc.perform(api.authGet("/api/v1/students/mistakes", owner))
                .andExpect(jsonPath("$.data.all").value(1));
    }

    @Test
    void theDailyGoalIsTenTwentyOrThirtyAndALowerGoalCanBeReachedAtOnce() throws Exception {
        String token = api.registerStudent(uniqueEmail("daily-goal"));
        mockMvc.perform(api.authGet("/api/v1/students/daily-goal", token))
                .andExpect(jsonPath("$.data.dailyGoalXp").value(20));
        mockMvc.perform(api.jsonPut("/api/v1/students/daily-goal", token, Map.of("dailyGoalXp", 25)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Daily goal must be 10, 20 or 30 XP."));

        LearnLesson lesson = lesson("Hello and goodbye");
        mockMvc.perform(api.jsonPost("/api/v1/students/learn/lessons/" + lesson.getId() + "/complete", token,
                        Map.of("answers", answers(lesson))))
                .andExpect(jsonPath("$.data.xp.goalReached").value(false));
        mockMvc.perform(api.jsonPut("/api/v1/students/daily-goal", token, Map.of("dailyGoalXp", 10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyGoalXp").value(10));
        mockMvc.perform(api.authGet("/api/v1/students/today", token))
                .andExpect(jsonPath("$.data.dailyGoalXp").value(10))
                .andExpect(jsonPath("$.data.todayXp").value(15))
                .andExpect(jsonPath("$.data.goalReached").value(true))
                .andExpect(jsonPath("$.data.currentStreak").value(1))
                .andExpect(jsonPath("$.data.week.length()").value(7))
                .andExpect(jsonPath("$.data.week[6].goalReached").value(true))
                .andExpect(jsonPath("$.data.week[5].goalReached").value(false));
        mockMvc.perform(api.authGet("/api/v1/students/daily-goal", api.approvedTeacher(uniqueEmail("goal-teacher"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reachingTheGoalTheDayAfterExtendsTheStreakAndEarnsStreakExplorer() throws Exception {
        String email = uniqueEmail("streak");
        String token = api.registerStudent(email);
        StudentProfile student = studentProfile(email);
        LocalDate today = clock.today();
        // Goal reached on the two days before today.
        DailyStreak streak = new DailyStreak();
        streak.setStudent(student);
        streak.setCurrentStreak(2);
        streak.setBestStreak(2);
        streak.setLastLearningDate(today.minusDays(1));
        dailyStreakRepository.save(streak);
        for (int daysAgo = 2; daysAgo >= 1; daysAgo--) {
            PointsTransaction goal = new PointsTransaction();
            goal.setStudent(student);
            goal.setSourceType(XpService.SOURCE_DAILY_GOAL);
            goal.setSourceId(today.minusDays(daysAgo).toEpochDay());
            goal.setPointsDelta(5);
            goal.setReason("Daily goal reached");
            goal.setCreatedAt(clock.startOf(today.minusDays(daysAgo)).plusHours(12));
            pointsTransactionRepository.save(goal);
        }
        mockMvc.perform(api.authGet("/api/v1/students/today", token))
                .andExpect(jsonPath("$.data.currentStreak").value(2));

        mockMvc.perform(api.jsonPut("/api/v1/students/daily-goal", token, Map.of("dailyGoalXp", 10)));
        LearnLesson lesson = lesson("Hello and goodbye");
        mockMvc.perform(api.jsonPost("/api/v1/students/learn/lessons/" + lesson.getId() + "/complete", token,
                        Map.of("answers", answers(lesson))))
                .andExpect(jsonPath("$.data.xp.goalJustReached").value(true))
                .andExpect(jsonPath("$.data.xp.currentStreak").value(3))
                .andExpect(jsonPath("$.data.xp.newBadges").value(org.hamcrest.Matchers.hasItem("STREAK_EXPLORER")));

        JsonNode progress = api.data(mockMvc.perform(api.authGet("/api/v1/students/progress/overview", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStreak").value(3))
                .andExpect(jsonPath("$.data.bestStreak").value(3))
                .andExpect(jsonPath("$.data.sources.learn").value(10))
                .andExpect(jsonPath("$.data.sources.reviewsAndGoals").value(15))
                .andExpect(jsonPath("$.data.badges.length()").value(8))
                .andExpect(jsonPath("$.data.badges[0].key").value("FIRST_STEPS"))
                .andExpect(jsonPath("$.data.badges[0].earned").value(true))
                .andExpect(jsonPath("$.data.recent.length()").value(4))
                .andReturn());
        assertThat(progress.path("calendar").path("goalDays").toString()).contains(today.toString());
        mockMvc.perform(api.authGet("/api/v1/students/rewards", token))
                .andExpect(jsonPath("$.data.currentStreak").value(3));

        // A missed day breaks the current streak but keeps the best.
        DailyStreak stored = dailyStreakRepository.findByStudentId(student.getId()).orElseThrow();
        stored.setLastLearningDate(today.minusDays(2));
        dailyStreakRepository.save(stored);
        mockMvc.perform(api.authGet("/api/v1/students/today", token))
                .andExpect(jsonPath("$.data.currentStreak").value(0));
        mockMvc.perform(api.authGet("/api/v1/students/progress/overview", token))
                .andExpect(jsonPath("$.data.currentStreak").value(0))
                .andExpect(jsonPath("$.data.bestStreak").value(3));
    }

    @Test
    void progressOverviewStartsEmptyAndRejectsABadMonth() throws Exception {
        String token = api.registerStudent(uniqueEmail("progress-new"));
        mockMvc.perform(api.authGet("/api/v1/students/progress/overview", token))
                .andExpect(jsonPath("$.data.totalXp").value(0))
                .andExpect(jsonPath("$.data.level").value(1))
                .andExpect(jsonPath("$.data.nextLevelXp").value(100))
                .andExpect(jsonPath("$.data.recent.length()").value(0))
                .andExpect(jsonPath("$.data.calendar.previousMonth").doesNotExist())
                .andExpect(jsonPath("$.data.calendar.nextMonth").doesNotExist());
        mockMvc.perform(api.authGet("/api/v1/students/progress/overview", token).param("month", "May"))
                .andExpect(status().isBadRequest());
    }

    private String lessonPath(LearnQuestion question) {
        return "/api/v1/students/learn/lessons/" + question.getLesson().getId();
    }

    private LearnLesson lesson(String title) {
        return lessonRepository.findPathInOrder().stream()
                .filter(lesson -> lesson.getTitle().equals(title))
                .findFirst()
                .orElseThrow();
    }

    private List<LearnQuestion> questions(String lessonTitle) {
        LearnLesson lesson = lesson(lessonTitle);
        List<LearnQuestion> questions = questionRepository.findByLessonIdOrderByPositionAsc(lesson.getId());
        questions.forEach(question -> question.setLesson(lesson));
        return questions;
    }

    private List<Map<String, Object>> answers(LearnLesson lesson) {
        return questionRepository.findByLessonIdOrderByPositionAsc(lesson.getId()).stream()
                .map(question -> Map.<String, Object>of("questionId", question.getId(), "answer", question.getAnswer()))
                .toList();
    }
}
