package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.USER_PASSWORD;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static com.example.aichinese.support.LearningApiTestClient.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.AdminInviteRepository;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.LiveClassEnrolmentRepository;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.TeacherVerificationDocumentRepository;
import com.example.aichinese.service.SecureTokens;
import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.LearningApiTestClient;

/**
 * The admin Users section: user search and detail, deactivation with its reason and cascade (sessions, live sessions,
 * review queue), reactivation, admin invites, and the teacher applications tab with its decisions.
 */
class AdminUsersIntegrationTests extends ApiIntegrationTest {

    private static final String USERS = "/api/v1/admin/users";
    private static final String APPLICATIONS = "/api/v1/admin/teacher-applications";
    private static final Map<String, Object> REASON = Map.of("reason", "Asked by email to pause teaching.");

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TeacherVerificationDocumentRepository documentRepository;

    @Autowired
    private LiveClassRepository liveClassRepository;

    @Autowired
    private LiveClassEnrolmentRepository liveClassEnrolmentRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private AdminInviteRepository adminInviteRepository;

    @Test
    void userListSearchesFiltersAndShowsTheVerificationState() throws Exception {
        String marker = "users" + uniqueSuffix();
        for (int index = 0; index < 12; index++) {
            api.registerStudent(marker + "-" + index + "@example.com");
        }
        String teacherEmail = marker + "-teacher@example.com";
        api.registerTeacher(teacherEmail);
        String adminToken = api.adminToken();

        mockMvc.perform(api.authGet(USERS + "?keyword=" + marker + "&size=5", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(13))
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.items[0].email").value(teacherEmail))
                .andExpect(jsonPath("$.data.items[0].role").value("TEACHER"))
                .andExpect(jsonPath("$.data.items[0].verificationState").value("NOT_SUBMITTED"))
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist());
        mockMvc.perform(api.authGet(USERS + "?keyword=" + marker + "&role=STUDENT", adminToken))
                .andExpect(jsonPath("$.data.totalItems").value(12))
                .andExpect(jsonPath("$.data.items[0].verificationState").doesNotExist());
        mockMvc.perform(api.authGet(USERS + "?keyword=" + marker + "&active=false", adminToken))
                .andExpect(jsonPath("$.data.totalItems").value(0));
        mockMvc.perform(api.authGet(USERS + "?role=NOBODY", adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivationSignsOutBlocksSignInAndIsAuditedWithItsReason() throws Exception {
        String email = uniqueEmail("users-deactivate");
        String studentToken = api.registerStudent(email);
        Long userId = user(email).getId();
        String adminToken = api.adminToken();

        mockMvc.perform(api.authGet(USERS + "/" + userId, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.self").value(false))
                .andExpect(jsonPath("$.data.deactivationImpact.upcomingSessions").value(0));
        mockMvc.perform(api.jsonPost(USERS + "/" + userId + "/deactivate", adminToken, Map.of("reason", " ")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.jsonPost(USERS + "/" + userId + "/deactivate", studentToken, REASON))
                .andExpect(status().isForbidden());

        mockMvc.perform(api.jsonPost(USERS + "/" + userId + "/deactivate", adminToken, REASON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.active").value(false))
                .andExpect(jsonPath("$.data.user.deactivation.reason").value(REASON.get("reason")))
                .andExpect(jsonPath("$.data.user.deactivation.actorEmail").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.data.user.recentAudit[0].actionType").value("ADMIN_USER_DEACTIVATE"));
        mockMvc.perform(api.authGet("/api/v1/auth/me", studentToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(api.loginRequest(email, USER_PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "This account is currently unavailable. Please contact the platform administrator."));

        mockMvc.perform(api.jsonPost(USERS + "/" + userId + "/reactivate", adminToken,
                        Map.of("reason", "Spoke with the student.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
        assertThat(api.login(email, USER_PASSWORD)).isNotBlank();
        assertThat(auditLogRepository.findAll()).anyMatch(entry ->
                "ADMIN_USER_REACTIVATE".equals(entry.getActionType())
                        && userId.equals(entry.getTargetId())
                        && "Spoke with the student.".equals(entry.getReason()));
    }

    @Test
    void deactivatingATeacherCancelsUpcomingSessionsAndSendsReviewItemsBackToDraft() throws Exception {
        String teacherEmail = uniqueEmail("users-teacher-cascade");
        String teacherToken = api.approvedTeacher(teacherEmail);
        String studentEmail = uniqueEmail("users-booked-student");
        String studentToken = api.registerStudent(studentEmail);
        LearningApiTestClient.PublishedClass published = api.publishedClass(teacherToken, "Greetings " + uniqueSuffix());
        Long lessonId = published.lessonId();
        Long sessionId = api.scheduledLiveClass(teacherToken, lessonId, "Greetings live", LocalDateTime.now().plusDays(3));
        api.join(studentToken, published.classId());
        mockMvc.perform(api.liveSessionAction(studentToken, sessionId, "book")).andExpect(status().isOk());
        Long draftId = api.draftLesson(teacherToken, published.unitId(), "Numbers " + uniqueSuffix());
        api.readyLesson(teacherToken, draftId, "Count from one to ten");
        mockMvc.perform(api.authPost("/api/v1/teachers/lessons/" + draftId + "/submit", teacherToken))
                .andExpect(status().isOk());
        Long userId = user(teacherEmail).getId();
        String adminToken = api.adminToken();

        mockMvc.perform(api.authGet(USERS + "/" + userId, adminToken))
                .andExpect(jsonPath("$.data.verificationState").value("APPROVED"))
                .andExpect(jsonPath("$.data.deactivationImpact.upcomingSessions").value(1))
                .andExpect(jsonPath("$.data.deactivationImpact.bookings").value(1))
                .andExpect(jsonPath("$.data.deactivationImpact.reviewItems").value(1));

        mockMvc.perform(api.jsonPost(USERS + "/" + userId + "/deactivate", adminToken, REASON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelledSessions").value(1))
                .andExpect(jsonPath("$.data.endedBookings").value(1))
                .andExpect(jsonPath("$.data.itemsBackToDraft").value(1));

        assertThat(liveClassRepository.findById(sessionId).orElseThrow().getLiveClassStatus())
                .isEqualTo(LiveClassStatus.CANCELLED);
        assertThat(liveClassEnrolmentRepository.findByLiveClassIdIn(List.of(sessionId)))
                .allMatch(booking -> booking.getEnrolmentStatus() == LiveClassEnrolmentStatus.CANCELLED);
        assertThat(lessonRepository.findById(draftId).orElseThrow().getContentStatus()).isEqualTo(ContentStatus.DRAFT);
        assertThat(lessonRepository.findById(lessonId).orElseThrow().getContentStatus())
                .isEqualTo(ContentStatus.PUBLISHED);
        assertThat(notificationRepository.findAll()).anyMatch(notification ->
                notification.getUser().getId().equals(user(studentEmail).getId())
                        && "Live session cancelled".equals(notification.getTitle()));
    }

    @Test
    void adminCannotDeactivateTheirOwnAccount() throws Exception {
        String adminToken = api.adminToken();
        Long adminId = user(ADMIN_EMAIL).getId();

        mockMvc.perform(api.authGet(USERS + "/" + adminId, adminToken))
                .andExpect(jsonPath("$.data.self").value(true))
                .andExpect(jsonPath("$.data.deactivationImpact").doesNotExist());
        mockMvc.perform(api.jsonPost(USERS + "/" + adminId + "/deactivate", adminToken, REASON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot deactivate your own admin account."));
        mockMvc.perform(api.jsonPost(USERS + "/999999/deactivate", adminToken, REASON))
                .andExpect(status().isNotFound());
    }

    @Test
    void invitedAdminSetsAPasswordAndAppearsAsAnActiveAdmin() throws Exception {
        String email = uniqueEmail("users-invite");
        String adminToken = api.adminToken();

        mockMvc.perform(api.jsonPost("/api/v1/admin/admin-invites", adminToken, Map.of("email", email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
        mockMvc.perform(api.authGet("/api/v1/admin/admin-invites", adminToken))
                .andExpect(jsonPath("$.data[*].email", hasItem(email)));
        assertThat(userRepository.existsByEmailIgnoreCase(email)).isFalse();
        mockMvc.perform(api.jsonPost("/api/v1/admin/admin-invites", adminToken, Map.of("email", ADMIN_EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already registered."));

        // The raw link token only exists in the e-mail: give the stored invite a known token.
        String rawToken = SecureTokens.newHexToken();
        adminInviteRepository.findFirstByEmailAndAcceptedAtIsNullOrderByCreatedAtDesc(email).ifPresent(invite -> {
            invite.setTokenHash(SecureTokens.sha256Hex(rawToken));
            adminInviteRepository.save(invite);
        });
        mockMvc.perform(api.jsonPost("/api/v1/auth/admin-invite/check", null, Map.of("token", rawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
        mockMvc.perform(api.jsonPost("/api/v1/auth/admin-invite/accept", null, Map.of(
                        "token", rawToken, "fullName", "Farid Admin",
                        "password", USER_PASSWORD, "confirmPassword", USER_PASSWORD)))
                .andExpect(status().isOk());

        String newAdminToken = api.login(email, USER_PASSWORD);
        mockMvc.perform(api.authGet("/api/v1/auth/me", newAdminToken))
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data.fullName").value("Farid Admin"));
        mockMvc.perform(api.authGet("/api/v1/admin/admin-invites", adminToken))
                .andExpect(jsonPath("$.data[*].email", not(hasItem(email))));
        mockMvc.perform(api.jsonPost("/api/v1/auth/admin-invite/accept", null, Map.of(
                        "token", rawToken, "fullName", "Again",
                        "password", USER_PASSWORD, "confirmPassword", USER_PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applicationsListOnlyThoseInReviewOldestFirstWithDocumentsAndHistory() throws Exception {
        String firstEmail = uniqueEmail("apps-first");
        TeacherProfile first = api.registerTeacherWithDocument(firstEmail);
        TeacherProfile second = api.registerTeacherWithDocument(uniqueEmail("apps-second"));
        api.registerTeacher(uniqueEmail("apps-not-submitted"));
        String adminToken = api.adminToken();

        mockMvc.perform(api.authGet(APPLICATIONS, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.teacherId == " + first.getId() + ")].documentCount").value(2))
                .andExpect(jsonPath("$.data[*].teacherId", hasItem(second.getId().intValue())));
        mockMvc.perform(api.authGet(APPLICATIONS + "/" + first.getId(), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(firstEmail))
                .andExpect(jsonPath("$.data.verificationState").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.documents.length()").value(2))
                .andExpect(jsonPath("$.data.history[0].event").value("APPLICATION_SUBMITTED"))
                .andExpect(jsonPath("$.data.history[1].event").value("ACCOUNT_CREATED"));
        mockMvc.perform(api.authGet("/api/v1/users/me/nav-counts", adminToken))
                .andExpect(jsonPath("$.data.teacherApplications").exists());
    }

    @Test
    void approvingNeedsASubmittedApplicationAndUnlocksTeaching() throws Exception {
        String adminToken = api.adminToken();
        String draftEmail = uniqueEmail("apps-draft");
        api.registerTeacher(draftEmail);
        mockMvc.perform(api.jsonPost("/api/v1/admin/teachers/" + teacherProfile(draftEmail).getId() + "/approve",
                        adminToken, Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only applications in review can be approved."));

        String email = uniqueEmail("apps-approve");
        TeacherProfile profile = api.registerTeacherWithDocument(email);
        mockMvc.perform(api.jsonPost("/api/v1/admin/teachers/" + profile.getId() + "/approve", adminToken, Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationState").value("APPROVED"))
                .andExpect(jsonPath("$.data.history[0].event").value("APPROVED"));
        TeacherProfile approved = teacherProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(approved.getTeacherStatus()).isEqualTo(TeacherStatus.APPROVED);
        assertThat(notificationRepository.findAll()).anyMatch(notification ->
                notification.getUser().getId().equals(approved.getUser().getId())
                        && "Your teacher account is approved".equals(notification.getTitle()));
        mockMvc.perform(api.authGet("/api/v1/teachers/classes", api.login(email, USER_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void requestingChangesNeedsAReasonAndTheTeacherIsTold() throws Exception {
        String adminToken = api.adminToken();
        TeacherProfile profile = api.registerTeacherWithDocument(uniqueEmail("apps-changes"));

        mockMvc.perform(api.jsonPost("/api/v1/admin/teachers/" + profile.getId() + "/reject", adminToken, Map.of()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.jsonPost("/api/v1/admin/teachers/" + profile.getId() + "/reject", adminToken,
                        Map.of("reason", "Upload a clearer certificate.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.history[0].event").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.data.history[0].note").value("Upload a clearer certificate."));
        assertThat(auditLogRepository.findAll()).anyMatch(entry ->
                "ADMIN_TEACHER_REJECT".equals(entry.getActionType())
                        && "Upload a clearer certificate.".equals(entry.getReason()));
    }

    @Test
    void onlyAdminsUseTheseEndpointsAndDocumentsDownloadAsAttachments() throws Exception {
        TeacherProfile profile = api.registerTeacherWithDocument(uniqueEmail("apps-access"));
        String studentToken = api.registerStudent(uniqueEmail("apps-student"));
        String teacherToken = api.registerTeacher(uniqueEmail("apps-teacher"));
        Long documentId = documentRepository.findTopByTeacherIdOrderBySubmittedAtDesc(profile.getId()).orElseThrow().getId();

        for (String token : new String[] { studentToken, teacherToken }) {
            mockMvc.perform(api.authGet(USERS, token)).andExpect(status().isForbidden());
            mockMvc.perform(api.authGet(APPLICATIONS, token)).andExpect(status().isForbidden());
            mockMvc.perform(api.jsonPost("/api/v1/admin/teachers/" + profile.getId() + "/approve", token, Map.of()))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(USERS)).andExpect(status().isUnauthorized());
        mockMvc.perform(api.authGet("/api/v1/admin/teacher-documents/" + documentId, api.adminToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")));
        assertThat(auditLogRepository.findAll()).anyMatch(entry ->
                "ADMIN_TEACHER_DOCUMENT_VIEW".equals(entry.getActionType()) && documentId.equals(entry.getTargetId()));
    }
}
