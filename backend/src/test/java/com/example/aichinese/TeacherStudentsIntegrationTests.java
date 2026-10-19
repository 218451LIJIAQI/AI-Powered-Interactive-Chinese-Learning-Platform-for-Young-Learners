package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
 * End-to-end MockMvc coverage of the teacher "my students" surface.
 * It proves the roster is derived from engagement signals (here: a
 * quiz attempt on the teacher's lesson) rather than an
 * explicit relationship table, and that the PII boundary holds
 * (student email and other account details never leak through to the
 * teacher view).
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!",
        "app.email.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeacherStudentsIntegrationTests {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "SuperSecure123!";
    private static final String USER_PASSWORD = "Password123!";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final String EMAIL_DOMAIN = "@example.com";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String TEACHER_BIO = "Teacher bio";
    private static final String TEACHERS_STUDENTS_ENDPOINT = "/api/v1/teachers/students";
    private static final String TEACHERS_FEEDBACK_ENDPOINT = "/api/v1/teachers/feedback";
    private static final String TEACHERS_LESSONS_ENDPOINT = "/api/v1/teachers/lessons";
    private static final String TEACHERS_QUIZZES_ENDPOINT = "/api/v1/teachers/quizzes";
    private static final String STUDENTS_QUIZZES_ENDPOINT = "/api/v1/students/quizzes";
    private static final String TEACHER_DOCUMENTS_ENDPOINT = "/api/v1/teachers/verification-documents";
    private static final String ADMIN_TEACHERS_ENDPOINT = "/api/v1/admin/teachers";
    private static final String TEACHER_REGISTER_ENDPOINT = "/api/v1/auth/register/teacher";
    private static final String STUDENT_REGISTER_ENDPOINT = "/api/v1/auth/register/student";
    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";
    private static final String FEEDBACK_TYPE_PROGRESS = "PROGRESS";
    private static final String LEARNING_TYPE_RECORDED = "RECORDED";
    private static final String CHOOSE_TONE = "CHOOSE_TONE";
    private static final String TRUE_FALSE = "TRUE_FALSE";
    private static final String DOCUMENT_TYPE_QUALIFICATION = "QUALIFICATION";
    private static final String QUALIFICATION_FILE_NAME = "qualification.pdf";
    private static final String APPLICATION_PDF = "application/pdf";
    private static final int PAGE_SIZE_REQUEST = 100_000;
    private static final int CAPPED_PAGE_SIZE = 100;
    private static final int DEFAULT_LEVEL_ID = 1;
    private static final int DEFAULT_CATEGORY_ID = 3;
    private static final int DEFAULT_DURATION_MINUTES = 15;
    private static final int QUESTION_POINTS = 5;

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
    void teacherStudentsShouldReturnLinkedStudentSummaries() throws Exception {
        String teacherEmail = uniqueEmail("teacher-students");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, USER_PASSWORD);

        Long lessonId = createLesson(teacherToken, "Teacher Students Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);

        String studentEmail = uniqueEmail("teacher-students-student");
        String studentToken = registerStudentAndLogin(studentEmail);
        StudentProfile studentProfile = findStudentProfile(studentEmail);
        User studentUser = userRepository.findByEmailWithRoles(studentEmail).orElseThrow();

        submitQuizAttempt(studentToken, quizId);

        mockMvc.perform(authenticatedJsonPost(TEACHERS_FEEDBACK_ENDPOINT, teacherToken, Map.of(
                        "studentId", studentProfile.getId(),
                        "lessonId", lessonId,
                        "feedbackType", FEEDBACK_TYPE_PROGRESS,
                        "message", "Great effort. Keep reviewing the greeting pattern.")))
                .andExpect(status().isOk());

        mockMvc.perform(authenticatedGet(TEACHERS_STUDENTS_ENDPOINT, teacherToken)
                .param("size", String.valueOf(PAGE_SIZE_REQUEST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(CAPPED_PAGE_SIZE))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].studentId").value(studentProfile.getId()))
                .andExpect(jsonPath("$.data.items[0].userId").value(studentUser.getId()))
                .andExpect(jsonPath("$.data.items[0].fullName").value(studentUser.getDisplayName()))
                .andExpect(jsonPath("$.data.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].progressPercent").value(100))
                .andExpect(jsonPath("$.data.items[0].quizAveragePercent").value(50.0))
                .andExpect(jsonPath("$.data.items[0].atRisk").value(true))
                // W4: flagged for the low quiz average (50 %), weak areas are lesson titles of wrong answers.
                .andExpect(jsonPath("$.data.items[0].needsSupport").value(true))
                .andExpect(jsonPath("$.data.items[0].supportReasons[0]").value("LOW_QUIZ_SCORE"))
                .andExpect(jsonPath("$.data.items[0].weakAreas[0]").value("Teacher Students Lesson"))
                .andExpect(jsonPath("$.data.items[0].lessonMastery[0].mastery").value("COMPLETED"))
                .andExpect(jsonPath("$.data.items[0].latestFeedbackType").value(FEEDBACK_TYPE_PROGRESS))
                .andExpect(jsonPath("$.data.items[0].latestFeedbackAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].latestActivityAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].joinedAt").isNotEmpty());
    }

    @Test
    void teacherStudentsShouldRejectWrongRole() throws Exception {
        String studentToken = registerStudentAndLogin(uniqueEmail("teacher-students-wrong-role"));

        mockMvc.perform(authenticatedGet(TEACHERS_STUDENTS_ENDPOINT, studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    private Long createLesson(String teacherToken, String title) throws Exception {
        MvcResult lessonResult = mockMvc.perform(authenticatedJsonPost(TEACHERS_LESSONS_ENDPOINT, teacherToken, Map.of(
                        "title", title,
                        "description", "Teacher students integration lesson",
                        "levelId", DEFAULT_LEVEL_ID,
                        "categoryId", DEFAULT_CATEGORY_ID,
                        "learningType", LEARNING_TYPE_RECORDED,
                        "estimatedDurationMinutes", DEFAULT_DURATION_MINUTES,
                        "basicContent", "Teacher students integration content")))
                .andExpect(status().isOk())
                .andReturn();

        Long lessonId = extractDataId(lessonResult);
        // D4: lessons reach students only after admin approval.
        ContentReviewTestSupport.publish(mockMvc, teacherToken, adminToken(), "LESSON", lessonId);
        return lessonId;
    }

    private Long createQuizWithQuestions(String teacherToken, Long lessonId) throws Exception {
        MvcResult quizResult = mockMvc.perform(authenticatedJsonPost(
                TEACHERS_LESSONS_ENDPOINT + "/" + lessonId + "/quizzes",
                teacherToken,
                Map.of(
                        "title", "Teacher students quiz",
                        "description", "Integration quiz")))
                .andExpect(status().isOk())
                .andReturn();

        Long quizId = extractDataId(quizResult);

        mockMvc.perform(authenticatedJsonPost(
                teacherQuizQuestionsPath(quizId),
                teacherToken,
                quizQuestion(
                        CHOOSE_TONE,
                        "Which tone matches ma?",
                        "ma1 is first tone.",
                        "ma1",
                        "ma3",
                        1)))
                .andExpect(status().isOk());

        mockMvc.perform(authenticatedJsonPost(
                teacherQuizQuestionsPath(quizId),
                teacherToken,
                quizQuestion(
                        TRUE_FALSE,
                        "ni hao means hello.",
                        "ni hao means hello.",
                        "TRUE",
                        "FALSE",
                        2)))
                .andExpect(status().isOk());

        ContentReviewTestSupport.publish(mockMvc, teacherToken, adminToken(), "QUIZ", quizId);
        return quizId;
    }

    private void submitQuizAttempt(String studentToken, Long quizId) throws Exception {
        MvcResult loadQuizResult = mockMvc.perform(authenticatedGet(studentQuizPath(quizId), studentToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode quizData = objectMapper.readTree(loadQuizResult.getResponse().getContentAsString()).path("data");
        Long firstQuestionId = quizData.path("questions").get(0).path("id").asLong();
        Long secondQuestionId = quizData.path("questions").get(1).path("id").asLong();

        mockMvc.perform(authenticatedJsonPost(studentQuizPath(quizId) + "/attempts", studentToken, Map.of(
                        "answers", List.of(
                                QuizAnswerTestSupport.optionAnswer(quizOptionRepository, firstQuestionId, "ma1"),
                                QuizAnswerTestSupport.optionAnswer(quizOptionRepository, secondQuestionId, "FALSE")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(QUESTION_POINTS))
                .andExpect(jsonPath("$.data.maxScore").value(QUESTION_POINTS * 2));
    }

    private void approveTeacherProfile(String teacherEmail) throws Exception {
        TeacherProfile profile = registerTeacherWithVerificationDocument(teacherEmail);
        String adminToken = adminToken();

        mockMvc.perform(authenticatedJsonPost(
                adminTeacherApprovePath(profile.getId()),
                adminToken,
                Map.of("note", "Approved for teacher students integration test")))
                .andExpect(status().isOk());

        TeacherProfile refreshed = teacherProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(refreshed.getTeacherStatus()).isEqualTo(TeacherStatus.APPROVED);
    }

    private TeacherProfile registerTeacherWithVerificationDocument(String email) throws Exception {
        registerTeacher(email);
        String teacherToken = loginAndExtractToken(email, USER_PASSWORD);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                QUALIFICATION_FILE_NAME,
                APPLICATION_PDF,
                TestUploadFixtures.minimalPdf());

        mockMvc.perform(multipart(TEACHER_DOCUMENTS_ENDPOINT)
                .file(file)
                .param("documentType", DOCUMENT_TYPE_QUALIFICATION)
                .param("description", "Qualification proof")
                .with(csrf())
                .header(AUTHORIZATION, BEARER + teacherToken))
                .andExpect(status().isOk());

        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return teacherProfileRepository.findDetailedByUserId(user.getId()).orElseThrow();
    }

    private void registerTeacher(String email) throws Exception {
        mockMvc.perform(jsonPost(TEACHER_REGISTER_ENDPOINT, Map.of(
                        "fullName", "Teacher " + email,
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "bio", TEACHER_BIO)))
                .andExpect(status().isOk());
    }

    private String registerStudentAndLogin(String email) throws Exception {
        mockMvc.perform(jsonPost(STUDENT_REGISTER_ENDPOINT, Map.of(
                        "fullName", "Student " + email,
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "preferredLanguage", DEFAULT_LANGUAGE)))
                .andExpect(status().isOk());

        return loginAndExtractToken(email, USER_PASSWORD);
    }

    private StudentProfile findStudentProfile(String email) {
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return studentProfileRepository.findByUserId(user.getId()).orElseThrow();
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(jsonPost(LOGIN_ENDPOINT, Map.of(
                        "email", email,
                        "password", password)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("token")
                .asText();
    }

    private Long extractDataId(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("id").asLong();
    }

    private String adminToken() throws Exception {
        return loginAndExtractToken(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    private MockHttpServletRequestBuilder authenticatedGet(String path, String token) {
        return get(path).header(AUTHORIZATION, BEARER + token);
    }

    private MockHttpServletRequestBuilder authenticatedJsonPost(String path, String token, Object body)
            throws Exception {
        return post(path)
                .with(csrf())
                .header(AUTHORIZATION, BEARER + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
    }

    private MockHttpServletRequestBuilder jsonPost(String path, Object body) throws Exception {
        return post(path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static String teacherQuizQuestionsPath(Long quizId) {
        return TEACHERS_QUIZZES_ENDPOINT + "/" + quizId + "/questions";
    }

    private static String studentQuizPath(Long quizId) {
        return STUDENTS_QUIZZES_ENDPOINT + "/" + quizId;
    }

    private static String adminTeacherApprovePath(Long teacherId) {
        return ADMIN_TEACHERS_ENDPOINT + "/" + teacherId + "/approve";
    }

    private static Map<String, Object> quizQuestion(
            String questionType,
            String questionText,
            String explanation,
            String correctOption,
            String incorrectOption,
            int displayOrder) {
        return Map.of(
                "questionType", questionType,
                "questionText", questionText,
                "points", QUESTION_POINTS,
                "displayOrder", displayOrder,
                "explanation", explanation,
                "options", List.of(
                        Map.of("optionText", correctOption, "correctOption", true, "displayOrder", 1),
                        Map.of("optionText", incorrectOption, "correctOption", false, "displayOrder", 2)));
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + EMAIL_DOMAIN;
    }
}
