package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.USER_PASSWORD;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.TeacherVerificationDocumentRepository;
import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.TestUploadFixtures;

/**
 * The teacher's side of verification: the teaching profile, the document upload pipeline (type, extension,
 * content-type and magic bytes), submitting with the teacher-expectations consent, and how the application moves
 * between Not submitted, In review, Changes requested and Approved.
 */
class TeacherVerificationIntegrationTests extends ApiIntegrationTest {

    private static final String APPLICATION_PDF = "application/pdf";
    private static final String PROFILE = "/api/v1/teachers/me";
    private static final String PROFILE_UPDATE = "/api/v1/teachers/me/profile";
    private static final String DOCUMENTS = "/api/v1/teachers/verification-documents";
    private static final String SUBMIT = "/api/v1/teachers/verification/submit";
    private static final String BIO = "Mandarin teacher for adult beginners in Malaysia.";
    private static final String BIO_LENGTH_MESSAGE = "Teacher bio must be between 20 and 1000 characters";
    private static final String REASON = "The teaching certificate scan is unreadable. Please upload a clearer copy.";
    private static final Map<String, Object> CONSENT = Map.of("acceptedStandards", true);

    @Autowired
    private TeacherVerificationDocumentRepository documentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void newTeacherStartsNotSubmittedWithTheSignUpProfile() throws Exception {
        String email = uniqueEmail("verify-new");
        String token = api.registerTeacher(email);

        mockMvc.perform(api.authGet(PROFILE, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Teacher " + email))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.bio").value("Mandarin teacher for young adult learners"))
                .andExpect(jsonPath("$.data.verificationState").value("NOT_SUBMITTED"))
                .andExpect(jsonPath("$.data.documents").isEmpty())
                .andExpect(jsonPath("$.data.publishedClasses").isEmpty())
                .andExpect(jsonPath("$.data.latestDecision").doesNotExist());
        mockMvc.perform(api.authGet("/api/v1/auth/me", token))
                .andExpect(jsonPath("$.data.teacherProfile.teacherStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.teacherProfile.verificationState").value("NOT_SUBMITTED"));
    }

