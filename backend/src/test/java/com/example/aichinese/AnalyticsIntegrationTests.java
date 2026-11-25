package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static com.example.aichinese.support.LearningApiTestClient.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.aichinese.config.PlatformProperties;
import com.example.aichinese.dto.admin.AdminAnalyticsResponse;
import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.repository.AiResponseReportRepository;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.service.admin.AdminOverviewService;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.support.SupportService;
import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.LearningApiTestClient.PublishedClass;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.EntityManager;

/**
 * MockMvc coverage of the admin analytics: the chosen period, counts by status and role, lessons completed in the
 * period, and the role checks (anonymous and wrong-role requests are rejected).
 */
class AnalyticsIntegrationTests extends ApiIntegrationTest {

    private static final String ADMIN_ANALYTICS_PATH = "/api/v1/admin/analytics";

    @Autowired
    private LearnClock learnClock;

    @Autowired
    private LessonProgressRepository lessonProgressRepository;

    @Autowired
    private SupportService supportService;

    @Autowired
    private AiResponseReportRepository aiResponseReportRepository;

    @Autowired
    private AiUsageLogRepository aiUsageLogRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private CoursePackageRepository coursePackageRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void adminAnalyticsCoversTheChosenPeriodAndEveryStatus() throws Exception {
        String adminToken = api.adminToken();

        mockMvc.perform(api.authGet(ADMIN_ANALYTICS_PATH + "?days=30", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(30))
                .andExpect(jsonPath("$.data.registrations.length()").value(30))
                .andExpect(jsonPath("$.data.contentByStatus.PUBLISHED").isNumber())
                .andExpect(jsonPath("$.data.contentByStatus.DRAFT").isNumber())
                .andExpect(jsonPath("$.data.aiRequestsByStatus.SUCCESS").isNumber())
                .andExpect(jsonPath("$.data.learning.totalStudents").isNumber());
        mockMvc.perform(api.authGet(ADMIN_ANALYTICS_PATH + "?days=12", adminToken))
                .andExpect(jsonPath("$.data.days").value(7));
    }

    @Test
    void adminAnalyticsCountsNewAccountsByRoleAndDay() throws Exception {
        String adminToken = api.adminToken();
        JsonNode baseline = analyticsData(adminToken);

        String email = uniqueEmail("student-admin-aggregate");
        api.registerStudentAccount(email);
        JsonNode after = analyticsData(adminToken);

        JsonNode users = after.path("numbers").path("users");
        assertThat(users.path("students").asLong())
                .isEqualTo(baseline.path("numbers").path("users").path("students").asLong() + 1L);
        assertThat(users.path("admins").asLong()).isPositive();
        assertThat(users.path("total").asLong()).isEqualTo(baseline.at("/numbers/users/total").asLong() + 1);
        assertThat(after.path("newRegistrations").asLong()).isEqualTo(baseline.path("newRegistrations").asLong() + 1L);
        JsonNode today = lastDay(after);
        // The platform day, like streaks and daily goals (AdminOverviewServiceTests covers another server zone).
        assertThat(today.path("date").asText()).isEqualTo(learnClock.today().toString());
        assertThat(today.path("count").asLong()).isPositive();

        Long id = user(email).getId();
        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'ADMIN'", id);
        JsonNode bothRoles = analyticsData(adminToken);
        assertThat(bothRoles.at("/numbers/users/total")).isEqualTo(users.path("total"));
        assertThat(bothRoles.at("/numbers/users/admins").asLong()).isEqualTo(users.path("admins").asLong() + 1);
        assertThat(bothRoles.path("newRegistrations")).isEqualTo(after.path("newRegistrations"));

        // V3 retained former teacher accounts for their history after removing their only role.
        jdbc.update("delete from user_roles where user_id = ?", id);
        jdbc.update("update users set active = false where id = ?", id);
        JsonNode retired = analyticsData(adminToken);
        assertThat(retired.at("/numbers/users")).isEqualTo(baseline.at("/numbers/users"));
        assertThat(retired.path("newRegistrations")).isEqualTo(baseline.path("newRegistrations"));
    }

    /** A lesson finished weeks ago and revisited today is not one of this week's completed lessons. */
    @Test
    void aLessonCountsInThePeriodItWasCompletedNotWhenItIsRevisited() throws Exception {
        String adminToken = api.adminToken();
        PublishedClass published = api.publishedClass(adminToken, "Analytics lesson " + uniqueSuffix());
        String studentEmail = uniqueEmail("analytics-lesson-student");
        String studentToken = api.registerStudent(studentEmail);
        api.join(studentToken, published.classId());
        long before = analyticsData(adminToken).path("learning").path("lessonsCompleted").asLong();

        String flashcards = completeLesson(studentToken, published);
        assertThat(analyticsData(adminToken).path("learning").path("lessonsCompleted").asLong()).isEqualTo(before + 1);

        // It was completed three weeks ago; today the student goes through its flashcards again.
        Long studentId = studentProfile(studentEmail).getId();
        LocalDateTime threeWeeksAgo = LocalDateTime.now().minusWeeks(3).withNano(0);
        Timestamp backdated = Timestamp.valueOf(threeWeeksAgo);
        int updated = jdbc.update(
                "update lesson_progress set completed_at = ?, updated_at = ?, last_accessed_at = ? where student_id = ? and lesson_id = ?",
                backdated, backdated, backdated, studentId, published.lessonId());
        assertThat(updated).isEqualTo(1);
        mockMvc.perform(api.authPost(flashcards, studentToken)).andExpect(status().isOk());

        LessonProgress revisited = lessonProgressRepository
                .findByStudentIdAndLessonId(studentId, published.lessonId()).orElseThrow();
        assertThat(revisited.getUpdatedAt()).isAfter(threeWeeksAgo.plusWeeks(2));
        assertThat(revisited.getCompletedAt()).isEqualTo(threeWeeksAgo);
        assertThat(analyticsData(adminToken).path("learning").path("lessonsCompleted").asLong()).isEqualTo(before);
    }

    /** Learning on the Learn path is learning: the student is active and the lesson is one of the period's lessons. */
    @Test
    void aLearnPathLessonMakesTheStudentActiveAndCountsAsACompletedLesson() throws Exception {
        String adminToken = api.adminToken();
        JsonNode before = analyticsData(adminToken).path("learning");
        String studentToken = api.registerStudent(uniqueEmail("analytics-path-student"));
        LearnLesson first = learnLesson("The four tones · 声调");

        mockMvc.perform(api.jsonPost("/api/v1/students/learn/lessons/" + first.getId() + "/complete", studentToken,
                        Map.of("answers", rightAnswers(first))))
                .andExpect(status().isOk());

        JsonNode after = analyticsData(adminToken).path("learning");
        assertThat(after.path("activeStudents").asLong()).isEqualTo(before.path("activeStudents").asLong() + 1);
        assertThat(after.path("lessonsCompleted").asLong()).isEqualTo(before.path("lessonsCompleted").asLong() + 1);
    }

    /** The working copy of a published lesson is a change to that lesson, not a second (draft) lesson. */
    @Test
    void contentByStatusCountsALessonWithUnpublishedChangesOnce() throws Exception {
        String adminToken = api.adminToken();
        PublishedClass published = api.publishedClass(adminToken, "Analytics revision " + uniqueSuffix());
        JsonNode before = analyticsData(adminToken).path("contentByStatus");

        mockMvc.perform(api.jsonPut("/api/v1/admin/lessons/" + published.lessonId() + "/content", adminToken, Map.of(
                        "title", "Revised lesson",
                        "keyPhrases", List.of(Map.of("chinese", "你好", "pinyin", "nǐ hǎo", "meaning", "hello")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.hasUnpublishedChanges").value(true));

        JsonNode after = analyticsData(adminToken).path("contentByStatus");
        for (String status : List.of("DRAFT", "PUBLISHED", "ARCHIVED")) {
            assertThat(after.path(status).asLong()).as(status).isEqualTo(before.path(status).asLong());
        }
    }

    /** Learning activity counts the students that still exist, like the total it is shown against. */
    @Test
    void aDeletedStudentIsNoLongerAnActiveStudent() throws Exception {
        String adminToken = api.adminToken();
        PublishedClass published = api.publishedClass(adminToken, "Analytics deleted " + uniqueSuffix());
        String email = uniqueEmail("analytics-deleted-student");
        String studentToken = api.registerStudent(email);
        api.join(studentToken, published.classId());
        api.submitQuiz(studentToken, published.quizId(), true, "wrong");
        JsonNode active = analyticsData(adminToken).path("learning");

        mockMvc.perform(api.jsonPost("/api/v1/users/me/delete", studentToken, Map.of("confirmEmail", email)))
                .andExpect(status().isOk());

        JsonNode afterDeletion = analyticsData(adminToken).path("learning");
        assertThat(afterDeletion.path("totalStudents").asLong()).isEqualTo(active.path("totalStudents").asLong() - 1);
        assertThat(afterDeletion.path("activeStudents").asLong()).isEqualTo(active.path("activeStudents").asLong() - 1);
    }

    @Test
    void quizAverageWeightsAttemptsEquallyAndCountsSupportByStudent() throws Exception {
        String admin = api.adminToken();
        long classId = api.draftClass(admin, "Quiz analytics " + uniqueSuffix());
        long lessonId = api.draftLesson(admin, api.unit(admin, classId, "Unit"), "Lesson");
        long quizId = api.data(mockMvc.perform(api.jsonPost("/api/v1/admin/lessons/" + lessonId + "/quizzes",
                        admin, Map.of("title", "Quiz")))
                .andExpect(status().isOk()).andReturn()).path("quizzes").get(0).path("id").asLong();
        String first = uniqueEmail("analytics-first");
        String second = uniqueEmail("analytics-second");
        api.registerStudentAccount(first);
        api.registerStudentAccount(second);
        long firstId = studentProfile(first).getId();
        long secondId = studentProfile(second).getId();

        // Use an isolated reporting week so other tests' attempts cannot change the rounded average.
        LocalDate day = LocalDate.of(2001, 6, 7);
        LearnClock weekClock = new LearnClock(
                new PlatformProperties("Asia/Singapore"),
                Clock.fixed(day.atTime(12, 0).atZone(ZoneId.of("Asia/Singapore")).toInstant(), ZoneOffset.UTC));
        AdminOverviewService isolatedOverview = new AdminOverviewService(
                supportService, aiResponseReportRepository, aiUsageLogRepository, userRepository,
                lessonRepository, coursePackageRepository, weekClock, entityManager);
        LocalDateTime completed = weekClock.startOf(day).plusHours(12);
        try {
            AdminAnalyticsResponse.Learning emptyWeek =
                    transactionTemplate.execute(status -> isolatedOverview.analytics(7).learning());
            assertThat(emptyWeek.quizAverage()).isNull();
            assertThat(emptyWeek.needsSupport()).isZero();
            Timestamp completedAt = Timestamp.valueOf(completed);
            jdbc.batchUpdate("""
                    insert into quiz_attempts (quiz_id, student_id, score, max_score, submitted, completed_at, created_at, updated_at)
                    values (?, ?, ?, ?, true, ?, ?, ?)
                    """, List.of(
                    new Object[]{quizId, firstId, 0, 5, completedAt, completedAt, completedAt},
                    new Object[]{quizId, firstId, 1, 5, completedAt, completedAt, completedAt},
                    new Object[]{quizId, secondId, 3, 3, completedAt, completedAt, completedAt}));

            AdminAnalyticsResponse.Learning weekLearning =
                    transactionTemplate.execute(status -> isolatedOverview.analytics(7).learning());
            assertThat(weekLearning.activeStudents()).isEqualTo(2);
            assertThat(weekLearning.quizAverage()).isEqualTo(40);
            assertThat(weekLearning.needsSupport()).isEqualTo(1);
        } finally {
            jdbc.update("delete from quiz_attempts where quiz_id = ?", quizId);
        }
    }

    /**
     * The registrations chart and its "new accounts" count the accounts that still exist, like the user totals beside
     * them: an account created this week and then deleted by its owner leaves all three.
     */
    @Test
    void aDeletedAccountLeavesTheRegistrationsLikeTheTotals() throws Exception {
        String adminToken = api.adminToken();
        String email = uniqueEmail("analytics-gone-signup");
        String studentToken = api.registerStudent(email);
        JsonNode registered = analyticsData(adminToken);

        mockMvc.perform(api.jsonPost("/api/v1/users/me/delete", studentToken, Map.of("confirmEmail", email)))
                .andExpect(status().isOk());

        JsonNode afterDeletion = analyticsData(adminToken);
        assertThat(afterDeletion.path("numbers").path("users").path("total").asLong())
                .isEqualTo(registered.path("numbers").path("users").path("total").asLong() - 1);
        assertThat(afterDeletion.path("newRegistrations").asLong())
                .isEqualTo(registered.path("newRegistrations").asLong() - 1);
        JsonNode lastDayAfterDeletion = lastDay(afterDeletion);
        assertThat(lastDayAfterDeletion.path("count").asLong()).isEqualTo(lastDay(registered).path("count").asLong() - 1);
        assertThat(lastDayAfterDeletion.path("date").asText()).isEqualTo(learnClock.today().toString());
    }

    private static JsonNode lastDay(JsonNode analytics) {
        JsonNode registrations = analytics.path("registrations");
        return registrations.get(registrations.size() - 1);
    }

    /**
     * Finishes every step of the published lesson ({@code readyLesson}: flashcards, tracing, matching and the quiz);
     * returns the flashcards' completion path.
     */
    private String completeLesson(String studentToken, PublishedClass published) throws Exception {
        String activities = "/api/v1/students/classes/activities/";
        JsonNode lesson = api.data(mockMvc.perform(
                        api.authGet("/api/v1/students/classes/lessons/" + published.lessonId(), studentToken))
                .andExpect(status().isOk())
                .andReturn());
        String flashcards = null;
        for (JsonNode activity : lesson.path("activities")) {
            String type = activity.path("type").asText();
            long id = activity.path("id").asLong();
            if (type.equals("MATCHING")) {
                JsonNode play = api.data(mockMvc.perform(api.authGet(activities + "MATCHING/" + id, studentToken))
                        .andExpect(status().isOk())
                        .andReturn());
                var pairs = play.path("words").valueStream()
                        .map(word -> Map.of("wordId", word.path("id").asLong(),
                                "meaning", word.path("chinese").asText().equals("你好") ? "hello" : "goodbye"))
                        .toList();
                mockMvc.perform(api.jsonPost(activities + "matching/" + id + "/check", studentToken, Map.of("pairs", pairs)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.done").value(true));
            } else {
                String path = activities + type + "/" + id + "/complete";
                mockMvc.perform(api.authPost(path, studentToken)).andExpect(status().isOk());
                if (type.equals("FLASHCARDS")) {
                    flashcards = path;
                }
            }
        }
        api.submitQuiz(studentToken, published.quizId(), true, "nǐ hǎo");
        mockMvc.perform(api.authGet("/api/v1/students/classes/lessons/" + published.lessonId(), studentToken))
                .andExpect(jsonPath("$.data.stepsDone").value(lesson.path("activities").size() + 1));
        return flashcards;
    }

    @Test
    void analyticsEndpointsShouldRejectUnauthorizedRequests() throws Exception {
        mockMvc.perform(get(ADMIN_ANALYTICS_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void analyticsEndpointsShouldRejectWrongRoles() throws Exception {
        String studentToken = api.registerStudent(uniqueEmail("student-wrong-role"));

        mockMvc.perform(api.authGet(ADMIN_ANALYTICS_PATH, studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    private JsonNode analyticsData(String adminToken) throws Exception {
        return api.data(mockMvc.perform(api.authGet(ADMIN_ANALYTICS_PATH, adminToken))
                .andExpect(status().isOk())
                .andReturn());
    }
}
