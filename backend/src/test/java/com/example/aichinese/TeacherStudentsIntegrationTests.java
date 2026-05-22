package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.example.aichinese.Support.TestUploadFixtures;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!",
        "app.email.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeacherStudentsIntegrationTests {

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

    @Test
    void teacherStudentsShouldReturnLinkedStudentSummaries() throws Exception {
        String teacherEmail = uniqueEmail("teacher-students");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");

        Long lessonId = createLesson(teacherToken, "Teacher Students Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);

        String studentEmail = uniqueEmail("teacher-students-student");
        String studentToken = registerStudentAndLogin(studentEmail);
        StudentProfile studentProfile = findStudentProfile(studentEmail);
        User studentUser = userRepository.findByEmailWithRoles(studentEmail).orElseThrow();

        submitQuizAttempt(studentToken, quizId);

        mockMvc.perform(post("/api/v1/teachers/feedback")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "studentId", studentProfile.getId(),
                        "lessonId", lessonId,
                        "feedbackType", "PROGRESS",
                        "message", "Great effort. Keep reviewing the greeting pattern."))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/teachers/students")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].studentId").value(studentProfile.getId()))
                .andExpect(jsonPath("$.data.items[0].userId").value(studentUser.getId()))
                .andExpect(jsonPath("$.data.items[0].fullName").value(studentUser.getDisplayName()))
                .andExpect(jsonPath("$.data.items[0].email").value(studentEmail))
                .andExpect(jsonPath("$.data.items[0].progressPercent").value(100))
                .andExpect(jsonPath("$.data.items[0].quizAveragePercent").value(50.0))
                .andExpect(jsonPath("$.data.items[0].atRisk").value(true))
                .andExpect(jsonPath("$.data.items[0].weakAreas[0]").value("Progress"))
                .andExpect(jsonPath("$.data.items[0].latestFeedbackType").value("PROGRESS"))
                .andExpect(jsonPath("$.data.items[0].latestFeedbackAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].latestActivityAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].joinedAt").isNotEmpty());
    }

    @Test
    void teacherStudentsShouldRejectWrongRole() throws Exception {
        String studentToken = registerStudentAndLogin(uniqueEmail("teacher-students-wrong-role"));

        mockMvc.perform(get("/api/v1/teachers/students")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    private Long createLesson(String teacherToken, String title) throws Exception {
        MvcResult lessonResult = mockMvc.perform(post("/api/v1/teachers/lessons")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "description", "Teacher students integration lesson",
                        "levelId", 1,
                        "categoryId", 3,
                        "learningType", "RECORDED",
                        "accessType", "FREE",
                        "price", 0,
                        "estimatedDurationMinutes", 15,
                        "basicContent", "Teacher students integration content"))))
                .andExpect(status().isOk())
                .andReturn();

        return extractDataId(lessonResult);
    }

    private Long createQuizWithQuestions(String teacherToken, Long lessonId) throws Exception {
        MvcResult quizResult = mockMvc.perform(post("/api/v1/teachers/lessons/" + lessonId + "/quizzes")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Teacher students quiz",
                        "description", "Integration quiz"))))
                .andExpect(status().isOk())
                .andReturn();

        Long quizId = extractDataId(quizResult);

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
                        "questionText", "ni hao means hello.",
                        "points", 5,
                        "displayOrder", 2,
                        "explanation", "ni hao means hello.",
                        "options", List.of(
                                Map.of("optionText", "TRUE", "correctOption", true, "displayOrder", 1),
                                Map.of("optionText", "FALSE", "correctOption", false, "displayOrder", 2))))))
                .andExpect(status().isOk());

        return quizId;
    }

    private void submitQuizAttempt(String studentToken, Long quizId) throws Exception {
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(5))
                .andExpect(jsonPath("$.data.maxScore").value(10));
    }

    private void approveTeacherProfile(String teacherEmail) throws Exception {
        TeacherProfile profile = registerTeacherWithVerificationDocument(teacherEmail);
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");

        mockMvc.perform(post("/api/v1/admin/teachers/" + profile.getId() + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Approved for teacher students integration test"))))
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

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/v1/teachers/verification-documents")
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

    private StudentProfile findStudentProfile(String email) {
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return studentProfileRepository.findByUserId(user.getId()).orElseThrow();
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

    private Long extractDataId(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("id").asLong();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