    @Test
    void teacherUpdatesNameAndBioWithTheSignUpBioRule() throws Exception {
        String token = api.registerTeacher(uniqueEmail("verify-profile"));

        mockMvc.perform(api.jsonPut(PROFILE_UPDATE, token, Map.of("fullName", "  Mei Lin  ", "bio", BIO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Mei Lin"))
                .andExpect(jsonPath("$.data.bio").value(BIO));
        mockMvc.perform(api.jsonPut(PROFILE_UPDATE, token, Map.of("fullName", "Mei Lin", "bio", "Too short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.bio[0]").value(BIO_LENGTH_MESSAGE));
    }

    @Test
    void uploadAcceptsTheThreeDocumentTypesAndReplacesTheCurrentOne() throws Exception {
        String email = uniqueEmail("verify-upload");
        String token = api.registerTeacher(email);

        mockMvc.perform(upload(token, pdf("certificate.pdf"), "CERTIFICATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documents.length()").value(1))
                .andExpect(jsonPath("$.data.documents[0].documentType").value("CERTIFICATE"))
                .andExpect(jsonPath("$.data.documents[0].status").value("READY"));
        mockMvc.perform(upload(token, pdf("certificate-v2.pdf"), "CERTIFICATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documents.length()").value(1))
                .andExpect(jsonPath("$.data.documents[0].fileName").value("certificate-v2.pdf"));
        mockMvc.perform(upload(token, pdf("degree.pdf"), "QUALIFICATION"))
                .andExpect(jsonPath("$.data.documents.length()").value(2));

        TeacherProfile profile = teacherProfile(email);
        assertThat(documentRepository.findByTeacherId(profile.getId())).hasSize(3);
        assertThat(documentRepository.findByTeacherIdAndCurrentVersionTrueOrderBySubmittedAtDesc(profile.getId()))
                .hasSize(2);

        mockMvc.perform(upload(token, pdf("other.pdf"), "OTHER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Upload a teaching certificate, an identity document or a degree certificate."));
    }

    @Test
    void uploadRejectsWrongFileTypesSpoofedContentAndOversizedFiles() throws Exception {
        String token = api.registerTeacher(uniqueEmail("verify-bad-upload"));

        mockMvc.perform(upload(token,
                        new MockMultipartFile("file", "script.exe", "application/octet-stream", "bad".getBytes()),
                        "CERTIFICATE"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(upload(token,
                        new MockMultipartFile("file", "certificate.pdf", APPLICATION_PDF, "not-a-pdf".getBytes()),
                        "CERTIFICATE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Uploaded file content does not match the declared PDF/JPG/PNG type."));
        mockMvc.perform(api.authMultipart(DOCUMENTS, token)
                        .file(new MockMultipartFile("file", "large.pdf", APPLICATION_PDF, new byte[6 * 1024 * 1024]))
                        .param("documentType", "CERTIFICATE"))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void submittingNeedsBothRequiredDocumentsAndTheConsent() throws Exception {
        String token = api.registerTeacher(uniqueEmail("verify-submit-rules"));
        api.uploadVerificationDocument(token, "CERTIFICATE");

        mockMvc.perform(api.jsonPost(SUBMIT, token, CONSENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Upload your teaching certificate and identity document before you submit."));
        api.uploadVerificationDocument(token, "IDENTITY");
        mockMvc.perform(api.jsonPost(SUBMIT, token, Map.of("acceptedStandards", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.acceptedStandards[0]").value(
                        "Agree to follow what MandarinSpark expects from teachers before you submit."));
    }

    @Test
    void submittedApplicationIsInReviewReadOnlyAndAdminsAreTold() throws Exception {
        String email = uniqueEmail("verify-submit");
        String token = api.registerTeacher(email);
        api.uploadVerificationDocument(token, "CERTIFICATE");
        api.uploadVerificationDocument(token, "IDENTITY");

        mockMvc.perform(api.jsonPost(SUBMIT, token, CONSENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationState").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.submittedAt").exists())
                .andExpect(jsonPath("$.data.documents[0].status").value("IN_REVIEW"));

        TeacherProfile profile = teacherProfile(email);
        assertThat(profile.getStandardsAcceptedAt()).isNotNull();
        assertThat(profile.getVerificationSubmittedAt()).isNotNull();
        assertThat(notificationRepository.findAll()).anyMatch(notification ->
                notification.getNotificationType() == NotificationType.TEACHER_APPROVAL
                        && notification.getMessage().startsWith(profile.getUser().getDisplayName()));
        assertThat(auditLogRepository.findAll()).anyMatch(entry ->
                "TEACHER_APPLICATION_SUBMITTED".equals(entry.getActionType())
                        && profile.getId().equals(entry.getTargetId()));

        mockMvc.perform(upload(token, pdf("late.pdf"), "QUALIFICATION"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Documents can't be changed while they are in review."));
        mockMvc.perform(api.jsonPost(SUBMIT, token, CONSENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your application is already in review."));
    }

    @Test
    void changesRequestedTeacherSeesTheReasonReplacesAFileAndResubmits() throws Exception {
        String email = uniqueEmail("verify-changes");
        TeacherProfile profile = api.registerTeacherWithDocument(email);
        String token = api.login(email, USER_PASSWORD);

        mockMvc.perform(api.jsonPost("/api/v1/admin/teachers/" + profile.getId() + "/reject", api.adminToken(),
                        Map.of("reason", REASON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationState").value("CHANGES_REQUESTED"));

        mockMvc.perform(api.authGet(PROFILE, token))
                .andExpect(jsonPath("$.data.verificationState").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.data.latestDecision.outcome").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.data.latestDecision.reason").value(REASON))
                .andExpect(jsonPath("$.data.latestDecision.reviewerName").exists())
                .andExpect(jsonPath("$.data.documents[0].status").value("SENT"));

        mockMvc.perform(upload(token, pdf("certificate-v2.pdf"), "CERTIFICATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documents[?(@.documentType == 'CERTIFICATE')].status").value("READY"))
                .andExpect(jsonPath("$.data.documents[?(@.documentType == 'IDENTITY')].status").value("SENT"));
        mockMvc.perform(api.jsonPost(SUBMIT, token, CONSENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationState").value("IN_REVIEW"));
        assertThat(teacherProfileRepository.findById(profile.getId()).orElseThrow().getRejectionReason()).isNull();
    }

    @Test
    void approvedTeacherIsVerifiedWithLockedDocuments() throws Exception {
        String email = uniqueEmail("verify-approved");
        String token = api.approvedTeacher(email);

        mockMvc.perform(api.authGet(PROFILE, token))
                .andExpect(jsonPath("$.data.verificationState").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedAt").exists())
                .andExpect(jsonPath("$.data.documents[0].status").value("VERIFIED"));
        mockMvc.perform(api.authGet("/api/v1/auth/me", token))
                .andExpect(jsonPath("$.data.teacherProfile.verificationState").value("APPROVED"))
                .andExpect(jsonPath("$.data.teacherProfile.approvedAt").exists());
        mockMvc.perform(upload(token, pdf("new.pdf"), "CERTIFICATE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your documents are already verified."));
        assertThat(teacherProfile(email).getTeacherStatus()).isEqualTo(TeacherStatus.APPROVED);
    }

    @Test
    void teacherCannotOpenAnotherTeachersDocumentAndOwnDownloadIsAnAttachment() throws Exception {
        String email = uniqueEmail("verify-owner");
        TeacherProfile profile = api.registerTeacherWithDocument(email);
        Long documentId = documentRepository.findTopByTeacherIdOrderBySubmittedAtDesc(profile.getId()).orElseThrow().getId();

        mockMvc.perform(api.authGet(DOCUMENTS + "/" + documentId, api.registerTeacher(uniqueEmail("verify-other"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(api.authGet(DOCUMENTS + "/" + documentId, api.login(email, USER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(header().stringValues("Cache-Control", "no-store"));
    }

    @Test
    void studentsCannotUseTheVerificationEndpoints() throws Exception {
        String studentToken = api.registerStudent(uniqueEmail("verify-student"));

        mockMvc.perform(api.authGet(PROFILE, studentToken)).andExpect(status().isForbidden());
        mockMvc.perform(api.jsonPost(SUBMIT, studentToken, CONSENT)).andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder upload(String token, MockMultipartFile file, String documentType) {
        return api.authMultipart(DOCUMENTS, token).file(file).param("documentType", documentType);
    }

    private static MockMultipartFile pdf(String fileName) {
        return new MockMultipartFile("file", fileName, APPLICATION_PDF, TestUploadFixtures.minimalPdf());
    }
}
