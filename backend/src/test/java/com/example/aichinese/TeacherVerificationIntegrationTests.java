package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.TeacherVerificationDocumentRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.support.TestUploadFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end MockMvc coverage of the teacher self-service profile +
 * verification document upload surface. Pins the document upload
 * pipeline (extension + content-type + magic-byte verification).
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeacherVerificationIntegrationTests {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "SuperSecure123!";
    private static final String USER_PASSWORD = "Password123!";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final String EMAIL_DOMAIN = "@example.com";
    private static final String APPLICATION_PDF = "application/pdf";
    private static final String TEACHER_BIO = "Teacher bio";
    private static final String UPDATED_FULL_NAME = "Updated Teacher";
    private static final String UPDATED_BIO = "Experienced Malaysian Mandarin teacher";
    private static final String UPDATED_SHORT_INTRODUCTION = "University Mandarin tutor";
    private static final String TEACHING_CATEGORY_VOCABULARY = "Vocabulary";
    private static final String TEACHING_CATEGORY_GREETINGS = "Greetings";
    private static final String UPLOAD_AUDIT_ACTION = "TEACHER_VERIFICATION_DOCUMENT_UPLOAD";
    private static final String UPLOAD_NOTIFICATION_TITLE = "Teacher verification document uploaded";
    private static final String TEACHER_ME_ENDPOINT = "/api/v1/teachers/me";
    private static final String TEACHER_PROFILE_ENDPOINT = "/api/v1/teachers/me/profile";
    private static final String TEACHER_DOCUMENTS_ENDPOINT = "/api/v1/teachers/verification-documents";
    private static final String TEACHER_STATUS_ENDPOINT = "/api/v1/teachers/verification-status";
    private static final String TEACHER_RESUBMIT_ENDPOINT = "/api/v1/teachers/resubmit-verification";
    private static final String TEACHER_REGISTER_ENDPOINT = "/api/v1/auth/register/teacher";
    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";
    private static final String ADMIN_TEACHERS_ENDPOINT = "/api/v1/admin/teachers";
    private static final String PENDING = "PENDING";
    private static final String REJECTED = "REJECTED";
    private static final String CERTIFICATE = "CERTIFICATE";
    private static final String QUALIFICATION = "QUALIFICATION";
    private static final String OTHER = "OTHER";
    private static final String CERTIFICATE_FILE_NAME = "certificate.pdf";
    private static final String VERIFICATION_FILE_NAME = "verification.pdf";
    private static final String INVALID_FILE_NAME = "script.exe";
    private static final String LARGE_FILE_NAME = "large.pdf";
    private static final String OCTET_STREAM = "application/octet-stream";
    private static final String DOCUMENT_TYPE_PARAM = "documentType";
    private static final String DESCRIPTION_PARAM = "description";
    private static final String CONTENT_DISPOSITION = "Content-Disposition";
    private static final String CACHE_CONTROL = "Cache-Control";
    private static final String NO_STORE = "no-store";
    private static final String SPOOFED_CONTENT_MESSAGE =
            "Uploaded file content does not match the declared PDF/JPG/PNG type.";
    private static final String TOO_LARGE_MESSAGE =
            "The file is too large. Please choose a smaller file and try again.";
    private static final String REJECTION_REASON = "Please provide a clearer qualification document.";
    /** Larger than both the test-profile (1 KB) and production (5 MB) verification-document limits. */
    private static final int OVERSIZED_UPLOAD_BYTES = 6 * 1024 * 1024;
    private static final int MAX_CATEGORY_LENGTH = 60;
    private static final String TEACHING_CATEGORIES_TOO_LONG_MESSAGE =
            "Teaching categories must not exceed 500 characters in total.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Autowired
    private TeacherVerificationDocumentRepository teacherVerificationDocumentRepository;

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

        mockMvc.perform(authenticatedGet(TEACHER_ME_ENDPOINT, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.teacherStatus").value(PENDING))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void teacherShouldUpdateOwnProfile() throws Exception {
        String email = uniqueEmail("teacher-update");
        String token = registerAndLoginTeacher(email);

        mockMvc.perform(authenticatedJsonPut(TEACHER_PROFILE_ENDPOINT, token, Map.of(
                        "fullName", UPDATED_FULL_NAME,
                        "bio", UPDATED_BIO,
                        "shortIntroduction", UPDATED_SHORT_INTRODUCTION,
                        "teachingCategories", new String[] {
                                TEACHING_CATEGORY_VOCABULARY,
                                TEACHING_CATEGORY_GREETINGS
                        })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value(UPDATED_FULL_NAME))
                .andExpect(jsonPath("$.data.bio").value(UPDATED_BIO))
                .andExpect(jsonPath("$.data.shortIntroduction").value(UPDATED_SHORT_INTRODUCTION))
                .andExpect(jsonPath("$.data.teachingCategories[0]").value(TEACHING_CATEGORY_VOCABULARY));

        TeacherProfile profile = teacherProfileRepository.findDetailedByUserId(findUserIdByEmail(email)).orElseThrow();
        assertThat(profile.getBio()).isEqualTo(UPDATED_BIO);
        assertThat(profile.getQualificationSummary()).isEqualTo(UPDATED_SHORT_INTRODUCTION);
    }

    @Test
    void teacherProfileUpdateRejectsCategoriesLongerThanTheStoredColumn() throws Exception {
        String email = uniqueEmail("teacher-long-categories");
        String token = registerAndLoginTeacher(email);

        // 12 x 60 characters joined with ", " is 742 characters; the column holds 500.
        mockMvc.perform(authenticatedJsonPut(TEACHER_PROFILE_ENDPOINT, token, Map.of(
                        "fullName", UPDATED_FULL_NAME,
                        "teachingCategories", categories(12))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(TEACHING_CATEGORIES_TOO_LONG_MESSAGE));

        // 8 x 60 characters joined with ", " is 494 characters and still fits.
        mockMvc.perform(authenticatedJsonPut(TEACHER_PROFILE_ENDPOINT, token, Map.of(
                        "fullName", UPDATED_FULL_NAME,
                        "teachingCategories", categories(8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teachingCategories.length()").value(8));
    }

    @Test
    void teacherShouldUploadVerificationDocument() throws Exception {
        String email = uniqueEmail("teacher-upload");
        String token = registerAndLoginTeacher(email);

        MockMultipartFile file = pdfFile(CERTIFICATE_FILE_NAME);

        mockMvc.perform(multipart(TEACHER_DOCUMENTS_ENDPOINT)
                .file(file)
                .param(DOCUMENT_TYPE_PARAM, CERTIFICATE)
                .param(DESCRIPTION_PARAM, "Bachelor teaching certificate")
                .with(csrf())
                .header(AUTHORIZATION, BEARER + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.latestSubmittedDocuments[0].documentType").value(CERTIFICATE))
                .andExpect(jsonPath("$.data.latestSubmittedDocuments[0].originalFileName").value(CERTIFICATE_FILE_NAME));

        TeacherProfile profile = teacherProfileRepository.findDetailedByUserId(findUserIdByEmail(email)).orElseThrow();
        assertThat(teacherVerificationDocumentRepository.countByTeacherId(profile.getId())).isEqualTo(1);
        // Scoped to this upload: other tests in the shared context upload verification documents too.
        Long documentId = latestDocumentId(profile);
        assertThat(auditLogRepository.findAll())
                .anyMatch(log -> UPLOAD_AUDIT_ACTION.equals(log.getActionType()) && documentId.equals(log.getTargetId()));
        assertThat(notificationRepository.findAll())
                .anyMatch(notification -> UPLOAD_NOTIFICATION_TITLE.equals(notification.getTitle())
                        && notification.getMessage().contains("Teacher " + email));
    }

    @Test
    void teacherUploadShouldRejectInvalidFileType() throws Exception {
        String email = uniqueEmail("teacher-invalid-upload");
        String token = registerAndLoginTeacher(email);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                INVALID_FILE_NAME,
                OCTET_STREAM,
                "bad-file".getBytes());

        mockMvc.perform(multipart(TEACHER_DOCUMENTS_ENDPOINT)
                .file(file)
                .param(DOCUMENT_TYPE_PARAM, OTHER)
                .with(csrf())
                .header(AUTHORIZATION, BEARER + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void teacherUploadShouldRejectSpoofedPdfContent() throws Exception {
        String email = uniqueEmail("teacher-spoofed-upload");
        String token = registerAndLoginTeacher(email);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                CERTIFICATE_FILE_NAME,
                APPLICATION_PDF,
                "not-a-pdf".getBytes());

        mockMvc.perform(multipart(TEACHER_DOCUMENTS_ENDPOINT)
                .file(file)
                .param(DOCUMENT_TYPE_PARAM, CERTIFICATE)
                .with(csrf())
                .header(AUTHORIZATION, BEARER + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SPOOFED_CONTENT_MESSAGE));
    }

    @Test
    void teacherUploadShouldRejectOversizedFile() throws Exception {
        String email = uniqueEmail("teacher-large-upload");
        String token = registerAndLoginTeacher(email);

        byte[] payload = new byte[OVERSIZED_UPLOAD_BYTES];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                LARGE_FILE_NAME,
                APPLICATION_PDF,
                payload);

        mockMvc.perform(multipart(TEACHER_DOCUMENTS_ENDPOINT)
                .file(file)
                .with(csrf())
                .header(AUTHORIZATION, BEARER + token))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value(TOO_LARGE_MESSAGE));
    }

    @Test
    void rejectedTeacherShouldSeeVerificationStatusAndResubmit() throws Exception {
        String email = uniqueEmail("teacher-resubmit");
        String teacherToken = registerAndLoginTeacher(email);
        TeacherProfile teacherProfile = uploadDocumentForTeacher(email, teacherToken);
        String adminToken = adminToken();

        mockMvc.perform(authenticatedJsonPost(
                adminTeacherRejectPath(teacherProfile.getId()),
                adminToken,
                Map.of("reason", REJECTION_REASON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherStatus").value(REJECTED));

        mockMvc.perform(authenticatedGet(TEACHER_STATUS_ENDPOINT, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherStatus").value(REJECTED))
                .andExpect(jsonPath("$.data.rejectionReason").value(REJECTION_REASON))
                .andExpect(jsonPath("$.data.resubmissionAllowed").value(true));

        mockMvc.perform(authenticatedPost(TEACHER_RESUBMIT_ENDPOINT, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherStatus").value(PENDING))
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
        Long documentId = latestDocumentId(firstProfile);

        String secondToken = registerAndLoginTeacher(uniqueEmail("teacher-b"));

        mockMvc.perform(authenticatedGet(teacherDocumentPath(documentId), secondToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void teacherVerificationDocumentDownloadUsesAttachmentDisposition() throws Exception {
        String email = uniqueEmail("teacher-attachment");
        String token = registerAndLoginTeacher(email);
        TeacherProfile profile = uploadDocumentForTeacher(email, token);
        Long documentId = latestDocumentId(profile);

        mockMvc.perform(authenticatedGet(teacherDocumentPath(documentId), token))
                .andExpect(status().isOk())
                .andExpect(header()
                        .string(CONTENT_DISPOSITION, startsWith("attachment;")))
                .andExpect(header()
                        .string(CACHE_CONTROL, NO_STORE));
    }

    private TeacherProfile uploadDocumentForTeacher(String email, String teacherToken) throws Exception {
        MockMultipartFile file = pdfFile(VERIFICATION_FILE_NAME);

        mockMvc.perform(multipart(TEACHER_DOCUMENTS_ENDPOINT)
                .file(file)
                .param(DOCUMENT_TYPE_PARAM, QUALIFICATION)
                .param(DESCRIPTION_PARAM, "Phase 5 qualification proof")
                .with(csrf())
                .header(AUTHORIZATION, BEARER + teacherToken))
                .andExpect(status().isOk());

        return teacherProfileRepository.findDetailedByUserId(findUserIdByEmail(email)).orElseThrow();
    }

    private String registerAndLoginTeacher(String email) throws Exception {
        mockMvc.perform(jsonPost(TEACHER_REGISTER_ENDPOINT, Map.of(
                        "fullName", "Teacher " + email,
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "bio", TEACHER_BIO)))
                .andExpect(status().isOk());

        return loginAndExtractToken(email, USER_PASSWORD);
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(jsonPost(LOGIN_ENDPOINT, Map.of(
                        "email", email,
                        "password", password)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("token").asText();
    }

    private Long findUserIdByEmail(String email) {
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return user.getId();
    }

    private Long latestDocumentId(TeacherProfile profile) {
        return teacherVerificationDocumentRepository.findTopByTeacherIdOrderBySubmittedAtDesc(profile.getId())
                .orElseThrow()
                .getId();
    }

    private String adminToken() throws Exception {
        return loginAndExtractToken(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    private MockMultipartFile pdfFile(String filename) {
        return new MockMultipartFile("file", filename, APPLICATION_PDF, TestUploadFixtures.minimalPdf());
    }

    private MockHttpServletRequestBuilder authenticatedGet(String path, String token) {
        return get(path).header(AUTHORIZATION, BEARER + token);
    }

    private MockHttpServletRequestBuilder authenticatedPost(String path, String token) {
        return post(path).with(csrf()).header(AUTHORIZATION, BEARER + token);
    }

    private MockHttpServletRequestBuilder authenticatedJsonPost(String path, String token, Object body)
            throws Exception {
        return authenticatedPost(path, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
    }

    private MockHttpServletRequestBuilder authenticatedJsonPut(String path, String token, Object body)
            throws Exception {
        return put(path)
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

    private static String adminTeacherRejectPath(Long teacherId) {
        return ADMIN_TEACHERS_ENDPOINT + "/" + teacherId + "/reject";
    }

    private static String teacherDocumentPath(Long documentId) {
        return TEACHER_DOCUMENTS_ENDPOINT + "/" + documentId;
    }

    private static String[] categories(int count) {
        String[] categories = new String[count];
        for (int index = 0; index < count; index++) {
            categories[index] = String.valueOf((char) ('A' + index)).repeat(MAX_CATEGORY_LENGTH);
        }
        return categories;
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + EMAIL_DOMAIN;
    }
}
