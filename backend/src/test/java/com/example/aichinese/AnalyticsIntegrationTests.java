package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;

/**
 * End-to-end MockMvc coverage of the three persona analytics
 * surfaces (student / teacher / admin): time-series trends, breakdown
 * aggregates, and per-persona authorization scope.
 *
 * <p>Pins the per-persona role checks: each analytics path accepts only
 * its own role and rejects anonymous and wrong-role requests.</p>
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsIntegrationTests {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "SuperSecure123!";
    private static final String USER_PASSWORD = "Password123!";
    private static final String STUDENT_ANALYTICS_PATH = "/api/v1/students/analytics";
    private static final String TEACHER_ANALYTICS_PATH = "/api/v1/teachers/analytics";
    private static final String ADMIN_ANALYTICS_PATH = "/api/v1/admin/analytics";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Test
    void studentAnalyticsShouldReturnOwnedSummary() throws Exception {
        String studentToken = registerAndLoginStudent(uniqueEmail("student-analytics"));

        mockMvc.perform(get(STUDENT_ANALYTICS_PATH)
                .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.overview.completedLessons").isNumber())
                .andExpect(jsonPath("$.data.overview.averageQuizScore").exists())
                .andExpect(jsonPath("$.data.quizScoreTrend").isArray())
                .andExpect(jsonPath("$.data.lessonStatusBreakdown").isArray());
    }

    @Test
    void teacherAnalyticsShouldReturnOwnedSummary() throws Exception {
        String teacherToken = registerAndLoginTeacher(uniqueEmail("teacher-analytics"));

        mockMvc.perform(get(TEACHER_ANALYTICS_PATH)
                .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.overview.totalLessons").isNumber())
                .andExpect(jsonPath("$.data.overview.averageQuizScore").isNumber())
                .andExpect(jsonPath("$.data.overview.netEarnings").doesNotExist())
                .andExpect(jsonPath("$.data.earningsTrend").doesNotExist())
                .andExpect(jsonPath("$.data.contentStatusBreakdown").isArray());
    }

    @Test
    void pendingTeacherCannotOpenLearnerAnalytics() throws Exception {
        String teacherToken = registerAndLoginPendingTeacher(uniqueEmail("pending-teacher-analytics"));

        mockMvc.perform(get(TEACHER_ANALYTICS_PATH)
                .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only approved teachers can use this teaching feature."));
    }

    @Test
    void quizAnalyticsShouldAggregateAcrossBatchesWithoutCountingDraftScores() throws Exception {
        String studentEmail = uniqueEmail("student-batched-analytics");
        String teacherEmail = uniqueEmail("teacher-batched-analytics");
        String studentToken = registerAndLoginStudent(studentEmail);
        String teacherToken = registerAndLoginTeacher(teacherEmail);
        Long studentId = studentProfileRepository.findByUserId(
                userRepository.findByEmailWithRoles(studentEmail).orElseThrow().getId()).orElseThrow().getId();
        TeacherProfile quizTeacher = teacherProfileRepository.findByUserId(
                userRepository.findByEmailWithRoles(teacherEmail).orElseThrow().getId()).orElseThrow();
        // V5: a quiz always belongs to a lesson (quizzes.lesson_id NOT NULL); a draft lesson is enough here.
        Lesson lesson = new Lesson();
        lesson.setTitle("Analytics batch lesson");
        lesson.setTeacher(quizTeacher);
        lesson.setLearningType(LearningType.RECORDED);
        lesson.setContentStatus(ContentStatus.DRAFT);
        lesson = lessonRepository.save(lesson);
        Quiz quiz = new Quiz();
        quiz.setTitle("Analytics batch boundary");
        quiz.setTeacher(quizTeacher);
        quiz.setLesson(lesson);
        Long quizId = quizRepository.save(quiz).getId();
        LocalDateTime completedBase = LocalDate.now().atTime(12, 0);
        LocalDateTime createdAt = LocalDateTime.now();

        jdbcTemplate.batchUpdate("""
                insert into quiz_attempts
                    (quiz_id, student_id, score, max_score, submitted, started_at,
                     completed_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement statement, int index) throws java.sql.SQLException {
                statement.setLong(1, quizId);
                statement.setLong(2, studentId);
                statement.setInt(3, index < 500 ? 50 : 100);
                statement.setInt(4, 100);
                statement.setBoolean(5, index < 505);
                statement.setTimestamp(6, Timestamp.valueOf(completedBase.plusSeconds(index)));
                // V1 chk_quiz_attempts_completion_state: only submitted attempts have a completion time.
                if (index < 505) {
                    statement.setTimestamp(7, Timestamp.valueOf(completedBase.plusSeconds(index)));
                } else {
                    statement.setNull(7, java.sql.Types.TIMESTAMP);
                }
                statement.setTimestamp(8, Timestamp.valueOf(createdAt));
                statement.setTimestamp(9, Timestamp.valueOf(createdAt));
            }

            @Override
            public int getBatchSize() {
                return 506;
            }
        });

        mockMvc.perform(get(STUDENT_ANALYTICS_PATH).header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview.averageQuizScore").value(50.50))
                .andExpect(jsonPath("$.data.quizScoreTrend.length()").value(6))
                .andExpect(jsonPath("$.data.quizScoreTrend[0].value").value(50.0))
                .andExpect(jsonPath("$.data.quizScoreTrend[5].value").value(100.0))
                .andExpect(jsonPath("$.data.activityTrend[0].value").value(506));
        mockMvc.perform(get(TEACHER_ANALYTICS_PATH).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview.averageQuizScore").value(50.50))
                .andExpect(jsonPath("$.data.quizScoreTrend.length()").value(1))
                .andExpect(jsonPath("$.data.quizScoreTrend[0].value").value(50.50));
    }

    @Test
    void adminAnalyticsShouldReturnPlatformSummary() throws Exception {
        String adminToken = loginAndExtractToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(get(ADMIN_ANALYTICS_PATH)
                .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.overview.totalStudents").isNumber())
                .andExpect(jsonPath("$.data.overview.totalTeachers").isNumber())
                .andExpect(jsonPath("$.data.overview.publishedContentItems").isNumber())
                .andExpect(jsonPath("$.data.overview.aiRequests").isNumber())
                .andExpect(jsonPath("$.data.overview.totalRevenue").doesNotExist())
                .andExpect(jsonPath("$.data.paymentStatusBreakdown").doesNotExist())
                .andExpect(jsonPath("$.data.userRegistrationTrend").isArray())
                .andExpect(jsonPath("$.data.teacherApprovalBreakdown").isArray())
                .andExpect(jsonPath("$.data.aiStatusBreakdown").isArray());
    }

    /**
     * Pins the aggregate-query path used by {@code AnalyticsService.getAdminAnalytics}.
     * Counts must react to new registrations, pending teacher approvals must
     * include self-registered teachers, and emitted breakdown buckets must carry
     * usable labels and positive values.
     */
    @Test
    void adminAnalyticsShouldReflectAggregateRegistrationsAndBreakdowns() throws Exception {
        String adminToken = loginAndExtractToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        JsonNode baseline = analyticsData(adminToken).path("overview");
        long baselineTeachers = baseline.path("totalTeachers").asLong();
        long baselineStudents = baseline.path("totalStudents").asLong();
        long baselinePendingTeachers = baseline.path("pendingTeacherApprovals").asLong();

        registerAndLoginPendingTeacher(uniqueEmail("teacher-admin-aggregate"));
        registerAndLoginStudent(uniqueEmail("student-admin-aggregate"));

        JsonNode afterData = analyticsData(adminToken);
        JsonNode after = afterData.path("overview");

        assertThat(after.path("totalTeachers").asLong())
                .as("Teacher count should increase by exactly one after a teacher self-registers.")
                .isEqualTo(baselineTeachers + 1L);
        assertThat(after.path("totalStudents").asLong())
                .as("Student count should increase by exactly one after a student self-registers.")
                .isEqualTo(baselineStudents + 1L);
        assertThat(after.path("pendingTeacherApprovals").asLong())
                .as("Pending teacher approvals must include the newly registered teacher.")
                .isGreaterThanOrEqualTo(baselinePendingTeachers + 1L);

        JsonNode teacherApproval = afterData.path("teacherApprovalBreakdown");
        assertThat(bucketValue(teacherApproval, "PENDING"))
                .as("Teacher approval breakdown must surface a PENDING bucket.")
                .isPositive();

        teacherApproval.forEach(item -> {
            assertThat(item.path("label").asText())
                    .as("Breakdown items must always have a non-blank label.")
                    .isNotBlank();
            assertThat(item.path("value").asLong())
                    .as("Zero-value breakdown items should be filtered out.")
                    .isPositive();
        });
    }

    @Test
    void analyticsEndpointsShouldRejectUnauthorizedRequests() throws Exception {
        assertUnauthorized(STUDENT_ANALYTICS_PATH);
        assertUnauthorized(TEACHER_ANALYTICS_PATH);
        assertUnauthorized(ADMIN_ANALYTICS_PATH);
    }

    @Test
    void analyticsEndpointsShouldRejectWrongRoles() throws Exception {
        String studentToken = registerAndLoginStudent(uniqueEmail("student-wrong-role-analytics"));
        String teacherToken = registerAndLoginTeacher(uniqueEmail("teacher-wrong-role-analytics"));

        mockMvc.perform(get(TEACHER_ANALYTICS_PATH)
                .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get(ADMIN_ANALYTICS_PATH)
                .header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    private JsonNode analyticsData(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(get(ADMIN_ANALYTICS_PATH)
                .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private void assertUnauthorized(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    private long bucketValue(JsonNode breakdownArray, String label) {
        if (!breakdownArray.isArray()) {
            return 0L;
        }
        for (JsonNode item : breakdownArray) {
            if (label.equals(item.path("label").asText())) {
                return item.path("value").asLong();
            }
        }
        return 0L;
    }

    private String registerAndLoginStudent(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/student")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Student Analytics Tester",
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "preferredLanguage", "en"))))
                .andExpect(status().isOk());

        return loginAndExtractToken(email, USER_PASSWORD);
    }

    private String registerAndLoginTeacher(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/teacher")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Teacher Analytics Tester",
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "bio", "Teacher analytics test user"))))
                .andExpect(status().isOk());

        // D13: teacher analytics requires an APPROVED teacher; approval itself is covered by
        // AdminTeacherManagementIntegrationTests, so the status is set directly here.
        TeacherProfile profile = teacherProfileRepository.findByUserId(
                userRepository.findByEmailWithRoles(email).orElseThrow().getId()).orElseThrow();
        profile.setTeacherStatus(TeacherStatus.APPROVED);
        teacherProfileRepository.save(profile);
        return loginAndExtractToken(email, USER_PASSWORD);
    }

    private String registerAndLoginPendingTeacher(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/teacher")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Pending Analytics Tester",
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "bio", "Pending teacher analytics test user"))))
                .andExpect(status().isOk());
        return loginAndExtractToken(email, USER_PASSWORD);
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", password))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("token")
                .asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueEmail(String prefix) {
        return prefix.replace("-", "") + UUID.randomUUID().toString().replace("-", "") + "@example.com";
    }
}
