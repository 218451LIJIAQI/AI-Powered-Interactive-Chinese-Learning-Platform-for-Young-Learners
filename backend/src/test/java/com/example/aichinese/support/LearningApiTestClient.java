package com.example.aichinese.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Small MockMvc client for the W4 learner-experience integration tests: accounts, approved
 * teachers, published lessons and quizzes (through the D4 review workflow) and JSON helpers.
 * Requires the test context properties DEFAULT_ADMIN_EMAIL / DEFAULT_ADMIN_PASSWORD below.
 */
public final class LearningApiTestClient {

    public static final String ADMIN_EMAIL = "admin@example.com";
    public static final String ADMIN_PASSWORD = "SuperSecure123!";
    public static final String USER_PASSWORD = "Password123!";
    private static final long BEGINNER_LEVEL_ID = 1L;
    private static final long CATEGORY_ID = 3L;

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final TeacherProfileRepository teacherProfileRepository;

    public LearningApiTestClient(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            TeacherProfileRepository teacherProfileRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.teacherProfileRepository = teacherProfileRepository;
    }

    // ----- accounts -----

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    public String registerStudent(String email) throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/register/student", null, Map.of(
                        "fullName", "Student " + email.substring(0, 8),
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "preferredLanguage", "en")))
                .andExpect(status().isOk());
        return login(email, USER_PASSWORD);
    }

    public String registerTeacher(String email) throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/register/teacher", null, Map.of(
                        "fullName", "Teacher " + email.substring(0, 8),
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "bio", "Mandarin teacher")))
                .andExpect(status().isOk());
        return login(email, USER_PASSWORD);
    }

    /** Registers a teacher, uploads a verification document and lets the admin approve it. */
    public String approvedTeacher(String email) throws Exception {
        String token = registerTeacher(email);
        MockMultipartFile file = new MockMultipartFile(
                "file", "qualification.pdf", "application/pdf", TestUploadFixtures.minimalPdf());
        mockMvc.perform(multipart("/api/v1/teachers/verification-documents")
                        .file(file)
                        .param("documentType", "QUALIFICATION")
                        .param("description", "Qualification proof")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        TeacherProfile profile = teacherProfileRepository.findDetailedByUserId(user.getId()).orElseThrow();
        mockMvc.perform(jsonPost("/api/v1/admin/teachers/" + profile.getId() + "/approve", adminToken(),
                        Map.of("note", "Approved")))
                .andExpect(status().isOk());
        return login(email, USER_PASSWORD);
    }

    public String adminToken() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    public String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(jsonPost("/api/v1/auth/login", null, Map.of(
                        "email", email,
                        "password", password)))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).path("token").asText();
    }

    // ----- content -----

    public Long publishedLesson(String teacherToken, String title, String content) throws Exception {
        Long lessonId = id(mockMvc.perform(jsonPost("/api/v1/teachers/lessons", teacherToken, Map.of(
                        "title", title,
                        "description", "Lesson summary",
                        "levelId", BEGINNER_LEVEL_ID,
                        "categoryId", CATEGORY_ID,
                        "learningType", "RECORDED",
                        "estimatedDurationMinutes", 15,
                        "basicContent", content)))
                .andExpect(status().isOk())
                .andReturn());
        ContentReviewTestSupport.publish(mockMvc, teacherToken, adminToken(), "LESSON", lessonId);
        return lessonId;
    }

    /** Creates a quiz with one option question (5 points) and one fill-blank question (5 points). */
    public Long publishedQuiz(String teacherToken, Long lessonId, String learningStage, String hint) throws Exception {
        Map<String, Object> quiz = new java.util.HashMap<>(Map.of(
                "title", "Greeting quiz",
                "description", "Greetings practice"));
        if (learningStage != null) {
            quiz.put("learningStage", learningStage);
        }
        Long quizId = id(mockMvc.perform(jsonPost("/api/v1/teachers/lessons/" + lessonId + "/quizzes", teacherToken, quiz))
                .andExpect(status().isOk())
                .andReturn());
        Map<String, Object> optionQuestion = new java.util.HashMap<>(Map.of(
                "questionType", "MULTIPLE_CHOICE",
                "questionText", "Which word means hello?",
                "points", 5,
                "displayOrder", 1,
                "explanation", "你好 (nǐ hǎo) means hello.",
                "options", List.of(
                        Map.of("optionText", "你好", "correctOption", true, "displayOrder", 1),
                        Map.of("optionText", "再见", "correctOption", false, "displayOrder", 2))));
        if (hint != null) {
            optionQuestion.put("hint", hint);
        }
        mockMvc.perform(jsonPost("/api/v1/teachers/quizzes/" + quizId + "/questions", teacherToken, optionQuestion))
                .andExpect(status().isOk());
        mockMvc.perform(jsonPost("/api/v1/teachers/quizzes/" + quizId + "/questions", teacherToken, Map.of(
                        "questionType", "FILL_BLANK",
                        "questionText", "Complete: nǐ ____ (hello)",
                        "correctAnswerText", "hǎo",
                        "points", 5,
                        "displayOrder", 2,
                        "explanation", "nǐ hǎo")))
                .andExpect(status().isOk());
        ContentReviewTestSupport.publish(mockMvc, teacherToken, adminToken(), "QUIZ", quizId);
        return quizId;
    }

    public JsonNode studentQuiz(String studentToken, Long quizId) throws Exception {
        return data(mockMvc.perform(authGet("/api/v1/students/quizzes/" + quizId, studentToken))
                .andExpect(status().isOk())
                .andReturn());
    }

    /** Answers the published quiz: correct option or the distractor, and the fill-blank text. */
    public MvcResult submitQuiz(String studentToken, Long quizId, boolean optionCorrect, String fillBlank) throws Exception {
        JsonNode quiz = studentQuiz(studentToken, quizId);
        JsonNode optionQuestion = quiz.path("questions").get(0);
        JsonNode fillQuestion = quiz.path("questions").get(1);
        long optionId = optionQuestion.path("options").get(optionCorrect ? 0 : 1).path("id").asLong();
        return mockMvc.perform(jsonPost("/api/v1/students/quizzes/" + quizId + "/attempts", studentToken, Map.of(
                        "answers", List.of(
                                Map.of("questionId", optionQuestion.path("id").asLong(), "optionId", optionId),
                                Map.of("questionId", fillQuestion.path("id").asLong(), "answerText", fillBlank)))))
                .andReturn();
    }

    // ----- HTTP helpers -----

    public MockHttpServletRequestBuilder authGet(String path, String token) {
        return get(path).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    public MockHttpServletRequestBuilder authPost(String path, String token) {
        return post(path).with(csrf()).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    public MockHttpServletRequestBuilder authPatch(String path, String token) {
        return patch(path).with(csrf()).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    public MockHttpServletRequestBuilder authDelete(String path, String token) {
        return delete(path).with(csrf()).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    public MockHttpServletRequestBuilder jsonPost(String path, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = post(path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        return token == null ? builder : builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    public MockHttpServletRequestBuilder jsonPut(String path, String token, Object body) throws Exception {
        return put(path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    public MockHttpServletRequestBuilder jsonPatch(String path, String token, Object body) throws Exception {
        return patch(path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    public JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    public Long id(MvcResult result) throws Exception {
        return data(result).path("id").asLong();
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
