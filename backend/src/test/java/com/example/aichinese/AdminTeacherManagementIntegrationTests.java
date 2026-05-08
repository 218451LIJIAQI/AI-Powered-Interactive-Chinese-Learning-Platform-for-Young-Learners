package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.TeacherVerificationReview;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.TeacherVerificationDocumentRepository;
import com.example.aichinese.repository.TeacherVerificationReviewRepository;
import com.example.aichinese.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminTeacherManagementIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Autowired
    private TeacherVerificationDocumentRepository teacherVerificationDocumentRepository;

    @Autowired
    private TeacherVerificationReviewRepository teacherVerificationReviewRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void adminShouldListPendingTeachers() throws Exception {
        String teacherEmail = uniqueEmail("pending-teacher");
        registerTeacherWithDocument(teacherEmail);
        String adminToken = adminToken();

        mockMvc.perform(get("/api/v1/admin/teachers/pending")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].teacherStatus").value("PENDING"));
    }

    @Test
    void adminShouldApproveTeacher() throws Exception {
        String teacherEmail = uniqueEmail("approve-teacher");
        TeacherProfile profile = registerTeacherWithDocument(teacherEmail);
        String adminToken = adminToken();

        mockMvc.perform(post("/api/v1/admin/teachers/" + profile.getId() + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Documents verified."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherStatus").value("APPROVED"));

        TeacherProfile refreshed = teacherProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(refreshed.getTeacherStatus()).isEqualTo(TeacherStatus.APPROVED);
        assertThat(refreshed.isPublicVisible()).isTrue();
        assertThat(teacherVerificationReviewRepository.findTopByTeacherIdOrderByReviewedAtDesc(profile.getId()))
                .map(TeacherVerificationReview::getDecisionStatus)
                .contains(TeacherStatus.APPROVED);
        assertThat(notificationRepository.findAll())
                .extracting(Notification::getTitle)
                .contains("Teacher account approved");
        assertThat(auditLogRepository.findAll())
                .extracting(AuditLog::getActionType)
                .contains("ADMIN_TEACHER_APPROVE");
    }

    @Test
    void adminShouldRejectTeacherWithReason() throws Exception {
        String teacherEmail = uniqueEmail("reject-teacher");
        TeacherProfile profile = registerTeacherWithDocument(teacherEmail);
        String adminToken = adminToken();

        mockMvc.perform(post("/api/v1/admin/teachers/" + profile.getId() + "/reject")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Document is too blurry."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Document is too blurry."));

        TeacherProfile refreshed = teacherProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(refreshed.getTeacherStatus()).isEqualTo(TeacherStatus.REJECTED);
        assertThat(refreshed.getRejectionReason()).isEqualTo("Document is too blurry.");
        assertThat(refreshed.isPublicVisible()).isFalse();
    }

    @Test
    void rejectWithoutReasonShouldFailValidation() throws Exception {
        String teacherEmail = uniqueEmail("reject-no-reason");
        TeacherProfile profile = registerTeacherWithDocument(teacherEmail);

        mockMvc.perform(post("/api/v1/admin/teachers/" + profile.getId() + "/reject")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void nonAdminShouldNotApproveTeacher() throws Exception {
        String teacherEmail = uniqueEmail("teacher-forbidden");
        TeacherProfile profile = registerTeacherWithDocument(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");

        mockMvc.perform(post("/api/v1/admin/teachers/" + profile.getId() + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Nope"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void studentShouldNotAccessAdminTeacherApis() throws Exception {
        String studentEmail = uniqueEmail("student-blocked");
        String studentToken = registerStudentAndLogin(studentEmail);

        mockMvc.perform(get("/api/v1/admin/teachers/pending")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void guestShouldNotAccessProtectedTeacherAdminApis() throws Exception {
        mockMvc.perform(get("/api/v1/teachers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void publicTeacherProfileShouldOnlyReturnApprovedTeacher() throws Exception {
        String approvedEmail = uniqueEmail("public-approved");
        TeacherProfile approvedProfile = registerTeacherWithDocument(approvedEmail);
        mockMvc.perform(post("/api/v1/admin/teachers/" + approvedProfile.getId() + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Approved for public profile"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/teachers/" + approvedProfile.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherId").value(approvedProfile.getId()))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.email").doesNotExist());

        String pendingEmail = uniqueEmail("public-pending");
        TeacherProfile pendingProfile = registerTeacherWithDocument(pendingEmail);

        mockMvc.perform(get("/api/v1/public/teachers/" + pendingProfile.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void adminUserAndStudentListsShouldHidePasswordHash() throws Exception {
        registerTeacherWithDocument(uniqueEmail("list-teacher"));
        String studentEmail = uniqueEmail("list-student");
        registerStudentAndLogin(studentEmail);
        String adminToken = adminToken();

        MvcResult usersResult = mockMvc.perform(get("/api/v1/admin/users")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult studentsResult = mockMvc.perform(get("/api/v1/admin/students")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(usersResult.getResponse().getContentAsString()).doesNotContain("passwordHash");
        assertThat(usersResult.getResponse().getContentAsString()).doesNotContain("password_hash");
        assertThat(studentsResult.getResponse().getContentAsString()).doesNotContain("passwordHash");
    }

    private TeacherProfile registerTeacherWithDocument(String email) throws Exception {
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

        String teacherToken = loginAndExtractToken(email, "Password123!");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "qualification.pdf",
                "application/pdf",
                "teacher-qualification".getBytes());

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

    private String adminToken() throws Exception {
        return loginAndExtractToken("admin@example.com", "SuperSecure123!");
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

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("token").asText();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
