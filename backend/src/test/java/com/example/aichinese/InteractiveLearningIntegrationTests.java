package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.support.TestUploadFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end MockMvc coverage of the lesson + quiz player flow:
 * lesson read, quiz start / submit / score, wrong-answer surface,
 * progress + XP + streak side-effects, and badge awards.
 *
 * <p>Pins the locked-content gate (paid lessons hidden from students
 * without entitlement), the quiz answer-key boundary (student
 * responses never include the correct answer flag), and the streak
 * deduplication so a single completion can't double-count.</p>
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InteractiveLearningIntegrationTests {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "SuperSecure123!";
    private static final String USER_PASSWORD = "Password123!";
    private static final String ACCESS_FREE = "FREE";
    private static final String LEARNING_RECORDED = "RECORDED";
    private static final String QUIZ_TITLE = "Tone quiz";
    private static final String QUALIFICATION_DOCUMENT_TYPE = "QUALIFICATION";
    private static final String TRUE = "TRUE";
    private static final String FALSE = "FALSE";
    private static final String APPROVED_TEACHERS_ONLY_MESSAGE =
            "Only approved teachers can create or submit learning content.";
    private static final String DUPLICATE_ANSWER_MESSAGE =
            "Duplicate answers for the same quiz question are not allowed.";
    private static final String FOREIGN_ANSWER_MESSAGE =
            "Submitted answer does not belong to this quiz.";
    private static final String QUESTION_LOCKED_MESSAGE =
            "Quiz questions cannot be changed after students have submitted attempts.";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int OVERSIZED_PAGE_SIZE = 100_000;
    private static final long BEGINNER_LEVEL_ID = 1L;
    private static final long VOCABULARY_CATEGORY_ID = 3L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private QuizAttemptAnswerRepository quizAttemptAnswerRepository;

    @Autowired
    private QuizOptionRepository quizOptionRepository;

    @Test
    void teacherCreatesQuizStudentLoadsWithoutAnswersAndSubmissionStoresWrongAnswersAndRewards() throws Exception {
        String teacherEmail = uniqueEmail("quiz-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);

        Long lessonId = createLesson(teacherToken, "Quiz Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);

        mockMvc.perform(oversizedPageGet("/api/v1/teachers/lessons/" + lessonId + "/quizzes", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(MAX_PAGE_SIZE))
                .andExpect(jsonPath("$.data.items[0].title").value(QUIZ_TITLE));

        String studentEmail = uniqueEmail("quiz-student");
        String studentToken = registerStudentAndLogin(studentEmail);

        mockMvc.perform(oversizedPageGet("/api/v1/students/quizzes", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(MAX_PAGE_SIZE))
                .andExpect(jsonPath("$.data.items[0].id").value(quizId))
                .andExpect(jsonPath("$.data.items[0].questionCount").value(2));

        mockMvc.perform(oversizedPageGet("/api/v1/students/quizzes", studentToken)
                .param("lessonId", String.valueOf(lessonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(quizId));

        MvcResult loadQuizResult = mockMvc.perform(
                authenticatedGet("/api/v1/students/quizzes/" + quizId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].correctAnswerText").doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].options[0].correctOption").doesNotExist())
                .andReturn();

        JsonNode quizData = responseData(loadQuizResult);
        Long firstQuestionId = quizData.path("questions").get(0).path("id").asLong();
        Long secondQuestionId = quizData.path("questions").get(1).path("id").asLong();

        MvcResult submitResult = mockMvc.perform(submitQuizAttempt(
                quizId,
                studentToken,
                answer(firstQuestionId, "ma1"),
                answer(secondQuestionId, TRUE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(5))
                .andExpect(jsonPath("$.data.maxScore").value(10))
                .andExpect(jsonPath("$.data.correctCount").value(1))
                .andExpect(jsonPath("$.data.wrongCount").value(1))
                .andExpect(jsonPath("$.data.earnedPoints").value(20))
                .andReturn();

        Long attemptId = extractAttemptId(submitResult);

        mockMvc.perform(authenticatedGet("/api/v1/students/quiz-attempts/" + attemptId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answers[0].correctAnswer").isString());

        mockMvc.perform(oversizedPageGet("/api/v1/teachers/quizzes/" + quizId + "/attempts", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(MAX_PAGE_SIZE))
                .andExpect(jsonPath("$.data.items[0].attemptId").value(attemptId));

        mockMvc.perform(oversizedPageGet("/api/v1/students/wrong-answers", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(MAX_PAGE_SIZE))
                .andExpect(jsonPath("$.data.items[0].questionText").exists());

        mockMvc.perform(oversizedPageGet("/api/v1/students/rewards/points-history", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(MAX_PAGE_SIZE))
                .andExpect(jsonPath("$.data.items[0].pointsDelta").exists());

        mockMvc.perform(oversizedPageGet("/api/v1/students/rewards/badges", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(MAX_PAGE_SIZE));

        StudentProfile studentProfile = findStudentProfile(studentEmail);
        QuizAttempt attempt = quizAttemptRepository
                .findByIdAndStudentUserId(attemptId, studentProfile.getUser().getId())
                .orElseThrow();
        assertThat(attempt.getScore()).isEqualTo(5);
        assertThat(quizAttemptAnswerRepository.findByQuizAttemptIdOrderByCreatedAtAsc(attemptId)).hasSize(2);
        assertThat(studentProfile.getTotalPoints()).isEqualTo(45);
    }

    @Test
    void anotherStudentCannotAccessQuizAttemptAndUnauthorizedAndWrongRoleRequestsAreRejected() throws Exception {
        String teacherEmail = uniqueEmail("attempt-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);

        Long lessonId = createLesson(teacherToken, "Attempt Protection Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);

        String firstStudentToken = registerStudentAndLogin(uniqueEmail("attempt-student-one"));
        String secondStudentToken = registerStudentAndLogin(uniqueEmail("attempt-student-two"));

        MvcResult loadQuizResult = mockMvc.perform(
                authenticatedGet("/api/v1/students/quizzes/" + quizId, firstStudentToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode quizData = responseData(loadQuizResult);
        Long firstQuestionId = quizData.path("questions").get(0).path("id").asLong();
        Long secondQuestionId = quizData.path("questions").get(1).path("id").asLong();

        MvcResult submitResult = mockMvc.perform(submitQuizAttempt(
                quizId,
                firstStudentToken,
                answer(firstQuestionId, "ma3"),
                answer(secondQuestionId, FALSE)))
                .andExpect(status().isOk())
                .andReturn();

        Long attemptId = extractAttemptId(submitResult);

        mockMvc.perform(authenticatedGet("/api/v1/students/quiz-attempts/" + attemptId, secondStudentToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/students/quizzes/" + quizId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(authenticatedGet("/api/v1/students/quizzes/" + quizId, teacherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void teacherCannotChangeQuizQuestionsAfterStudentsSubmitAttempts() throws Exception {
        String teacherEmail = uniqueEmail("quiz-freeze-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        Long lessonId = createLesson(teacherToken, "Question Freeze Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);

        String studentToken = registerStudentAndLogin(uniqueEmail("quiz-freeze-student"));
        MvcResult loadQuizResult = mockMvc.perform(
                authenticatedGet("/api/v1/students/quizzes/" + quizId, studentToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode quizData = responseData(loadQuizResult);
        Long firstQuestionId = quizData.path("questions").get(0).path("id").asLong();
        Long secondQuestionId = quizData.path("questions").get(1).path("id").asLong();

        mockMvc.perform(submitQuizAttempt(
                quizId,
                studentToken,
                answer(firstQuestionId, "ma1"),
                answer(secondQuestionId, FALSE)))
                .andExpect(status().isOk());

        Map<String, Object> questionPayload = Map.of(
                "questionType", "MULTIPLE_CHOICE",
                "questionText", "Which one is hello?",
                "points", 5,
                "displayOrder", 3,
                "options", List.of(
                        Map.of("optionText", "ni hao", "correctOption", true, "displayOrder", 1),
                        Map.of("optionText", "zai jian", "correctOption", false, "displayOrder", 2)));

        mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/quizzes/" + quizId + "/questions",
                teacherToken,
                questionPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(QUESTION_LOCKED_MESSAGE));

        mockMvc.perform(authenticatedJsonPut(
                "/api/v1/teachers/quiz-questions/" + firstQuestionId,
                teacherToken,
                questionPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(QUESTION_LOCKED_MESSAGE));

        mockMvc.perform(authenticatedDelete("/api/v1/teachers/quiz-questions/" + firstQuestionId, teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(QUESTION_LOCKED_MESSAGE));
    }

    @Test
    void unapprovedTeachersCannotCreateQuizzes() throws Exception {
        String ownerEmail = uniqueEmail("quiz-owner");
        approveTeacherProfile(ownerEmail);
        String ownerToken = userToken(ownerEmail);
        Long lessonId = createLesson(ownerToken, "Owner Quiz Lesson");

        String pendingTeacherEmail = uniqueEmail("quiz-pending");
        registerTeacher(pendingTeacherEmail);
        String pendingTeacherToken = userToken(pendingTeacherEmail);

        mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/lessons/" + lessonId + "/quizzes",
                pendingTeacherToken,
                Map.of(
                        "title", "Blocked pending quiz",
                        "description", "Should not work")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(APPROVED_TEACHERS_ONLY_MESSAGE));

        String rejectedTeacherEmail = uniqueEmail("quiz-rejected");
        TeacherProfile rejectedProfile = registerTeacherWithVerificationDocument(rejectedTeacherEmail);
        String adminToken = adminToken();
        mockMvc.perform(authenticatedJsonPost(
                "/api/v1/admin/teachers/" + rejectedProfile.getId() + "/reject",
                adminToken,
                Map.of("reason", "Verification incomplete")))
                .andExpect(status().isOk());

        String rejectedTeacherToken = userToken(rejectedTeacherEmail);
        mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/lessons/" + lessonId + "/quizzes",
                rejectedTeacherToken,
                Map.of(
                        "title", "Blocked rejected quiz",
                        "description", "Should not work")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(APPROVED_TEACHERS_ONLY_MESSAGE));
    }

    @Test
    void optionBasedQuizQuestionWithoutExactlyOneCorrectOptionReturnsBadRequest() throws Exception {
        String teacherEmail = uniqueEmail("quiz-validation-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        Long lessonId = createLesson(teacherToken, "Quiz Validation Lesson");

        MvcResult quizResult = mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/lessons/" + lessonId + "/quizzes",
                teacherToken,
                Map.of(
                        "title", "Validation Quiz",
                        "description", "Validates question options")))
                .andExpect(status().isOk())
                .andReturn();

        Long quizId = extractId(quizResult);

        mockMvc.perform(addQuizQuestion(quizId, teacherToken, Map.of(
                        "questionType", "MULTIPLE_CHOICE",
                        "questionText", "Choose the first tone.",
                        "points", 5,
                        "displayOrder", 1,
                        "options", List.of(
                                Map.of("optionText", "ma1", "correctOption", false, "displayOrder", 1),
                                Map.of("optionText", "ma3", "correctOption", false, "displayOrder", 2)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Exactly one correct answer option is required."));
    }

    @Test
    void nonOptionBasedQuizQuestionsIgnoreClientSuppliedOptions() throws Exception {
        String teacherEmail = uniqueEmail("quiz-free-text-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        Long lessonId = createLesson(teacherToken, "Free Text Quiz Lesson");

        MvcResult quizResult = mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/lessons/" + lessonId + "/quizzes",
                teacherToken,
                Map.of(
                        "title", "Free text quiz",
                        "description", "Options should be ignored")))
                .andExpect(status().isOk())
                .andReturn();
        Long quizId = extractId(quizResult);

        Map<String, Object> freeTextQuestionPayload = Map.of(
                "questionType", "FILL_BLANK",
                "questionText", "Fill in the greeting: ni ____",
                "correctAnswerText", "hao",
                "points", 5,
                "displayOrder", 1,
                "options", List.of(
                        Map.of("optionText", "malicious distractor", "correctOption", true, "displayOrder", 1)));

        MvcResult questionResult = mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/quizzes/" + quizId + "/questions",
                teacherToken,
                freeTextQuestionPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].options").isEmpty())
                .andReturn();

        Long questionId = responseData(questionResult)
                .path("questions")
                .get(0)
                .path("id")
                .asLong();

        assertThat(quizOptionRepository.findByQuestionIdOrderBySortOrderAscCreatedAtAsc(questionId)).isEmpty();
    }

    @Test
    void studentCannotDiscoverOrLoadQuizWhenParentLessonIsArchived() throws Exception {
        String teacherEmail = uniqueEmail("quiz-archived-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        Long lessonId = createLesson(teacherToken, "Archived Quiz Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);
        String studentToken = registerStudentAndLogin(uniqueEmail("quiz-archived-student"));

        mockMvc.perform(authenticatedPatch("/api/v1/teachers/lessons/" + lessonId + "/archive", teacherToken))
                .andExpect(status().isOk());

        mockMvc.perform(authenticatedGet("/api/v1/students/quizzes/" + quizId, studentToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(oversizedPageGet("/api/v1/students/quizzes", studentToken)
                .param("lessonId", String.valueOf(lessonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void quizSubmissionRejectsDuplicateQuestionAnswersAndForeignQuestionIds() throws Exception {
        String teacherEmail = uniqueEmail("quiz-submit-guard-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);

        Long firstLessonId = createLesson(teacherToken, "Guarded Quiz Lesson");
        Long firstQuizId = createQuizWithQuestions(teacherToken, firstLessonId);
        Long secondLessonId = createLesson(teacherToken, "Foreign Quiz Lesson");
        Long secondQuizId = createQuizWithQuestions(teacherToken, secondLessonId);

        String studentToken = registerStudentAndLogin(uniqueEmail("quiz-submit-guard-student"));

        MvcResult firstQuizLoadResult = mockMvc.perform(authenticatedGet(
                "/api/v1/students/quizzes/" + firstQuizId,
                studentToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode firstQuizData = responseData(firstQuizLoadResult);
        Long firstQuestionId = firstQuizData.path("questions").get(0).path("id").asLong();
        Long secondQuestionId = firstQuizData.path("questions").get(1).path("id").asLong();

        MvcResult secondQuizLoadResult = mockMvc.perform(authenticatedGet(
                "/api/v1/students/quizzes/" + secondQuizId,
                studentToken))
                .andExpect(status().isOk())
                .andReturn();
        Long foreignQuestionId = responseData(secondQuizLoadResult)
                .path("questions")
                .get(0)
                .path("id")
                .asLong();

        mockMvc.perform(submitQuizAttempt(
                firstQuizId,
                studentToken,
                answer(firstQuestionId, "ma1"),
                answer(firstQuestionId, "ma3"),
                answer(secondQuestionId, TRUE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(DUPLICATE_ANSWER_MESSAGE));

        mockMvc.perform(submitQuizAttempt(
                firstQuizId,
                studentToken,
                answer(firstQuestionId, "ma1"),
                answer(foreignQuestionId, "ma1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(FOREIGN_ANSWER_MESSAGE));
    }

    private void approveTeacherProfile(String teacherEmail) throws Exception {
        TeacherProfile profile = registerTeacherWithVerificationDocument(teacherEmail);
        String adminToken = adminToken();
        mockMvc.perform(authenticatedJsonPost(
                "/api/v1/admin/teachers/" + profile.getId() + "/approve",
                adminToken,
                Map.of("note", "Approved for interactive learning")))
                .andExpect(status().isOk());

        TeacherProfile refreshed = teacherProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(refreshed.getTeacherStatus()).isEqualTo(TeacherStatus.APPROVED);
    }

    private TeacherProfile registerTeacherWithVerificationDocument(String email) throws Exception {
        registerTeacher(email);
        String teacherToken = userToken(email);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "qualification.pdf",
                "application/pdf",
                TestUploadFixtures.minimalPdf());

        mockMvc.perform(multipart("/api/v1/teachers/verification-documents")
                .file(file)
                .param("documentType", QUALIFICATION_DOCUMENT_TYPE)
                .param("description", "Qualification proof")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return teacherProfileRepository.findDetailedByUserId(user.getId()).orElseThrow();
    }

    private void registerTeacher(String email) throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/register/teacher", Map.of(
                        "fullName", "Teacher " + email,
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "bio", "Teacher bio")))
                .andExpect(status().isOk());
    }

    private String registerStudentAndLogin(String email) throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/register/student", Map.of(
                        "fullName", "Student " + email,
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "preferredLanguage", "en")))
                .andExpect(status().isOk());

        return userToken(email);
    }

    private Long createLesson(String teacherToken, String title) throws Exception {
        MvcResult result = mockMvc.perform(authenticatedJsonPost("/api/v1/teachers/lessons", teacherToken, Map.of(
                        "title", title,
                        "description", "Quiz lesson",
                        "levelId", BEGINNER_LEVEL_ID,
                        "categoryId", VOCABULARY_CATEGORY_ID,
                        "learningType", LEARNING_RECORDED,
                        "accessType", ACCESS_FREE,
                        "price", 0,
                        "estimatedDurationMinutes", 15,
                        "basicContent", "Practice content")))
                .andExpect(status().isOk())
                .andReturn();
        return extractId(result);
    }

    private Long createQuizWithQuestions(String teacherToken, Long lessonId) throws Exception {
        MvcResult quizResult = mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/lessons/" + lessonId + "/quizzes",
                teacherToken,
                Map.of(
                        "title", QUIZ_TITLE,
                        "description", "Check pinyin and true or false")))
                .andExpect(status().isOk())
                .andReturn();
        Long quizId = extractId(quizResult);

        mockMvc.perform(addQuizQuestion(quizId, teacherToken, Map.of(
                        "questionType", "CHOOSE_TONE",
                        "questionText", "Which tone matches ma?",
                        "points", 5,
                        "displayOrder", 1,
                        "explanation", "ma1 is first tone.",
                        "options", List.of(
                                Map.of("optionText", "ma1", "correctOption", true, "displayOrder", 1),
                                Map.of("optionText", "ma3", "correctOption", false, "displayOrder", 2)))))
                .andExpect(status().isOk());

        mockMvc.perform(addQuizQuestion(quizId, teacherToken, Map.of(
                        "questionType", "TRUE_FALSE",
                        "questionText", "ni hao means goodbye.",
                        "points", 5,
                        "displayOrder", 2,
                        "explanation", "ni hao means hello.",
                        "options", List.of(
                                Map.of("optionText", TRUE, "correctOption", false, "displayOrder", 1),
                                Map.of("optionText", FALSE, "correctOption", true, "displayOrder", 2)))))
                .andExpect(status().isOk());

        return quizId;
    }

    private StudentProfile findStudentProfile(String email) {
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return studentProfileRepository.findByUserId(user.getId()).orElseThrow();
    }

    private Long extractId(MvcResult result) throws Exception {
        return responseData(result).path("id").asLong();
    }

    private Long extractAttemptId(MvcResult result) throws Exception {
        return responseData(result).path("attemptId").asLong();
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String adminToken() throws Exception {
        return userToken(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    private String userToken(String email) throws Exception {
        return userToken(email, USER_PASSWORD);
    }

    private String userToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(jsonPost("/api/v1/auth/login", Map.of(
                        "email", email,
                        "password", password)))
                .andExpect(status().isOk())
                .andReturn();

        return responseData(result).path("token").asText();
    }

    private MockHttpServletRequestBuilder authenticatedGet(String path, String token) {
        return get(path).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    private MockHttpServletRequestBuilder oversizedPageGet(String path, String token) {
        return authenticatedGet(path, token).param("size", String.valueOf(OVERSIZED_PAGE_SIZE));
    }

    private MockHttpServletRequestBuilder authenticatedJsonPost(String path, String token, Map<String, ?> payload)
            throws Exception {
        return jsonPost(path, payload).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    private MockHttpServletRequestBuilder authenticatedJsonPut(String path, String token, Map<String, ?> payload)
            throws Exception {
        return jsonPut(path, payload).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    private MockHttpServletRequestBuilder authenticatedPatch(String path, String token) {
        return patch(path)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    private MockHttpServletRequestBuilder authenticatedDelete(String path, String token) {
        return delete(path)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    private MockHttpServletRequestBuilder addQuizQuestion(Long quizId, String teacherToken, Map<String, ?> payload)
            throws Exception {
        return authenticatedJsonPost("/api/v1/teachers/quizzes/" + quizId + "/questions", teacherToken, payload);
    }

    @SafeVarargs
    private final MockHttpServletRequestBuilder submitQuizAttempt(
            Long quizId,
            String studentToken,
            Map<String, ?>... answers) throws Exception {
        return authenticatedJsonPost(
                "/api/v1/students/quizzes/" + quizId + "/attempts",
                studentToken,
                Map.of("answers", List.of(answers)));
    }

    private static Map<String, ?> answer(Long questionId, String answerText) {
        return Map.of("questionId", questionId, "answerText", answerText);
    }

    private MockHttpServletRequestBuilder jsonPost(String path, Map<String, ?> payload) throws Exception {
        return post(path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(payload));
    }

    private MockHttpServletRequestBuilder jsonPut(String path, Map<String, ?> payload) throws Exception {
        return put(path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(payload));
    }

    private String json(Map<String, ?> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
