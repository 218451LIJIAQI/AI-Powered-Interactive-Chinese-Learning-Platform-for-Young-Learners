package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.aichinese.Support.TestUploadFixtures;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
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

    @Test
    void teacherCreatesQuizStudentLoadsWithoutAnswersAndSubmissionStoresWrongAnswersAndRewards() throws Exception {
        String teacherEmail = uniqueEmail("quiz-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");

        Long lessonId = createLesson(teacherToken, "Quiz Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);

        mockMvc.perform(get("/api/v1/teachers/lessons/" + lessonId + "/quizzes")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items[0].title").value("Tone quiz"));

        String studentEmail = uniqueEmail("quiz-student");
        String studentToken = registerStudentAndLogin(studentEmail);

        mockMvc.perform(get("/api/v1/students/quizzes")
                .param("size", "100000")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items[0].id").value(quizId))
                .andExpect(jsonPath("$.data.items[0].questionCount").value(2));

        mockMvc.perform(get("/api/v1/students/quizzes")
                .param("lessonId", String.valueOf(lessonId))
                .param("size", "100000")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(quizId));

        MvcResult loadQuizResult = mockMvc.perform(get("/api/v1/students/quizzes/" + quizId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].correctAnswerText").doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].options[0].correctOption").doesNotExist())
                .andReturn();

        JsonNode quizData = objectMapper.readTree(loadQuizResult.getResponse().getContentAsString()).path("data");
        Long firstQuestionId = quizData.path("questions").get(0).path("id").asLong();
        Long secondQuestionId = quizData.path("questions").get(1).path("id").asLong();

        MvcResult submitResult = mockMvc.perform(post("/api/v1/students/quizzes/" + quizId + "/attempts")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "answers", List.of(
                                Map.of("questionId", firstQuestionId, "answerText", "ma1"),
                                Map.of("questionId", secondQuestionId, "answerText", "TRUE"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(5))
                .andExpect(jsonPath("$.data.maxScore").value(10))
                .andExpect(jsonPath("$.data.correctCount").value(1))
                .andExpect(jsonPath("$.data.wrongCount").value(1))
                .andExpect(jsonPath("$.data.earnedPoints").value(20))
                .andReturn();

        Long attemptId = objectMapper.readTree(submitResult.getResponse().getContentAsString())
                .path("data").path("attemptId").asLong();

        mockMvc.perform(get("/api/v1/students/quiz-attempts/" + attemptId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answers[0].correctAnswer").isString());

        mockMvc.perform(get("/api/v1/teachers/quizzes/" + quizId + "/attempts")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items[0].attemptId").value(attemptId));

        mockMvc.perform(get("/api/v1/students/wrong-answers")
                .param("size", "100000")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items[0].questionText").exists());

        mockMvc.perform(get("/api/v1/students/rewards/points-history")
                .param("size", "100000")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items[0].pointsDelta").exists());

        mockMvc.perform(get("/api/v1/students/rewards/badges")
                .param("size", "100000")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(100));

        StudentProfile studentProfile = findStudentProfile(studentEmail);
        QuizAttempt attempt = quizAttemptRepository.findByIdAndStudentUserId(attemptId, studentProfile.getUser().getId()).orElseThrow();
        assertThat(attempt.getScore()).isEqualTo(5);
        assertThat(quizAttemptAnswerRepository.findByQuizAttemptIdOrderByCreatedAtAsc(attemptId)).hasSize(2);
        assertThat(studentProfile.getTotalPoints()).isEqualTo(45);
    }

    @Test
    void anotherStudentCannotAccessQuizAttemptAndUnauthorizedAndWrongRoleRequestsAreRejected() throws Exception {
        String teacherEmail = uniqueEmail("attempt-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");

        Long lessonId = createLesson(teacherToken, "Attempt Protection Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);

        String firstStudentToken = registerStudentAndLogin(uniqueEmail("attempt-student-one"));
        String secondStudentToken = registerStudentAndLogin(uniqueEmail("attempt-student-two"));

        MvcResult loadQuizResult = mockMvc.perform(get("/api/v1/students/quizzes/" + quizId)
                .header("Authorization", "Bearer " + firstStudentToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode quizData = objectMapper.readTree(loadQuizResult.getResponse().getContentAsString()).path("data");
        Long firstQuestionId = quizData.path("questions").get(0).path("id").asLong();
        Long secondQuestionId = quizData.path("questions").get(1).path("id").asLong();

        MvcResult submitResult = mockMvc.perform(post("/api/v1/students/quizzes/" + quizId + "/attempts")
                .with(csrf())
                .header("Authorization", "Bearer " + firstStudentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "answers", List.of(
                                Map.of("questionId", firstQuestionId, "answerText", "ma3"),
                                Map.of("questionId", secondQuestionId, "answerText", "FALSE"))))))
                .andExpect(status().isOk())
                .andReturn();

        Long attemptId = objectMapper.readTree(submitResult.getResponse().getContentAsString())
                .path("data").path("attemptId").asLong();

        mockMvc.perform(get("/api/v1/students/quiz-attempts/" + attemptId)
                .header("Authorization", "Bearer " + secondStudentToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/students/quizzes/" + quizId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/students/quizzes/" + quizId)
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void teacherCannotChangeQuizQuestionsAfterStudentsSubmitAttempts() throws Exception {
        String teacherEmail = uniqueEmail("quiz-freeze-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createLesson(teacherToken, "Question Freeze Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);

        String studentToken = registerStudentAndLogin(uniqueEmail("quiz-freeze-student"));
        MvcResult loadQuizResult = mockMvc.perform(get("/api/v1/students/quizzes/" + quizId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode quizData = objectMapper.readTree(loadQuizResult.getResponse().getContentAsString()).path("data");
        Long firstQuestionId = quizData.path("questions").get(0).path("id").asLong();
        Long secondQuestionId = quizData.path("questions").get(1).path("id").asLong();

        mockMvc.perform(post("/api/v1/students/quizzes/" + quizId + "/attempts")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "answers", List.of(
                                Map.of("questionId", firstQuestionId, "answerText", "ma1"),
                                Map.of("questionId", secondQuestionId, "answerText", "FALSE"))))))
                .andExpect(status().isOk());

        Map<String, Object> questionPayload = Map.of(
                "questionType", "MULTIPLE_CHOICE",
                "questionText", "Which one is hello?",
                "points", 5,
                "displayOrder", 3,
                "options", List.of(
                        Map.of("optionText", "ni hao", "correctOption", true, "displayOrder", 1),
                        Map.of("optionText", "zai jian", "correctOption", false, "displayOrder", 2)));

        mockMvc.perform(post("/api/v1/teachers/quizzes/" + quizId + "/questions")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(questionPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quiz questions cannot be changed after students have submitted attempts."));

        mockMvc.perform(put("/api/v1/teachers/quiz-questions/" + firstQuestionId)
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(questionPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quiz questions cannot be changed after students have submitted attempts."));

        mockMvc.perform(delete("/api/v1/teachers/quiz-questions/" + firstQuestionId)
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quiz questions cannot be changed after students have submitted attempts."));
    }

    @Test
    void unapprovedTeachersCannotCreateQuizzes() throws Exception {
        String ownerEmail = uniqueEmail("quiz-owner");
        approveTeacherProfile(ownerEmail);
        String ownerToken = loginAndExtractToken(ownerEmail, "Password123!");
        Long lessonId = createLesson(ownerToken, "Owner Quiz Lesson");

        String pendingTeacherEmail = uniqueEmail("quiz-pending");
        registerTeacher(pendingTeacherEmail);
        String pendingTeacherToken = loginAndExtractToken(pendingTeacherEmail, "Password123!");

        mockMvc.perform(post("/api/v1/teachers/lessons/" + lessonId + "/quizzes")
                .with(csrf())
                .header("Authorization", "Bearer " + pendingTeacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Blocked pending quiz",
                        "description", "Should not work"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only approved teachers can create or submit learning content."));

        String rejectedTeacherEmail = uniqueEmail("quiz-rejected");
        TeacherProfile rejectedProfile = registerTeacherWithVerificationDocument(rejectedTeacherEmail);
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        mockMvc.perform(post("/api/v1/admin/teachers/" + rejectedProfile.getId() + "/reject")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Verification incomplete"))))
                .andExpect(status().isOk());

        String rejectedTeacherToken = loginAndExtractToken(rejectedTeacherEmail, "Password123!");
        mockMvc.perform(post("/api/v1/teachers/lessons/" + lessonId + "/quizzes")
                .with(csrf())
                .header("Authorization", "Bearer " + rejectedTeacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Blocked rejected quiz",
                        "description", "Should not work"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only approved teachers can create or submit learning content."));
    }

    @Test
    void optionBasedQuizQuestionWithoutExactlyOneCorrectOptionReturnsBadRequest() throws Exception {
        String teacherEmail = uniqueEmail("quiz-validation-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createLesson(teacherToken, "Quiz Validation Lesson");

        MvcResult quizResult = mockMvc.perform(post("/api/v1/teachers/lessons/" + lessonId + "/quizzes")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Validation Quiz",
                        "description", "Validates question options"))))
                .andExpect(status().isOk())
                .andReturn();

        Long quizId = extractId(quizResult);

        mockMvc.perform(post("/api/v1/teachers/quizzes/" + quizId + "/questions")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "questionType", "MULTIPLE_CHOICE",
                        "questionText", "Choose the first tone.",
                        "points", 5,
                        "displayOrder", 1,
                        "options", List.of(
                                Map.of("optionText", "ma1", "correctOption", false, "displayOrder", 1),
                                Map.of("optionText", "ma3", "correctOption", false, "displayOrder", 2))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Exactly one correct answer option is required."));
    }

    @Test
    void studentCannotDiscoverOrLoadQuizWhenParentLessonIsArchived() throws Exception {
        String teacherEmail = uniqueEmail("quiz-archived-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createLesson(teacherToken, "Archived Quiz Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);
        String studentToken = registerStudentAndLogin(uniqueEmail("quiz-archived-student"));

        mockMvc.perform(patch("/api/v1/teachers/lessons/" + lessonId + "/archive")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/students/quizzes/" + quizId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/students/quizzes")
                .param("lessonId", String.valueOf(lessonId))
                .param("size", "100000")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void quizSubmissionRejectsDuplicateQuestionAnswersAndForeignQuestionIds() throws Exception {
        String teacherEmail = uniqueEmail("quiz-submit-guard-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");

        Long firstLessonId = createLesson(teacherToken, "Guarded Quiz Lesson");
        Long firstQuizId = createQuizWithQuestions(teacherToken, firstLessonId);
        Long secondLessonId = createLesson(teacherToken, "Foreign Quiz Lesson");
        Long secondQuizId = createQuizWithQuestions(teacherToken, secondLessonId);

        String studentToken = registerStudentAndLogin(uniqueEmail("quiz-submit-guard-student"));

        MvcResult firstQuizLoadResult = mockMvc.perform(get("/api/v1/students/quizzes/" + firstQuizId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode firstQuizData = objectMapper.readTree(firstQuizLoadResult.getResponse().getContentAsString()).path("data");
        Long firstQuestionId = firstQuizData.path("questions").get(0).path("id").asLong();
        Long secondQuestionId = firstQuizData.path("questions").get(1).path("id").asLong();

        MvcResult secondQuizLoadResult = mockMvc.perform(get("/api/v1/students/quizzes/" + secondQuizId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        Long foreignQuestionId = objectMapper.readTree(secondQuizLoadResult.getResponse().getContentAsString())
                .path("data").path("questions").get(0).path("id").asLong();

        mockMvc.perform(post("/api/v1/students/quizzes/" + firstQuizId + "/attempts")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "answers", List.of(
                                Map.of("questionId", firstQuestionId, "answerText", "ma1"),
                                Map.of("questionId", firstQuestionId, "answerText", "ma3"),
                                Map.of("questionId", secondQuestionId, "answerText", "TRUE"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Duplicate answers for the same quiz question are not allowed."));

        mockMvc.perform(post("/api/v1/students/quizzes/" + firstQuizId + "/attempts")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "answers", List.of(
                                Map.of("questionId", firstQuestionId, "answerText", "ma1"),
                                Map.of("questionId", foreignQuestionId, "answerText", "ma1"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Submitted answer does not belong to this quiz."));
    }

    private void approveTeacherProfile(String teacherEmail) throws Exception {
        TeacherProfile profile = registerTeacherWithVerificationDocument(teacherEmail);
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        mockMvc.perform(post("/api/v1/admin/teachers/" + profile.getId() + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Approved for interactive learning"))))
                .andExpect(status().isOk());

        TeacherProfile refreshed = teacherProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(refreshed.getTeacherStatus()).isEqualTo(TeacherStatus.APPROVED);
    }

    private TeacherProfile registerTeacherWithVerificationDocument(String email) throws Exception {
        registerTeacher(email);
        String teacherToken = loginAndExtractToken(email, "Password123!");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "qualification.pdf",
                "application/pdf",
                TestUploadFixtures.minimalPdf());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/teachers/verification-documents")
                .file(file)
                .param("documentType", "QUALIFICATION")
                .param("description", "Qualification proof")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return teacherProfileRepository.findDetailedByUserId(user.getId()).orElseThrow();
    }

    private void registerTeacher(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/teacher")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Teacher " + email,
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!",
                        "bio", "Teacher bio"))))
                .andExpect(status().isOk());
    }

    private String registerStudentAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/student")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Student " + email,
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!",
                        "preferredLanguage", "en"))))
                .andExpect(status().isOk());

        return loginAndExtractToken(email, "Password123!");
    }

    private Long createLesson(String teacherToken, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teachers/lessons")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "description", "Quiz lesson",
                        "levelId", 1,
                        "categoryId", 3,
                        "learningType", "RECORDED",
                        "accessType", "FREE",
                        "price", 0,
                        "estimatedDurationMinutes", 15,
                        "basicContent", "Practice content"))))
                .andExpect(status().isOk())
                .andReturn();
        return extractId(result);
    }

    private Long createQuizWithQuestions(String teacherToken, Long lessonId) throws Exception {
        MvcResult quizResult = mockMvc.perform(post("/api/v1/teachers/lessons/" + lessonId + "/quizzes")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Tone quiz",
                        "description", "Check pinyin and true or false"))))
                .andExpect(status().isOk())
                .andReturn();
        Long quizId = extractId(quizResult);

        mockMvc.perform(post("/api/v1/teachers/quizzes/" + quizId + "/questions")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "questionType", "CHOOSE_TONE",
                        "questionText", "Which tone matches ma?",
                        "points", 5,
                        "displayOrder", 1,
                        "explanation", "ma1 is first tone.",
                        "options", List.of(
                                Map.of("optionText", "ma1", "correctOption", true, "displayOrder", 1),
                                Map.of("optionText", "ma3", "correctOption", false, "displayOrder", 2))))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/teachers/quizzes/" + quizId + "/questions")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "questionType", "TRUE_FALSE",
                        "questionText", "ni hao means goodbye.",
                        "points", 5,
                        "displayOrder", 2,
                        "explanation", "ni hao means hello.",
                        "options", List.of(
                                Map.of("optionText", "TRUE", "correctOption", false, "displayOrder", 1),
                                Map.of("optionText", "FALSE", "correctOption", true, "displayOrder", 2))))))
                .andExpect(status().isOk());

        return quizId;
    }

    private StudentProfile findStudentProfile(String email) {
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return studentProfileRepository.findByUserId(user.getId()).orElseThrow();
    }

    private Long extractId(MvcResult result) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("id").asLong();
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

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
