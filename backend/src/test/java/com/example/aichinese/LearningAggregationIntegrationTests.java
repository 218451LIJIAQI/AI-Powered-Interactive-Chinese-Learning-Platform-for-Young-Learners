package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.aichinese.support.QuizAnswerTestSupport;
import com.example.aichinese.support.ContentReviewTestSupport;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.support.TestUploadFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end coverage of how one student's learning activity on free,
 * published content flows into every aggregate view: student progress,
 * analytics and dashboard; the owning teacher's roster, analytics,
 * dashboard, quiz attempts, video progress and live-class enrolments.
 *
 * <p>All published content is free (no purchase step). The tests also pin
 * isolation: another approved teacher and another student never see the
 * first student's activity, and a quiz attempt stops being readable once
 * its lesson is archived.</p>
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LearningAggregationIntegrationTests {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "SuperSecure123!";
    private static final String USER_PASSWORD = "Password123!";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.example.aichinese.repository.QuizOptionRepository quizOptionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Test
    void freeLessonActivityFlowsIntoEveryAggregateViewAndStaysIsolatedPerTeacherAndStudent() throws Exception {
        String teacherEmail = uniqueEmail("aggregate-t");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, USER_PASSWORD);
        Long lessonId = createLesson(teacherToken, "Aggregate Roster Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);
        Long videoId = createUploadedRecordedVideo(teacherToken, lessonId, "Aggregate Roster Video");
        Long liveClassId = createLiveClass(teacherToken, lessonId, "Aggregate Roster Live Class");

        String otherTeacherEmail = uniqueEmail("aggregate-other-t");
        approveTeacherProfile(otherTeacherEmail);
        String otherTeacherToken = loginAndExtractToken(otherTeacherEmail, USER_PASSWORD);

        String studentEmail = uniqueEmail("aggregate-s");
        String studentToken = registerStudentAndLogin(studentEmail);
        Long studentId = findStudentProfile(studentEmail).getId();
        String otherStudentToken = registerStudentAndLogin(uniqueEmail("aggregate-other-s"));

        // Published content is open to any signed-in student: no purchase, no lock.
        mockMvc.perform(get("/api/v1/students/lessons/" + lessonId)
                .header(AUTHORIZATION_HEADER, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessonId").value(lessonId))
                .andExpect(jsonPath("$.data", not(hasKey("locked"))));
        submitWrongAnswer(studentToken, quizId);
        mockMvc.perform(post("/api/v1/students/video-progress/" + videoId)
                .with(csrf())
                .header(AUTHORIZATION_HEADER, bearer(studentToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "watchedSeconds", 120,
                        "totalSeconds", 120,
                        "progressPercentage", 100,
                        "completed", true))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/students/live-classes/" + liveClassId + "/enrol")
                .with(csrf())
                .header(AUTHORIZATION_HEADER, bearer(studentToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/teachers/students")
                .param("size", "100000")
                .header(AUTHORIZATION_HEADER, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].studentId").value(studentId))
                .andExpect(jsonPath("$.data.totalItems").value(1));
        mockMvc.perform(get("/api/v1/teachers/analytics")
                .header(AUTHORIZATION_HEADER, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview.totalActiveStudents").value(1))
                .andExpect(jsonPath("$.data.overview.totalLiveEnrolments").value(1))
                .andExpect(jsonPath("$.data.quizScoreTrend.length()").value(1))
                .andExpect(jsonPath("$.data", not(hasKey("earningsTrend"))))
                .andExpect(jsonPath("$.data.overview", not(hasKey("netEarnings"))));
        mockMvc.perform(get("/api/v1/students/analytics")
                .header(AUTHORIZATION_HEADER, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview.totalTrackedLessons").value(1))
                .andExpect(jsonPath("$.data.quizScoreTrend.length()").value(1))
                .andExpect(jsonPath("$.data.weakAreaBreakdown.length()").value(1))
                .andExpect(jsonPath("$.data.weakAreaBreakdown[0].label").value("CHOOSE_TONE"));
        mockMvc.perform(get("/api/v1/students/dashboard")
                .header(AUTHORIZATION_HEADER, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentQuizResults[0].quizId").value(quizId))
                .andExpect(jsonPath("$.data.weakAreas.length()").value(1))
                // Analytics keeps the wrong-answer breakdown by question type.
                .andExpect(jsonPath("$.data.weakAreas[0]").value("CHOOSE_TONE"));
        mockMvc.perform(get("/api/v1/students/progress")
                .header(AUTHORIZATION_HEADER, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalLessonsStarted").value(1))
                .andExpect(jsonPath("$.data.completedLessons").value(1))
                .andExpect(jsonPath("$.data.weakAreas.length()").value(1))
                // W4: weak areas are the lesson titles (topics) of wrong answers, not question types.
                .andExpect(jsonPath("$.data.weakAreas[0]").value("Aggregate Roster Lesson"))
                .andExpect(jsonPath("$.data.recentLessons[0].lessonId").value(lessonId));
        mockMvc.perform(get("/api/v1/teachers/dashboard")
                .header(AUTHORIZATION_HEADER, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStudents").value(1))
                .andExpect(jsonPath("$.data.recentQuizResults.length()").value(1))
                .andExpect(jsonPath("$.data", not(hasKey("earningsSummary"))));
        mockMvc.perform(get("/api/v1/teachers/quizzes/" + quizId + "/attempts")
                .param("size", "100000")
                .header(AUTHORIZATION_HEADER, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].studentId").value(studentId));
        mockMvc.perform(get("/api/v1/teachers/lessons/" + lessonId + "/video-progress")
                .param("size", "100000")
                .header(AUTHORIZATION_HEADER, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].studentId").value(studentId));
        mockMvc.perform(get("/api/v1/teachers/live-classes/" + liveClassId + "/enrolments")
                .param("size", "100000")
                .header(AUTHORIZATION_HEADER, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].studentId").value(studentId));

        // Another approved teacher sees none of this student's activity and cannot reach the content.
        mockMvc.perform(get("/api/v1/teachers/students")
                .param("size", "100000")
                .header(AUTHORIZATION_HEADER, bearer(otherTeacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.totalItems").value(0));
        mockMvc.perform(get("/api/v1/teachers/analytics")
                .header(AUTHORIZATION_HEADER, bearer(otherTeacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview.totalActiveStudents").value(0))
                .andExpect(jsonPath("$.data.overview.totalLiveEnrolments").value(0))
                .andExpect(jsonPath("$.data.quizScoreTrend.length()").value(0));
        mockMvc.perform(get("/api/v1/teachers/dashboard")
                .header(AUTHORIZATION_HEADER, bearer(otherTeacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStudents").value(0))
                .andExpect(jsonPath("$.data.averageQuizScore").value(0.0))
                .andExpect(jsonPath("$.data.recentQuizResults.length()").value(0));
        mockMvc.perform(get("/api/v1/teachers/quizzes/" + quizId + "/attempts")
                .header(AUTHORIZATION_HEADER, bearer(otherTeacherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/teachers/lessons/" + lessonId + "/video-progress")
                .header(AUTHORIZATION_HEADER, bearer(otherTeacherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/teachers/live-classes/" + liveClassId + "/enrolments")
                .header(AUTHORIZATION_HEADER, bearer(otherTeacherToken)))
                .andExpect(status().isNotFound());

        // Another student's own aggregates stay empty.
        mockMvc.perform(get("/api/v1/students/analytics")
                .header(AUTHORIZATION_HEADER, bearer(otherStudentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview.totalTrackedLessons").value(0))
                .andExpect(jsonPath("$.data.overview.completedLessons").value(0))
                .andExpect(jsonPath("$.data.overview.averageQuizScore").value(0.0))
                .andExpect(jsonPath("$.data.quizScoreTrend.length()").value(0))
                .andExpect(jsonPath("$.data.weakAreaBreakdown.length()").value(0));
        mockMvc.perform(get("/api/v1/students/dashboard")
                .header(AUTHORIZATION_HEADER, bearer(otherStudentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentQuizResults.length()").value(0))
                .andExpect(jsonPath("$.data.weakAreas.length()").value(0))
                .andExpect(jsonPath("$.data.unfinishedLessons.length()").value(0));
        mockMvc.perform(get("/api/v1/students/progress")
                .header(AUTHORIZATION_HEADER, bearer(otherStudentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalLessonsStarted").value(0))
                .andExpect(jsonPath("$.data.completedLessons").value(0))
                .andExpect(jsonPath("$.data.quizAverage").value(0))
                .andExpect(jsonPath("$.data.weakAreas.length()").value(0))
                .andExpect(jsonPath("$.data.recentLessons.length()").value(0));
    }

    @Test
    void quizAttemptDetailExposesCorrectAnswersUntilTheLessonIsArchived() throws Exception {
        String teacherEmail = uniqueEmail("attempt-detail-t");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, USER_PASSWORD);
        Long lessonId = createLesson(teacherToken, "Attempt Detail Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);
        String studentToken = registerStudentAndLogin(uniqueEmail("attempt-detail-s"));
        String otherStudentToken = registerStudentAndLogin(uniqueEmail("attempt-detail-other-s"));

        Long attemptId = submitWrongAnswer(studentToken, quizId);

        mockMvc.perform(get("/api/v1/students/quiz-attempts/" + attemptId)
                .header(AUTHORIZATION_HEADER, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answers[0].correctAnswer").value("ma1"));
        mockMvc.perform(get("/api/v1/students/wrong-answers")
                .param("size", "100000")
                .header(AUTHORIZATION_HEADER, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items[0].correctAnswer").value("ma1"));
        mockMvc.perform(get("/api/v1/students/quiz-attempts/" + attemptId)
                .header(AUTHORIZATION_HEADER, bearer(otherStudentToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/teachers/lessons/" + lessonId + "/archive")
                .with(csrf())
                .header(AUTHORIZATION_HEADER, bearer(teacherToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/students/quizzes/" + quizId)
                .header(AUTHORIZATION_HEADER, bearer(studentToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/students/quiz-attempts/" + attemptId)
                .header(AUTHORIZATION_HEADER, bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/students/quiz-attempts/" + attemptId + "/wrong-answers")
                .header(AUTHORIZATION_HEADER, bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/students/wrong-answers")
                .header(AUTHORIZATION_HEADER, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    private Long submitWrongAnswer(String studentToken, Long quizId) throws Exception {
        MvcResult loadQuizResult = mockMvc.perform(get("/api/v1/students/quizzes/" + quizId)
                .header(AUTHORIZATION_HEADER, bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        Long questionId = objectMapper.readTree(loadQuizResult.getResponse().getContentAsString())
                .path("data").path("questions").get(0).path("id").asLong();
        MvcResult submitResult = mockMvc.perform(post("/api/v1/students/quizzes/" + quizId + "/attempts")
                .with(csrf())
                .header(AUTHORIZATION_HEADER, bearer(studentToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "answers", List.of(QuizAnswerTestSupport.optionAnswer(quizOptionRepository, questionId, "ma3"))))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(submitResult.getResponse().getContentAsString())
                .path("data").path("attemptId").asLong();
    }

    private void approveTeacherProfile(String teacherEmail) throws Exception {
        TeacherProfile profile = registerTeacherWithVerificationDocument(teacherEmail);
        String adminToken = loginAndExtractToken(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(post("/api/v1/admin/teachers/" + profile.getId() + "/approve")
                .with(csrf())
                .header(AUTHORIZATION_HEADER, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("note", "Approved for learning aggregation coverage"))))
                .andExpect(status().isOk());

        TeacherProfile refreshed = teacherProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(refreshed.getTeacherStatus()).isEqualTo(TeacherStatus.APPROVED);
    }

    private TeacherProfile registerTeacherWithVerificationDocument(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/teacher")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "fullName", "Teacher " + email,
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "bio", "Teacher bio"))))
                .andExpect(status().isOk());
        String teacherToken = loginAndExtractToken(email, USER_PASSWORD);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "qualification.pdf",
                "application/pdf",
                TestUploadFixtures.minimalPdf());

        mockMvc.perform(multipart("/api/v1/teachers/verification-documents")
                .file(file)
                .param("documentType", "QUALIFICATION")
                .param("description", "Qualification proof")
                .with(csrf())
                .header(AUTHORIZATION_HEADER, bearer(teacherToken)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return teacherProfileRepository.findDetailedByUserId(user.getId()).orElseThrow();
    }

    private StudentProfile findStudentProfile(String email) {
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return studentProfileRepository.findByUserId(user.getId()).orElseThrow();
    }

    private String registerStudentAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/student")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "fullName", "Student " + email,
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "preferredLanguage", "en"))))
                .andExpect(status().isOk());

        return loginAndExtractToken(email, USER_PASSWORD);
    }

    private Long createLesson(String teacherToken, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teachers/lessons")
                .with(csrf())
                .header(AUTHORIZATION_HEADER, bearer(teacherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "title", title,
                        "description", "Free beginner lesson",
                        "levelId", 1,
                        "categoryId", 3,
                        "learningType", "RECORDED",
                        "estimatedDurationMinutes", 20,
                        "basicContent", "Tone practice content"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", not(hasKey("price"))))
                .andExpect(jsonPath("$.data", not(hasKey("accessType"))))
                .andReturn();
        Long lessonId = extractId(result);
        // D4: teacher lessons are drafts until an admin approves them.
        ContentReviewTestSupport.publish(
                mockMvc, teacherToken, loginAndExtractToken(ADMIN_EMAIL, ADMIN_PASSWORD), "LESSON", lessonId);
        return lessonId;
    }

    private Long createQuizWithQuestions(String teacherToken, Long lessonId) throws Exception {
        MvcResult quizResult = mockMvc.perform(post("/api/v1/teachers/lessons/" + lessonId + "/quizzes")
                .with(csrf())
                .header(AUTHORIZATION_HEADER, bearer(teacherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "title", "Tone quiz",
                        "description", "Checks first-tone recognition"))))
                .andExpect(status().isOk())
                .andReturn();

        Long quizId = extractId(quizResult);
        mockMvc.perform(post("/api/v1/teachers/quizzes/" + quizId + "/questions")
                .with(csrf())
                .header(AUTHORIZATION_HEADER, bearer(teacherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "questionType", "CHOOSE_TONE",
                        "questionText", "Which tone matches ma?",
                        "points", 5,
                        "displayOrder", 1,
                        "explanation", "ma1 is first tone.",
                        "options", List.of(
                                Map.of("optionText", "ma1", "correctOption", true, "displayOrder", 1),
                                Map.of("optionText", "ma3", "correctOption", false, "displayOrder", 2))))))
                .andExpect(status().isOk());

        ContentReviewTestSupport.publish(
                mockMvc, teacherToken, loginAndExtractToken(ADMIN_EMAIL, ADMIN_PASSWORD), "QUIZ", quizId);
        return quizId;
    }

    private Long createUploadedRecordedVideo(String teacherToken, Long lessonId, String title) throws Exception {
        MockMultipartFile videoFile = new MockMultipartFile(
                "file",
                "lesson.mp4",
                "video/mp4",
                TestUploadFixtures.minimalMp4());
        MvcResult result = mockMvc.perform(multipart("/api/v1/teachers/lessons/" + lessonId + "/recorded-videos")
                .file(videoFile)
                .param("title", title)
                .param("description", "Recorded lesson coverage")
                .param("sourceType", "UPLOADED_FILE")
                .param("durationSeconds", "120")
                .param("sortOrder", "1")
                .with(csrf())
                .header(AUTHORIZATION_HEADER, bearer(teacherToken)))
                .andExpect(status().isOk())
                .andReturn();
        return extractId(result);
    }

    private Long createLiveClass(String teacherToken, Long lessonId, String title) throws Exception {
        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        MvcResult result = mockMvc.perform(post("/api/v1/teachers/live-classes")
                .with(csrf())
                .header(AUTHORIZATION_HEADER, bearer(teacherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "title", title,
                        "description", "Live class coverage",
                        "lessonId", lessonId,
                        "startTime", start.toString(),
                        "endTime", start.plusMinutes(45).toString(),
                        "meetingLink", "https://meet.google.com/" + UUID.randomUUID(),
                        "meetingPlatform", "GOOGLE_MEET",
                        "status", "SCHEDULED"))))
                .andExpect(status().isOk())
                .andReturn();
        return extractId(result);
    }

    private Long extractId(MvcResult result) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("id").asLong();
    }

    private String json(Map<String, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", email,
                        "password", password))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
