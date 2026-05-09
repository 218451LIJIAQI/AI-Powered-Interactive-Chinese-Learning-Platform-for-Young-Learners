package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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

import com.example.aichinese.entity.AiFeedbackRecord;
import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.AiFeedbackRecordRepository;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.AiLogService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!",
        "AI_PROVIDER=deepseek",
        "AI_API_BASE_URL=https://api.deepseek.com",
        "AI_MODEL_NAME=deepseek-chat",
        "AI_ENABLE_REAL_PROVIDER=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiIntegrationTests {

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
    private AiUsageLogRepository aiUsageLogRepository;

    @Autowired
    private AiFeedbackRecordRepository aiFeedbackRecordRepository;

    @Test
    void aiChatRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/ai/chat")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "message", "Hello AI tutor"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void aiVocabularyHelperValidatesInputAndReturnsFriendlyUnavailableWhenProviderDisabled() throws Exception {
        String studentToken = registerStudentAndLogin(uniqueEmail("ai-vocab-student"));

        mockMvc.perform(post("/api/v1/ai/vocabulary-helper")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "word", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/v1/ai/vocabulary-helper")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "word", "apple",
                        "inputLanguage", "en",
                        "outputLanguage", "zh",
                        "learnerLevel", "Beginner",
                        "languagePreference", "en"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.simpleExplanation").value("AI tutor is currently unavailable. Please try again later."));

        assertThat(aiUsageLogRepository.findAll()).anyMatch(log -> "VOCABULARY_HELPER".equals(log.getFeatureKey())
                && "UNAVAILABLE".equals(log.getStatus()));
    }

    @Test
    void aiSentencePracticeValidationAndChatUsageLogWork() throws Exception {
        String studentToken = registerStudentAndLogin(uniqueEmail("ai-chat-student"));

        mockMvc.perform(post("/api/v1/ai/sentence-practice")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "sentence", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/v1/ai/chat")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "message", "Can you help me say hello in Chinese?",
                        "languagePreference", "en"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").isNumber())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.aiResponse").value("AI tutor is currently unavailable. Please try again later."));

        assertThat(aiUsageLogRepository.findAll()).anyMatch(log -> "CHAT_TUTOR".equals(log.getFeatureKey()));
    }

    @Test
    void quizAndStoryGeneratorReturnSafeUnavailableDrafts() throws Exception {
        String studentToken = registerStudentAndLogin(uniqueEmail("ai-generator-student"));

        mockMvc.perform(post("/api/v1/ai/quiz-generator")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "topic", "Animals",
                        "level", "Beginner",
                        "category", "Vocabulary",
                        "numberOfQuestions", 3,
                        "languagePreference", "en"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usageMode").value("TEMPORARY_SELF_PRACTICE"))
                .andExpect(jsonPath("$.data.questions[0].questionText").exists());

        mockMvc.perform(post("/api/v1/ai/story-generator")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "topic", "My cat",
                        "level", "Beginner",
                        "languagePreference", "en"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usageMode").value("SELF_PRACTICE"))
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"));
    }

    @Test
    void teacherAssistantRequiresApprovedTeacherOrAdmin() throws Exception {
        String pendingTeacherEmail = uniqueEmail("ai-pending-teacher");
        registerTeacher(pendingTeacherEmail);
        String pendingTeacherToken = loginAndExtractToken(pendingTeacherEmail, "Password123!");

        mockMvc.perform(post("/api/v1/ai/teacher-assistant")
                .with(csrf())
                .header("Authorization", "Bearer " + pendingTeacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "topic", "Greetings",
                        "level", "Beginner",
                        "languagePreference", "en"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only approved teachers can use the teacher assistant."));

        String approvedTeacherEmail = uniqueEmail("ai-approved-teacher");
        approveTeacherProfile(approvedTeacherEmail);
        String approvedTeacherToken = loginAndExtractToken(approvedTeacherEmail, "Password123!");

        mockMvc.perform(post("/api/v1/ai/teacher-assistant")
                .with(csrf())
                .header("Authorization", "Bearer " + approvedTeacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "topic", "Greetings",
                        "level", "Beginner",
                        "languagePreference", "en"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"));
    }

    @Test
    void adminAiUsageLogsRequireAdminRole() throws Exception {
        String studentToken = registerStudentAndLogin(uniqueEmail("ai-log-student"));

        mockMvc.perform(get("/api/v1/admin/ai/usage-logs")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");

        mockMvc.perform(get("/api/v1/admin/ai/usage-logs")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void pronunciationAndWritingFeedbackHandlePrototypeUploadsGracefully() throws Exception {
        String studentToken = registerStudentAndLogin(uniqueEmail("ai-feedback-student"));

        MockMultipartFile audioFile = new MockMultipartFile(
                "file",
                "voice.webm",
                "audio/webm",
                "fake-webm-data".getBytes());

        MvcResult pronunciationResult = mockMvc.perform(multipart("/api/v1/ai/pronunciation-feedback")
                .file(audioFile)
                .param("targetText", "你好")
                .param("pinyin", "ni hao")
                .param("languagePreference", "en")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andReturn();

        MockMultipartFile imageFile = new MockMultipartFile(
                "file",
                "character.png",
                "image/png",
                "png-image".getBytes());

        mockMvc.perform(multipart("/api/v1/ai/writing-feedback")
                .file(imageFile)
                .param("targetCharacter", "你")
                .param("languagePreference", "en")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"));

        List<AiFeedbackRecord> feedbackRecords = aiFeedbackRecordRepository.findAll();
        assertThat(feedbackRecords).hasSizeGreaterThanOrEqualTo(2);

        String body = pronunciationResult.getResponse().getContentAsString();
        assertThat(body).doesNotContain("target/test-uploads");
        assertThat(body).doesNotContain("passwordHash");

        AiUsageLog latestAudioLog = aiUsageLogRepository.findAll().stream()
                .filter(log -> "PRONUNCIATION_FEEDBACK".equals(log.getFeatureKey()))
                .findFirst()
                .orElseThrow();
        assertThat(latestAudioLog.getErrorSummary()).contains("audio analysis");

        Path aiFeedbackDir = Path.of("target", "test-uploads", "ai-feedback");
        assertThat(Files.exists(aiFeedbackDir)).isTrue();
        String storedFileName = feedbackRecords.stream()
                .filter(record -> record.getInputFileUrl() != null)
                .max(Comparator.comparing(AiFeedbackRecord::getCreatedAt))
                .map(AiFeedbackRecord::getInputFileUrl)
                .orElseThrow();
        assertThat(new File(aiFeedbackDir.toFile(), storedFileName)).exists();
    }

    @Test
    void studentLearningRecommendationAndProgressInsightStayWithinOwnDataScope() throws Exception {
        String firstStudentEmail = uniqueEmail("ai-own-student");
        String firstStudentToken = registerStudentAndLogin(firstStudentEmail);
        String secondStudentEmail = uniqueEmail("ai-other-student");
        registerStudentAndLogin(secondStudentEmail);

        Long otherStudentId = studentProfileRepository.findByUserId(
                userRepository.findByEmailWithRoles(secondStudentEmail).orElseThrow().getId()).orElseThrow().getId();

        mockMvc.perform(post("/api/v1/ai/learning-recommendation")
                .with(csrf())
                .header("Authorization", "Bearer " + firstStudentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "studentId", otherStudentId,
                        "languagePreference", "en"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Students can only request learning recommendations for their own account."));

        mockMvc.perform(post("/api/v1/ai/progress-insight")
                .with(csrf())
                .header("Authorization", "Bearer " + firstStudentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "studentId", otherStudentId,
                        "audience", "STUDENT",
                        "languagePreference", "en"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Students can only request progress insight for their own account."));
    }

    @Test
    void aiTinyTextSummariesStayWithinMysqlTinyTextByteLimit() {
        String repeatedChinese = "你好".repeat(200);

        String summary = AiLogService.summarizeForTinyText(repeatedChinese);

        assertThat(summary).isNotBlank();
        assertThat(summary.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(AiLogService.TINYTEXT_SAFE_BYTES);
    }

    private void approveTeacherProfile(String teacherEmail) throws Exception {
        TeacherProfile profile = registerTeacherWithVerificationDocument(teacherEmail);
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        mockMvc.perform(post("/api/v1/admin/teachers/" + profile.getId() + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Approved for AI tools"))))
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
                "verification".getBytes());

        mockMvc.perform(multipart("/api/v1/teachers/verification-documents")
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

        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        assertThat(user.getRoles()).extracting(role -> role.getName()).contains(RoleName.STUDENT);
        return loginAndExtractToken(email, "Password123!");
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
