package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.PracticeSubmissionRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.support.LearningApiTestClient;
import com.example.aichinese.support.TestUploadFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * W4 practice submissions (D7 / D7a) and account security (D12, Proposal Table 6.2): a stored
 * submission per upload with AI status, strict ownership, teacher review linked to the
 * submission (student notified), admin moderation view, profile update, change password with
 * session revocation, BCrypt hashing, expired JWT rejection and the reset-password rate limit.
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=" + LearningApiTestClient.ADMIN_EMAIL,
        "DEFAULT_ADMIN_PASSWORD=" + LearningApiTestClient.ADMIN_PASSWORD
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PracticeSubmissionAndAccountIntegrationTests {

    private static final String NEW_PASSWORD = "NewPassword456!";
    private static final String BCRYPT_PATTERN = "^\\$2[aby]\\$(1[0-9]|[2-3][0-9])\\$[./A-Za-z0-9]{53}$";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Autowired
    private PracticeSubmissionRepository practiceSubmissionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    private LearningApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new LearningApiTestClient(mockMvc, objectMapper, userRepository, teacherProfileRepository);
    }

    @Test
    void uploadsBecomeOwnedSubmissionsThatOnlyTheStudentsTeacherCanReviewAndTheStudentSeesFeedback() throws Exception {
        String teacherToken = api.approvedTeacher(LearningApiTestClient.uniqueEmail("w4-ps-teacher"));
        Long lessonId = api.publishedLesson(teacherToken, "Tones practice", "mā má mǎ mà");
        String otherTeacherToken = api.approvedTeacher(LearningApiTestClient.uniqueEmail("w4-ps-other-teacher"));
        String pendingTeacherToken = api.registerTeacher(LearningApiTestClient.uniqueEmail("w4-ps-pending"));
        String studentEmail = LearningApiTestClient.uniqueEmail("w4-ps-student");
        String studentToken = api.registerStudent(studentEmail);
        String otherStudentToken = api.registerStudent(LearningApiTestClient.uniqueEmail("w4-ps-other-student"));

        MvcResult upload = mockMvc.perform(multipart("/api/v1/ai/pronunciation-feedback")
                        .file(new MockMultipartFile("file", "voice.webm", "audio/webm", TestUploadFixtures.minimalWebm()))
                        .param("targetText", "妈妈")
                        .param("lessonId", String.valueOf(lessonId))
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, LearningApiTestClient.bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.submissionId").isNumber())
                .andReturn();
        long submissionId = api.data(upload).path("submissionId").asLong();
        mockMvc.perform(multipart("/api/v1/ai/writing-feedback")
                        .file(new MockMultipartFile("file", "character.png", "image/png", TestUploadFixtures.minimalPng()))
                        .param("targetCharacter", "你")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, LearningApiTestClient.bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").isNumber());
        mockMvc.perform(multipart("/api/v1/ai/writing-feedback")
                        .file(new MockMultipartFile("file", "character.png", "image/png", TestUploadFixtures.minimalPng()))
                        .param("targetCharacter", "你")
                        .param("lessonId", "999999")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, LearningApiTestClient.bearer(studentToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(api.authGet("/api/v1/students/practice-submissions", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.items[?(@.id == " + submissionId + ")].aiFeedbackStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.items[?(@.id == " + submissionId + ")].reviewStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.items[?(@.id == " + submissionId + ")].lessonTitle").value("Tones practice"))
                .andExpect(jsonPath("$.data.items[?(@.id == " + submissionId + ")].aiNotice").isNotEmpty());
        mockMvc.perform(api.authGet("/api/v1/students/practice-submissions/" + submissionId + "/file", studentToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/webm"));

        // Ownership: another student, an unrelated teacher and a pending teacher cannot see it.
        mockMvc.perform(api.authGet("/api/v1/students/practice-submissions/" + submissionId, otherStudentToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(api.authGet("/api/v1/students/practice-submissions/" + submissionId + "/file", otherStudentToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(api.authGet("/api/v1/teachers/practice-submissions/" + submissionId, otherTeacherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(api.authGet("/api/v1/teachers/practice-submissions", otherTeacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(0));
        mockMvc.perform(api.authGet("/api/v1/teachers/practice-submissions", pendingTeacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(api.jsonPost("/api/v1/teachers/practice-submissions/" + submissionId + "/feedback", otherTeacherToken,
                        Map.of("message", "Nice tones, keep going!")))
                .andExpect(status().isNotFound());

        // The lesson owner reviews it (the upload is linked to the teacher's lesson).
        mockMvc.perform(api.authGet("/api/v1/teachers/practice-submissions", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + submissionId + ")].fileUrl")
                        .value("/api/v1/teachers/practice-submissions/" + submissionId + "/file"));
        mockMvc.perform(api.authGet("/api/v1/teachers/practice-submissions/" + submissionId + "/file", teacherToken))
                .andExpect(status().isOk());
        mockMvc.perform(api.jsonPost("/api/v1/teachers/practice-submissions/" + submissionId + "/feedback", teacherToken,
                        Map.of("feedbackType", "CORRECTIVE", "message", "Your second tone rises nicely; lengthen the third tone.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("REVIEWED"))
                .andExpect(jsonPath("$.data.reviewedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.teacherFeedback[0].feedbackType").value("CORRECTIVE"));
        mockMvc.perform(api.authGet("/api/v1/teachers/practice-submissions", teacherToken))
                .andExpect(jsonPath("$.data.items[?(@.id == " + submissionId + ")]").isEmpty());
        mockMvc.perform(api.authGet("/api/v1/teachers/practice-submissions?reviewStatus=REVIEWED", teacherToken))
                .andExpect(jsonPath("$.data.items[?(@.id == " + submissionId + ")]").isNotEmpty());

        mockMvc.perform(api.authGet("/api/v1/students/practice-submissions/" + submissionId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("REVIEWED"))
                .andExpect(jsonPath("$.data.teacherFeedback[0].message")
                        .value("Your second tone rises nicely; lengthen the third tone."));
        mockMvc.perform(api.authGet("/api/v1/students/feedback", studentToken))
                .andExpect(jsonPath("$.data.items[0].practiceSubmissionId").value(submissionId));
        User studentUser = userRepository.findByEmailWithRoles(studentEmail).orElseThrow();
        assertThat(notificationRepository.findAll())
                .anySatisfy(notification -> {
                    assertThat(notification.getUser().getId()).isEqualTo(studentUser.getId());
                    assertThat(notification.getTargetPath()).isEqualTo("/student/practice-submissions");
                });
        assertThat(practiceSubmissionRepository.findById(submissionId).orElseThrow().getReviewStatus().name())
                .isEqualTo("REVIEWED");

        // Admin moderation view (read-only, file access audited).
        String adminToken = api.adminToken();
        mockMvc.perform(api.authGet("/api/v1/admin/practice-submissions/" + submissionId, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileUrl").value("/api/v1/admin/practice-submissions/" + submissionId + "/file"));
        mockMvc.perform(api.authGet("/api/v1/admin/practice-submissions/" + submissionId + "/file", adminToken))
                .andExpect(status().isOk());
        assertThat(auditLogRepository.findAll())
                .anyMatch(log -> "PRACTICE_SUBMISSION_FILE_VIEWED".equals(log.getActionType()));
        mockMvc.perform(api.authGet("/api/v1/admin/practice-submissions", studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void rosterTeacherCanReviewAnUnlinkedSubmissionThroughTheFeedbackEndpoint() throws Exception {
        String teacherToken = api.approvedTeacher(LearningApiTestClient.uniqueEmail("w4-roster-teacher"));
        Long lessonId = api.publishedLesson(teacherToken, "Greetings", "你好");
        Long quizId = api.publishedQuiz(teacherToken, lessonId, null, null);
        String studentToken = api.registerStudent(LearningApiTestClient.uniqueEmail("w4-roster-student"));
        api.submitQuiz(studentToken, quizId, true, "hǎo");

        MvcResult upload = mockMvc.perform(multipart("/api/v1/ai/writing-feedback")
                        .file(new MockMultipartFile("file", "character.png", "image/png", TestUploadFixtures.minimalPng()))
                        .param("targetCharacter", "好")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, LearningApiTestClient.bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        long submissionId = api.data(upload).path("submissionId").asLong();
        long studentId = api.data(mockMvc.perform(api.authGet("/api/v1/teachers/students", teacherToken)).andReturn())
                .path("items").get(0).path("studentId").asLong();

        mockMvc.perform(api.authGet("/api/v1/teachers/practice-submissions", teacherToken))
                .andExpect(jsonPath("$.data.items[?(@.id == " + submissionId + ")].submissionType").value("HANDWRITING_IMAGE"));
        mockMvc.perform(api.jsonPost("/api/v1/teachers/feedback", teacherToken, Map.of(
                        "studentId", studentId,
                        "practiceSubmissionId", submissionId,
                        "feedbackType", "ENCOURAGING",
                        "message", "Neat strokes - keep the radicals balanced.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.practiceSubmissionId").value(submissionId));
        assertThat(practiceSubmissionRepository.findById(submissionId).orElseThrow().getReviewStatus().name())
                .isEqualTo("REVIEWED");
    }

    @Test
    void profileUpdateAndPasswordChangeRevokeOtherSessionsAndKeepBcryptHashes() throws Exception {
        String email = LearningApiTestClient.uniqueEmail("w4-account");
        String firstSession = api.registerStudent(email);
        String secondSession = api.login(email, LearningApiTestClient.USER_PASSWORD);
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        // Proposal Table 6.2 "bcrypt": BCrypt format with the configured cost and a matching raw password.
        assertThat(user.getPasswordHash()).matches(BCRYPT_PATTERN).startsWith("$2a$12$")
                .isNotEqualTo(LearningApiTestClient.USER_PASSWORD);
        assertThat(passwordEncoder.matches(LearningApiTestClient.USER_PASSWORD, user.getPasswordHash())).isTrue();

        mockMvc.perform(api.jsonPut("/api/v1/users/me/profile", firstSession, Map.of(
                        "displayName", "  Mei Ling  ",
                        "preferredLanguage", "zh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Mei Ling"))
                .andExpect(jsonPath("$.data.preferredLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.data.studentProfile.displayName").value("Mei Ling"));
        mockMvc.perform(api.jsonPut("/api/v1/users/me/profile", firstSession, Map.of(
                        "displayName", "Mei Ling",
                        "preferredLanguage", "fr")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(api.jsonPost("/api/v1/users/me/password", firstSession, Map.of(
                        "currentPassword", "WrongPassword1!",
                        "newPassword", NEW_PASSWORD,
                        "confirmPassword", NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect."));
        mockMvc.perform(api.jsonPost("/api/v1/users/me/password", firstSession, Map.of(
                        "currentPassword", LearningApiTestClient.USER_PASSWORD,
                        "newPassword", "short",
                        "confirmPassword", "short")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.jsonPost("/api/v1/users/me/password", firstSession, Map.of(
                        "currentPassword", LearningApiTestClient.USER_PASSWORD,
                        "newPassword", NEW_PASSWORD,
                        "confirmPassword", "Different789!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Confirm password does not match."));

        MvcResult changed = mockMvc.perform(api.jsonPost("/api/v1/users/me/password", firstSession, Map.of(
                        "currentPassword", LearningApiTestClient.USER_PASSWORD,
                        "newPassword", NEW_PASSWORD,
                        "confirmPassword", NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully. Other sessions have been signed out."))
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();
        String freshToken = api.data(changed).path("token").asText();

        // Session revocation: both older tokens stop working, the fresh one works.
        mockMvc.perform(api.authGet("/api/v1/auth/me", firstSession)).andExpect(status().isUnauthorized());
        mockMvc.perform(api.authGet("/api/v1/auth/me", secondSession)).andExpect(status().isUnauthorized());
        mockMvc.perform(api.authGet("/api/v1/auth/me", freshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Mei Ling"));

        mockMvc.perform(api.jsonPost("/api/v1/auth/login", null, Map.of("email", email, "password", LearningApiTestClient.USER_PASSWORD)))
                .andExpect(status().isUnauthorized());
        assertThat(api.login(email, NEW_PASSWORD)).isNotBlank();
        User updated = userRepository.findByEmailWithRoles(email).orElseThrow();
        assertThat(updated.getPasswordHash()).matches(BCRYPT_PATTERN);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, updated.getPasswordHash())).isTrue();
        assertThat(auditLogRepository.findAll())
                .anyMatch(log -> "PASSWORD_CHANGED".equals(log.getActionType()) && updated.getId().equals(log.getTargetId()));

        mockMvc.perform(api.jsonPut("/api/v1/users/me/profile", null, Map.of())).andExpect(status().isUnauthorized());
    }

    @Test
    void teachersAndAdminsCanAlsoManageTheirAccount() throws Exception {
        String teacherToken = api.registerTeacher(LearningApiTestClient.uniqueEmail("w4-account-teacher"));
        mockMvc.perform(api.jsonPut("/api/v1/users/me/profile", teacherToken, Map.of(
                        "displayName", "Cikgu Aisyah",
                        "preferredLanguage", "ms")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preferredLanguage").value("ms"));
        mockMvc.perform(api.jsonPut("/api/v1/users/me/profile", api.adminToken(), Map.of(
                        "displayName", "Platform Admin",
                        "preferredLanguage", "en")))
                .andExpect(status().isOk());
    }

    @Test
    void expiredBearerTokenIsRejectedWithUnauthorized() throws Exception {
        String email = LearningApiTestClient.uniqueEmail("w4-expired");
        api.registerStudent(email);
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date past = new Date(System.currentTimeMillis() - 120_000L);
        String expired = Jwts.builder()
                .subject(email)
                .claims(Map.of("userId", user.getId(), "sessionVersion", user.getSessionVersion()))
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(past.getTime() - 60_000L))
                .expiration(past)
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, LearningApiTestClient.bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void resetPasswordIsRateLimitedPerClientIpLikeForgotPassword() throws Exception {
        String remoteIp = "203.0.113." + (10 + (int) (Math.random() * 200));
        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(resetRequest(remoteIp)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(resetRequest(remoteIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void notificationsCanBeFilteredByReadState() throws Exception {
        String teacherToken = api.approvedTeacher(LearningApiTestClient.uniqueEmail("w4-notify-teacher"));
        mockMvc.perform(api.authGet("/api/v1/notifications?status=UNREAD", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].status").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("UNREAD"))));
        JsonNode unread = api.data(mockMvc.perform(api.authGet("/api/v1/notifications?status=UNREAD", teacherToken)).andReturn());
        assertThat(unread.path("totalItems").asLong()).isPositive();
        mockMvc.perform(api.authPatch("/api/v1/notifications/read-all", teacherToken)).andExpect(status().isOk());
        mockMvc.perform(api.authGet("/api/v1/notifications?status=UNREAD", teacherToken))
                .andExpect(jsonPath("$.data.totalItems").value(0));
        mockMvc.perform(api.authGet("/api/v1/notifications?status=READ", teacherToken))
                .andExpect(jsonPath("$.data.totalItems").value(unread.path("totalItems").asLong()));
        mockMvc.perform(api.authGet("/api/v1/notifications?status=SOMETIMES", teacherToken))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder resetRequest(String remoteIp)
            throws Exception {
        return post("/api/v1/auth/reset-password")
                .with(csrf())
                .with(request -> {
                    request.setRemoteAddr(remoteIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "token", "not-a-real-token-" + UUID.randomUUID(),
                        "password", NEW_PASSWORD,
                        "confirmPassword", NEW_PASSWORD)));
    }
}
