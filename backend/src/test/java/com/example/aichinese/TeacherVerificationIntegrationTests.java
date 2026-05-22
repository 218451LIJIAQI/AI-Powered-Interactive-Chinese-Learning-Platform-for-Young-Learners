package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.example.aichinese.Support.TestUploadFixtures;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.TeacherProfile;
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
class TeacherVerificationIntegrationTests {

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
    void teacherShouldGetOwnProfile() throws Exception {
        String email = uniqueEmail("teacher-profile");
        String token = registerAndLoginTeacher(email);

        mockMvc.perform(get("/api/v1/teachers/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.teacherStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void teacherShouldUpdateOwnProfile() throws Exception {
        String email = uniqueEmail("teacher-update");
        String token = registerAndLoginTeacher(email);

        mockMvc.perform(put("/api/v1/teachers/me/profile")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Updated Teacher",
                        "bio", "Experienced Malaysian Mandarin teacher",
                        "shortIntroduction", "Primary school learning specialist",
                        "teachingCategories", new String[] { "Vocabulary", "Greetings" }))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Updated Teacher"))
                .andExpect(jsonPath("$.data.bio").value("Experienced Malaysian Mandarin teacher"))
                .andExpect(jsonPath("$.data.shortIntroduction").value("Primary school learning specialist"))
                .andExpect(jsonPath("$.data.teachingCategories[0]").value("Vocabulary"));

        TeacherProfile profile = teacherProfileRepository.findDetailedByUserId(findUserIdByEmail(email)).orElseThrow();
        assertThat(profile.getBio()).isEqualTo("Experienced Malaysian Mandarin teacher");
        assertThat(profile.getQualificationSummary()).isEqualTo("Primary school learning specialist");
    }

    @Test
    void teacherShouldUploadVerificationDocument() throws Exception {
        String email = uniqueEmail("teacher-upload");
        String token = registerAndLoginTeacher(email);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "certificate.pdf",
                "application/pdf",
                TestUploadFixtures.minimalPdf());

        mockMvc.perform(multipart("/api/v1/teachers/verification-documents")
                .file(file)
                .param("documentType", "CERTIFICATE")
                .param("description", "Bachelor teaching certificate")
                .with(csrf())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.latestSubmittedDocuments[0].documentType").value("CERTIFICATE"))
                .andExpect(jsonPath("$.data.latestSubmittedDocuments[0].originalFileName").value("certificate.pdf"));

        TeacherProfile profile = teacherProfileRepository.findDetailedByUserId(findUserIdByEmail(email)).orElseThrow();
        assertThat(teacherVerificationDocumentRepository.countByTeacherId(profile.getId())).isEqualTo(1);
        assertThat(auditLogRepository.findAll())
                .extracting(AuditLog::getActionType)
                .contains("TEACHER_VERIFICATION_DOCUMENT_UPLOAD");
        assertThat(notificationRepository.findAll())
                .extracting(Notification::getTitle)
                .contains("Teacher verification document uploaded");
    }

    @Test
    void teacherUploadShouldRejectInvalidFileType() throws Exception {
        String email = uniqueEmail("teacher-invalid-upload");
        String token = registerAndLoginTeacher(email);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.exe",
                "application/octet-stream",
                "bad-file".getBytes());

        mockMvc.perform(multipart("/api/v1/teachers/verification-documents")
                .file(file)
                .param("documentType", "OTHER")
                .with(csrf())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void teacherUploadShouldRejectSpoofedPdfContent() throws Exception {
        String email = uniqueEmail("teacher-spoofed-upload");
        String token = registerAndLoginTeacher(email);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "certificate.pdf",
                "application/pdf",
                "not-a-pdf".getBytes());

        mockMvc.perform(multipart("/api/v1/teachers/verification-documents")
                .file(file)
                .param("documentType", "CERTIFICATE")
                .with(csrf())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Uploaded file content does not match the declared PDF/JPG/PNG type."));
    }

    @Test
    void teacherUploadShouldRejectOversizedFile() throws Exception {
        String email = uniqueEmail("teacher-large-upload");
        String token = registerAndLoginTeacher(email);

        byte[] payload = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                payload);

        mockMvc.perform(multipart("/api/v1/teachers/verification-documents")
                .file(file)
                .with(csrf())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("The file is too large. Please choose a smaller file and try again."));
    }

    @Test
    void rejectedTeacherShouldSeeVerificationStatusAndResubmit() throws Exception {
        String email = uniqueEmail("teacher-resubmit");
        String teacherToken = registerAndLoginTeacher(email);
        TeacherProfile teacherProfile = uploadDocumentForTeacher(email, teacherToken);
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");

        mockMvc.perform(post("/api/v1/admin/teachers/" + teacherProfile.getId() + "/reject")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Please provide a clearer qualification document."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherStatus").value("REJECTED"));

        mockMvc.perform(get("/api/v1/teachers/verification-status")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Please provide a clearer qualification document."))
                .andExpect(jsonPath("$.data.resubmissionAllowed").value(true));

        mockMvc.perform(post("/api/v1/teachers/resubmit-verification")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.rejectionReason").doesNotExist());

        TeacherProfile refreshed = teacherProfileRepository.findById(teacherProfile.getId()).orElseThrow();
        assertThat(refreshed.getTeacherStatus()).isEqualTo(TeacherStatus.PENDING);
        assertThat(refreshed.getRejectionReason()).isNull();
    }

    @Test
    void teacherShouldNotAccessAnotherTeachersDocument() throws Exception {
        String firstEmail = uniqueEmail("teacher-a");
        String firstToken = registerAndLoginTeacher(firstEmail);
        TeacherProfile firstProfile = uploadDocumentForTeacher(firstEmail, firstToken);
        Long documentId = teacherVerificationDocumentRepository.findTopByTeacherIdOrderBySubmittedAtDesc(firstProfile.getId())
                .orElseThrow()
                .getId();

        String secondToken = registerAndLoginTeacher(uniqueEmail("teacher-b"));

        mockMvc.perform(get("/api/v1/teachers/verification-documents/" + documentId)
                .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void teacherVerificationDocumentDownloadUsesAttachmentDisposition() throws Exception {
        String email = uniqueEmail("teacher-attachment");
        String token = registerAndLoginTeacher(email);
        TeacherProfile profile = uploadDocumentForTeacher(email, token);
        Long documentId = teacherVerificationDocumentRepository.findTopByTeacherIdOrderBySubmittedAtDesc(profile.getId())
                .orElseThrow()
                .getId();

        mockMvc.perform(get("/api/v1/teachers/verification-documents/" + documentId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", startsWith("attachment;")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"));
    }

    private TeacherProfile uploadDocumentForTeacher(String email, String teacherToken) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "verification.pdf",
                "application/pdf",
                TestUploadFixtures.minimalPdf());

        mockMvc.perform(multipart("/api/v1/teachers/verification-documents")
                .file(file)
                .param("documentType", "QUALIFICATION")
                .param("description", "Phase 5 qualification proof")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        return teacherProfileRepository.findDetailedByUserId(findUserIdByEmail(email)).orElseThrow();
    }

    private String registerAndLoginTeacher(String email) throws Exception {
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

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("token").asText();
    }

    private Long findUserIdByEmail(String email) {
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return user.getId();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
