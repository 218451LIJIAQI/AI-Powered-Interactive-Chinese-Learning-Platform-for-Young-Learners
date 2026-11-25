package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.USER_PASSWORD;
import static com.example.aichinese.support.LearningApiTestClient.fromRemoteAddress;
import static com.example.aichinese.support.LearningApiTestClient.nextClientIp;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static com.example.aichinese.support.LearningApiTestClient.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.SupportRequest;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.SupportMessageRepository;
import com.example.aichinese.repository.SupportRequestRepository;
import com.example.aichinese.service.SecureTokens;
import com.example.aichinese.service.account.AccountGuards;
import com.example.aichinese.service.support.SupportService;
import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.LearningApiTestClient;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The Account page and the account links: settings, notification preferences, e-mail change by confirmation link,
 * account deletion with its role rules, "Keep me signed in" and the reset link check.
 */
class AccountIntegrationTests extends ApiIntegrationTest {

    private static final String ACCOUNT = "/api/v1/users/me/account";
    private static final String PREFERENCES = "/api/v1/users/me/notification-preferences";
    private static final String EMAIL_CHANGE = "/api/v1/users/me/email-change";
    private static final String DELETE = "/api/v1/users/me/delete";
    private static final String ADMIN_SUPPORT = "/api/v1/admin/support/";
    private static final String REFRESH_COOKIE = "mandarin_spark_refresh";
    private static final String NEW_PASSWORD = "NewPassword456!";

    @Autowired
    private SupportRequestRepository supportRequestRepository;

    @Autowired
    private SupportMessageRepository supportMessageRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Test
    void anAccountReferenceRetainsItsIdentityOutsideThePersistenceContext() {
        User account = user(ADMIN_EMAIL);
        User reference = userRepository.getReferenceById(account.getId());

        assertThat(Hibernate.isInitialized(reference)).isFalse();
        assertThat(reference).isEqualTo(account);
        assertThat(account).isEqualTo(reference);
        assertThat(reference.hashCode()).isEqualTo(account.hashCode());
        assertThat(reference.toString()).isEqualTo("User(id=" + account.getId() + ")");
        assertThat(Hibernate.isInitialized(reference)).isFalse();
    }

    /** Learners have no e-mail to switch off; an admin can switch off the daily summary. */
    @Test
    void settingsShowOnlyThePreferencesOfTheRole() throws Exception {
        String studentToken = api.registerStudent(uniqueEmail("account-student"));

        mockMvc.perform(api.authGet(ACCOUNT, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications.reviewQueues").doesNotExist());
        mockMvc.perform(api.jsonPut(PREFERENCES, studentToken, Map.of("reviewQueues", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewQueues").doesNotExist());

        String adminToken = api.adminToken();
        mockMvc.perform(api.authGet(ACCOUNT, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications.reviewQueues").value(true));
        try {
            mockMvc.perform(api.jsonPut(PREFERENCES, adminToken, Map.of("reviewQueues", false)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reviewQueues").value(false));
        } finally {
            mockMvc.perform(api.jsonPut(PREFERENCES, adminToken, Map.of("reviewQueues", true)))
                    .andExpect(jsonPath("$.data.reviewQueues").value(true));
        }
    }

    @Test
    void emailChangesOnlyWhenTheLinkSentToTheNewAddressIsOpened() throws Exception {
        String email = uniqueEmail("account-email");
        String token = api.registerStudent(email);
        String newEmail = uniqueEmail("account-email-new");

        mockMvc.perform(api.jsonPost(EMAIL_CHANGE, token, Map.of("newEmail", newEmail, "currentPassword", "Wrong123!"))
                        .with(fromRemoteAddress(nextClientIp())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect."));
        mockMvc.perform(api.jsonPost(EMAIL_CHANGE, token, Map.of("newEmail", ADMIN_EMAIL, "currentPassword", USER_PASSWORD))
                        .with(fromRemoteAddress(nextClientIp())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already registered."));
        mockMvc.perform(api.jsonPost(EMAIL_CHANGE, token, Map.of("newEmail", newEmail, "currentPassword", USER_PASSWORD))
                        .with(fromRemoteAddress(nextClientIp())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingEmail").value(newEmail));
        mockMvc.perform(api.authGet(ACCOUNT, token))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.pendingEmail").value(newEmail));

        String rawToken = knownEmailChangeToken(user(email).getId());
        mockMvc.perform(api.jsonPost("/api/v1/auth/confirm-email", null, Map.of("token", rawToken))
                        .with(fromRemoteAddress(nextClientIp())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(newEmail));
        assertThat(api.login(newEmail, USER_PASSWORD)).isNotBlank();
        mockMvc.perform(api.authGet("/api/v1/auth/me", token)).andExpect(status().isUnauthorized());
        mockMvc.perform(api.jsonPost("/api/v1/auth/confirm-email", null, Map.of("token", rawToken))
                        .with(fromRemoteAddress(nextClientIp())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This confirmation link is invalid or has already been used."));
    }

    /** "Resend link" sends a new link that replaces the first; "Cancel change" ends the change and its link. */
    @Test
    void aWaitingEmailChangeCanBeSentAgainOrCancelled() throws Exception {
        String email = uniqueEmail("account-email-resend");
        String token = api.registerStudent(email);
        Long userId = user(email).getId();
        String newEmail = uniqueEmail("account-email-resend-new");
        String clientIp = nextClientIp();
        mockMvc.perform(api.authPost(EMAIL_CHANGE + "/resend", token).with(fromRemoteAddress(clientIp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("No email change is waiting for confirmation. Use Change email to start again."));
        mockMvc.perform(api.jsonPost(EMAIL_CHANGE, token, Map.of("newEmail", newEmail, "currentPassword", USER_PASSWORD))
                        .with(fromRemoteAddress(clientIp)))
                .andExpect(status().isOk());
        String firstLink = knownEmailChangeToken(userId);

        mockMvc.perform(api.authPost(EMAIL_CHANGE + "/resend", token).with(fromRemoteAddress(clientIp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingEmail").value(newEmail));
        mockMvc.perform(api.jsonPost("/api/v1/auth/confirm-email", null, Map.of("token", firstLink))
                        .with(fromRemoteAddress(clientIp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This confirmation link is invalid or has already been used."));
        mockMvc.perform(api.authGet(ACCOUNT, token)).andExpect(jsonPath("$.data.pendingEmail").value(newEmail));
        String secondLink = knownEmailChangeToken(userId);

        mockMvc.perform(api.authDelete(EMAIL_CHANGE, token)).andExpect(status().isOk());
        mockMvc.perform(api.authGet(ACCOUNT, token))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.pendingEmail").doesNotExist());
        mockMvc.perform(api.jsonPost("/api/v1/auth/confirm-email", null, Map.of("token", secondLink))
                        .with(fromRemoteAddress(clientIp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This confirmation link is invalid or has already been used."));
        assertThat(auditEntriesAbout("User", userId)).extracting(AuditLog::getActionType).contains("EMAIL_CHANGE_CANCELLED");
        // Nothing waits any more: cancelling again changes nothing.
        mockMvc.perform(api.authDelete(EMAIL_CHANGE, token)).andExpect(status().isOk());
    }

    @Test
    void changingThePasswordRetiresTheOpenEmailChangeLink() throws Exception {
        String email = uniqueEmail("account-email-password");
        String token = api.registerStudent(email);
        mockMvc.perform(api.jsonPost(EMAIL_CHANGE, token,
                        Map.of("newEmail", uniqueEmail("account-email-password-new"), "currentPassword", USER_PASSWORD))
                        .with(fromRemoteAddress(nextClientIp())))
                .andExpect(status().isOk());
        String rawToken = knownEmailChangeToken(user(email).getId());

        mockMvc.perform(api.changePasswordRequest(token, USER_PASSWORD, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk());

        assertThat(api.login(email, NEW_PASSWORD)).isNotBlank();
        mockMvc.perform(api.jsonPost("/api/v1/auth/confirm-email", null, Map.of("token", rawToken))
                        .with(fromRemoteAddress(nextClientIp())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This confirmation link is invalid or has already been used."));
    }

    @Test
    void confirmingANewEmailRetiresTheOpenResetLink() throws Exception {
        String email = uniqueEmail("account-email-reset");
        String token = api.registerStudent(email);
        Long userId = user(email).getId();
        String clientIp = nextClientIp();
        mockMvc.perform(api.jsonPost(EMAIL_CHANGE, token,
                        Map.of("newEmail", uniqueEmail("account-email-reset-new"), "currentPassword", USER_PASSWORD))
                        .with(fromRemoteAddress(clientIp)))
                .andExpect(status().isOk());
        mockMvc.perform(api.jsonPost("/api/v1/auth/forgot-password", null, Map.of("email", email))
                        .with(fromRemoteAddress(clientIp)))
                .andExpect(status().isOk());

        mockMvc.perform(api.jsonPost("/api/v1/auth/confirm-email", null, Map.of("token", knownEmailChangeToken(userId)))
                        .with(fromRemoteAddress(clientIp)))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForList(
                "select used_at is not null from password_reset_tokens where user_id = ?", Boolean.class, userId))
                .containsOnly(true);
    }

    /** A link retired by a newer one records when, like every other token change: its updated_at is its used_at. */
    @Test
    void aLinkRetiredByANewerOneRecordsWhenItWasRetired() throws Exception {
        String email = uniqueEmail("account-retired-links");
        String token = api.registerStudent(email);
        Long userId = user(email).getId();
        // Past the one-minute pause, so the sign-up link can be sent again.
        jdbc.update("update email_confirmation_tokens set created_at = ? where user_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(2)), userId);
        mockMvc.perform(api.authPost("/api/v1/users/me/email-confirmation", token)).andExpect(status().isOk());
        String clientIp = nextClientIp();
        for (int request = 0; request < 2; request++) {
            mockMvc.perform(api.jsonPost(EMAIL_CHANGE, token, Map.of(
                                    "newEmail", uniqueEmail("account-retired-links-new"), "currentPassword", USER_PASSWORD))
                            .with(fromRemoteAddress(clientIp)))
                    .andExpect(status().isOk());
        }

        for (String table : List.of("email_confirmation_tokens", "email_change_tokens")) {
            assertThat(jdbc.queryForList(
                    "select updated_at = used_at from " + table + " where user_id = ? and used_at is not null",
                    Boolean.class, userId)).as(table).containsExactly(true);
        }

        // Even if created_at sits ahead of wall-clock now (clock step-back / multi-node skew), retirement clamps used_at >= created_at.
        mockMvc.perform(api.jsonPost("/api/v1/auth/forgot-password", null, Map.of("email", email))
                        .with(fromRemoteAddress(clientIp)))
                .andExpect(status().isOk());
        LocalDateTime ahead = LocalDateTime.now().plusMinutes(5).withNano(0);
        jdbc.update("update password_reset_tokens set created_at = ? where user_id = ? and used_at is null",
                Timestamp.valueOf(ahead), userId);
        mockMvc.perform(api.jsonPost("/api/v1/auth/forgot-password", null, Map.of("email", email))
                        .with(fromRemoteAddress(clientIp)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForList(
                "select used_at >= created_at and updated_at = used_at from password_reset_tokens where user_id = ? and used_at is not null",
                Boolean.class, userId)).containsOnly(true);
    }

    @Test
    void studentDeletesTheAccountAndItsPersonalDataGoes() throws Exception {
        String email = uniqueEmail("account-delete");
        String token = api.registerStudent(email);
        mockMvc.perform(api.authMultipart("/api/v1/users/me/support", token)
                        .param("topic", "ACCOUNT").param("subject", "Change my name").param("message", "Please"))
                .andExpect(status().isOk());
        Long userId = user(email).getId();

        mockMvc.perform(api.jsonPost(DELETE, token, Map.of("confirmEmail", "someone@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The email address you typed doesn't match your account."));
        mockMvc.perform(api.jsonPost(DELETE, token, Map.of("confirmEmail", email.toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie", hasItem(containsString("Max-Age=0"))));

        User deleted = userRepository.findById(userId).orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.isActive()).isFalse();
        assertThat(deleted.getEmail()).doesNotContain(email);
        assertThat(deleted.getDisplayName()).isEqualTo("Deleted account");
        assertThat(jdbc.queryForObject("select count(*) from support_requests where requester_user_id = ?", Long.class, userId))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ?", Long.class, userId))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ?", Long.class, userId))
                .isZero();
        mockMvc.perform(api.authGet("/api/v1/auth/me", token)).andExpect(status().isUnauthorized());
        mockMvc.perform(api.loginRequest(email, USER_PASSWORD)).andExpect(status().isUnauthorized());
        String adminToken = api.adminToken();
        mockMvc.perform(api.authGet("/api/v1/admin/users?keyword=" + email, adminToken))
                .andExpect(jsonPath("$.data.totalItems").value(0));
        mockMvc.perform(api.jsonPost("/api/v1/admin/users/" + userId + "/reactivate", adminToken,
                        Map.of("reason", "Asked to come back.")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A deleted account can't be reactivated."));
        mockMvc.perform(api.jsonPost("/api/v1/admin/users/" + userId + "/deactivate", adminToken,
                        Map.of("reason", "Should not deactivate deleted user.")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A deleted account can't be deactivated."));
        mockMvc.perform(api.authGet("/api/v1/admin/users/" + userId, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    /**
     * The Privacy notice and the delete dialog: what admins were told about the learner, and the audit trail, keep no
     * trace of the name or the e-mail.
     */
    @Test
    void otherPeopleNoLongerSeeADeletedLearnersNameOrEmail() throws Exception {
        String email = uniqueEmail("account-privacy");
        String token = api.registerStudent(email);
        String name = user(email).getDisplayName();
        // The subject is the learner's own words, here with the name and the address; support answers and solves it.
        String subject = name + " / " + email;
        JsonNode request = api.data(mockMvc.perform(api.authMultipart("/api/v1/users/me/support", token)
                        .param("topic", "ACCOUNT").param("subject", subject).param("message", "Please"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andReturn());
        int requestNumber = request.path("number").asInt();
        Long requestId = supportRequestId(requestNumber);
        Long planReportId = api.id(mockMvc.perform(api.jsonPost("/api/v1/ai/reports", token, Map.of(
                        "featureKey", "LEARNING_PLAN",
                        "reasonCategory", "DOES_NOT_MATCH_INPUT",
                        "description", "My email is " + email + " and my name is " + name,
                        "promptSnapshot", "Moving to KL (" + email + ")",
                        "aiResponseSnapshot", "Goal for " + name + ": speak Mandarin in KL.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.promptSnapshot").value("Moving to KL ([EMAIL])"))
                .andReturn());
        Long hintReportId = api.id(mockMvc.perform(api.jsonPost("/api/v1/ai/reports", token, Map.of(
                        "featureKey", "HINT",
                        "reasonCategory", "UNCLEAR_EXPLANATION",
                        "description", "Note from " + name + " (" + email + ")",
                        "promptSnapshot", "Learner prompt by " + name,
                        "aiResponseSnapshot", "It starts with 一.")))
                .andExpect(status().isOk())
                .andReturn());
        String adminToken = api.adminToken();
        mockMvc.perform(api.jsonPost(ADMIN_SUPPORT + requestId + "/replies", adminToken, Map.of("body", "Done.")))
                .andExpect(status().isOk());
        mockMvc.perform(api.authPost(ADMIN_SUPPORT + requestId + "/solve", adminToken)).andExpect(status().isOk());
        mockMvc.perform(api.jsonPost(EMAIL_CHANGE, token,
                        Map.of("newEmail", uniqueEmail("account-privacy-new"), "currentPassword", USER_PASSWORD))
                        .with(fromRemoteAddress(nextClientIp())))
                .andExpect(status().isOk());
        assertThat(notificationsOf(ADMIN_EMAIL))
                .as("admins were told about the learner by name")
                .anyMatch(notification -> mentions(notification.getTitle() + notification.getMessage(), name, email));
        assertThat(auditLogRepository.findAll())
                .as("the audit log named the request by its subject, and the account by its e-mail")
                .anyMatch(entry -> String.valueOf(entry.getTargetLabel()).contains(subject))
                .anyMatch(entry -> "ACCOUNT_CREATED".equals(entry.getActionType()) && email.equals(entry.getActorEmail()));

        mockMvc.perform(api.jsonPost(DELETE, token, Map.of("confirmEmail", email))).andExpect(status().isOk());

        assertThat(notificationRepository.findAll())
                .noneMatch(notification -> mentions(notification.getTitle() + notification.getMessage(), name, email));
        List<AuditLog> remainingEntries = auditLogRepository.findAll();
        assertThat(remainingEntries)
                .noneMatch(entry -> mentions(auditText(entry), name, email))
                .noneMatch(entry -> auditText(entry).contains(subject));
        // Support's reply and solve stay in the log, under the request number.
        assertThat(remainingEntries)
                .filteredOn(entry -> SupportService.TARGET_TYPE.equals(entry.getTargetType())
                        && requestId.equals(entry.getTargetId()))
                .extracting(AuditLog::getActionType, AuditLog::getTargetLabel)
                .containsExactlyInAnyOrder(
                        tuple("SUPPORT_REPLY_SENT", "#" + requestNumber),
                        tuple("SUPPORT_REQUEST_SOLVED", "#" + requestNumber));
        mockMvc.perform(api.authGet("/api/v1/admin/ai/reports/" + planReportId, adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(api.authGet("/api/v1/admin/ai/reports/" + hintReportId, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentName").value("Deleted account"))
                .andExpect(jsonPath("$.data.description").doesNotExist())
                .andExpect(jsonPath("$.data.promptSnapshot").doesNotExist())
                .andExpect(jsonPath("$.data.aiResponseSnapshot").value("It starts with 一."));
    }

    /**
     * "Support requests are removed": also the ones sent from the account's address on the public Help page (no
     * account, as when the student could not sign in), with their messages, the admins' notices and the subject in
     * the audit log. A request from another address stays.
     */
    @Test
    void deletionRemovesTheRequestsSentFromTheAccountsAddressOnTheHelpPage() throws Exception {
        String email = uniqueEmail("account-visitor");
        String token = api.registerStudent(email);
        // Only a verified account has proven ownership of the email address used by unlinked visitor requests.
        User learner = user(email);
        learner.setEmailVerifiedAt(LocalDateTime.now());
        userRepository.save(learner);
        String subject = "Locked out: " + email;
        Long sentSignedOut = visitorRequest(email, subject + "\nThe reset link never came.");
        // An address stored before addresses were kept in lower case still belongs to the account.
        SupportRequest older = supportRequestRepository.findById(visitorRequest(email, "Older question")).orElseThrow();
        older.setRequesterEmail(email.toUpperCase(Locale.ROOT));
        supportRequestRepository.save(older);
        Long otherAddress = visitorRequest(uniqueEmail("account-visitor-other"), "Someone else's question");
        String adminToken = api.adminToken();
        mockMvc.perform(api.jsonPost(ADMIN_SUPPORT + sentSignedOut + "/replies", adminToken, Map.of("body", "Try again.")))
                .andExpect(status().isOk());
        mockMvc.perform(api.authPost(ADMIN_SUPPORT + sentSignedOut + "/solve", adminToken)).andExpect(status().isOk());
        int number = supportRequestRepository.findById(sentSignedOut).orElseThrow().getNumber();

        mockMvc.perform(api.jsonPost(DELETE, token, Map.of("confirmEmail", email))).andExpect(status().isOk());

        List<Long> removed = List.of(sentSignedOut, older.getId());
        assertThat(supportRequestRepository.findAllById(removed)).isEmpty();
        for (Long id : removed) {
            assertThat(supportMessageRepository.findByRequestIdOrderByCreatedAtAscIdAsc(id)).isEmpty();
            mockMvc.perform(api.authGet(ADMIN_SUPPORT + id, adminToken)).andExpect(status().isNotFound());
        }
        assertThat(notificationRepository.findAll())
                .noneMatch(notice -> SupportService.TARGET_TYPE.equals(notice.getTargetType())
                        && removed.contains(notice.getTargetId()))
                .noneMatch(notice -> (notice.getTitle() + notice.getMessage()).contains(email))
                .anyMatch(notice -> SupportService.TARGET_TYPE.equals(notice.getTargetType())
                        && otherAddress.equals(notice.getTargetId()));
        assertThat(auditLogRepository.findAll())
                .noneMatch(entry -> auditText(entry).contains(email))
                .filteredOn(entry -> SupportService.TARGET_TYPE.equals(entry.getTargetType())
                        && sentSignedOut.equals(entry.getTargetId()))
                .extracting(AuditLog::getTargetLabel)
                .containsExactly("#" + number, "#" + number);
        assertThat(supportRequestRepository.findById(otherAddress)).isPresent();
    }

    @Test
    void unverifiedAccountDeletionDoesNotRemoveThirdPartyVisitorSupportRequests() throws Exception {
        String victimEmail = uniqueEmail("unverified-victim");
        Long visitorTicketId = visitorRequest(victimEmail, "I need help with my account.");
        String token = api.registerStudent(victimEmail);
        // Learner registers without confirming email ownership, creates one signed-in request, then deletes account.
        JsonNode ownRequest = api.data(mockMvc.perform(api.authMultipart("/api/v1/users/me/support", token)
                        .param("topic", "ACCOUNT").param("subject", "Own request").param("message", "Hello"))
                .andExpect(status().isOk())
                .andReturn());
        Long ownRequestId = supportRequestId(ownRequest.path("number").asInt());

        mockMvc.perform(api.jsonPost(DELETE, token, Map.of("confirmEmail", victimEmail)))
                .andExpect(status().isOk());

        // Own signed-in request is deleted, while unlinked visitor request remains protected.
        assertThat(supportRequestRepository.findById(ownRequestId)).isEmpty();
        assertThat(supportRequestRepository.findById(visitorTicketId)).isPresent();
    }

    /**
     * Databases whose earlier version stored school names keep student_profiles.school_name (V2 drops it only where it
     * is empty, and no entity maps it): deletion clears the student's. The test schema has no such column, so it is
     * added here as an upgraded database has it.
     */
    @Test
    void deletionClearsTheSchoolNameAnUpgradedDatabaseKept() throws Exception {
        String email = uniqueEmail("account-school");
        String token = api.registerStudent(email);
        Long profileId = studentProfile(email).getId();
        String otherEmail = uniqueEmail("account-school-other");
        api.registerStudentAccount(otherEmail);
        Long otherProfileId = studentProfile(otherEmail).getId();
        jdbc.execute("alter table student_profiles add column school_name varchar(150) default null");
        try {
            jdbc.update("update student_profiles set school_name = 'SMK Taman Desa' where id in (?, ?)",
                    profileId, otherProfileId);

            mockMvc.perform(api.jsonPost(DELETE, token, Map.of("confirmEmail", email))).andExpect(status().isOk());

            assertThat(schoolName(profileId)).isNull();
            assertThat(schoolName(otherProfileId)).isEqualTo("SMK Taman Desa");
        } finally {
            jdbc.execute("alter table student_profiles drop column school_name");
        }
    }

    private String schoolName(Long profileId) {
        return jdbc.queryForObject("select school_name from student_profiles where id = ?", String.class, profileId);
    }

    /** A request sent through the public Help page; its subject is the message's first line. */
    private Long visitorRequest(String email, String message) throws Exception {
        int number = api.data(mockMvc.perform(api.jsonPost("/api/v1/public/support", null,
                                Map.of("email", email, "topic", "ACCOUNT", "message", message))
                                .with(fromRemoteAddress(nextClientIp())))
                        .andExpect(status().isOk())
                        .andReturn())
                .path("number").asInt();
        return supportRequestId(number);
    }

    private static String auditText(AuditLog entry) {
        return entry.getActorEmail() + entry.getTargetLabel() + entry.getDescription() + entry.getReason();
    }

    private static boolean mentions(String text, String name, String email) {
        return text.contains(name) || text.contains(email);
    }

    @Test
    void adminsCannotDeleteTheirAccount() throws Exception {
        mockMvc.perform(api.jsonPost(DELETE, api.adminToken(), Map.of("confirmEmail", ADMIN_EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Admin accounts can't be deleted. Another admin can deactivate this account."));
    }

    /** A learner who joined a class deletes the account; the class keeps going for everyone else. */
    @Test
    void aLearnerInAClassCanDeleteTheAccount() throws Exception {
        LearningApiTestClient.PublishedClass published = api.publishedClass(api.adminToken(), "Deleting class " + uniqueSuffix());
        String email = uniqueEmail("account-in-class");
        String token = api.registerStudent(email);
        api.join(token, published.classId());
        api.submitQuiz(token, published.quizId(), true, "hǎo");

        mockMvc.perform(api.jsonPost(DELETE, token, Map.of("confirmEmail", email))).andExpect(status().isOk());

        assertThat(lessonRepository.findById(published.lessonId()).orElseThrow().getContentStatus())
                .isEqualTo(ContentStatus.PUBLISHED);
        String otherToken = api.registerStudent(uniqueEmail("account-in-class-other"));
        api.join(otherToken, published.classId());
        mockMvc.perform(api.authGet("/api/v1/students/classes/" + published.classId(), otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joined").value(true));
    }

    @Test
    void keepMeSignedInDecidesWhetherTheRefreshCookieOutlivesTheBrowser() throws Exception {
        String email = uniqueEmail("account-remember");
        api.registerStudentAccount(email);

        mockMvc.perform(api.jsonPost("/api/v1/auth/login", null,
                        Map.of("email", email, "password", USER_PASSWORD, "keepSignedIn", false)))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie", hasItem(
                        allOf(containsString("/api/v1/auth/refresh"), not(containsString("Max-Age"))))));
        mockMvc.perform(api.jsonPost("/api/v1/auth/login", null,
                        Map.of("email", email, "password", USER_PASSWORD, "keepSignedIn", true)))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie", hasItem(
                        allOf(containsString("/api/v1/auth/refresh"), containsString("Max-Age="),
                                not(containsString("Max-Age=0"))))));
    }

    /** A password change replaces the caller's session; a browser-session sign-in must not come back lasting. */
    @Test
    void passwordChangeKeepsTheKeepMeSignedInChoiceOfTheSignIn() throws Exception {
        String email = uniqueEmail("account-remember-password");
        api.registerStudentAccount(email);

        // Signed in without "Keep me signed in" (a shared computer), then the access token was refreshed once.
        MvcResult browserSession = mockMvc.perform(api.jsonPost("/api/v1/auth/login", null,
                        Map.of("email", email, "password", USER_PASSWORD, "keepSignedIn", false)))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(browserSession.getResponse().getCookie(REFRESH_COOKIE)))
                .andExpect(status().isOk())
                .andReturn();
        mockMvc.perform(api.changePasswordRequest(api.data(refreshed).path("token").asText(),
                        USER_PASSWORD, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie", hasItem(
                        allOf(containsString("/api/v1/auth/refresh"), not(containsString("Max-Age"))))));

        String keptSession = api.data(mockMvc.perform(api.jsonPost("/api/v1/auth/login", null,
                        Map.of("email", email, "password", NEW_PASSWORD, "keepSignedIn", true)))
                .andExpect(status().isOk())
                .andReturn()).path("token").asText();
        String nextPassword = "OtherPassword789!";
        mockMvc.perform(api.changePasswordRequest(keptSession, NEW_PASSWORD, nextPassword, nextPassword))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie", hasItem(
                        allOf(containsString("/api/v1/auth/refresh"), containsString("Max-Age="),
                                not(containsString("Max-Age=0"))))));
    }

    @Test
    void deletingAVerifiedLearnerErasesAiHistoryAndAnonymisesAdminInviteAuditLabels() throws Exception {
        String email = uniqueEmail("account-erase-ai");
        String token = api.registerStudent(email);
        Long userId = user(email).getId();
        jdbc.update("update users set email_verified_at = now() where id = ?", userId);
        jdbc.update("""
                insert into ai_chat_sessions (user_id, created_at, updated_at) values (?, now(), now())
                """, userId);
        Long sessionId = jdbc.queryForObject(
                "select id from ai_chat_sessions where user_id = ? order by id desc limit 1", Long.class, userId);
        jdbc.update("""
                insert into ai_chat_messages (session_id, sender_role, message_text, created_at, updated_at)
                values (?, 'USER', 'Private question', now(), now())
                """, sessionId);
        jdbc.update("""
                insert into ai_practice_checks
                    (student_user_id, check_type, target_text, score, headline, created_at, updated_at)
                values (?, 'HANDWRITING', '你好', 88, 'Clear strokes', now(), now())
                """, userId);
        jdbc.update("""
                insert into audit_logs
                    (actor_email, action_type, target_type, target_id, target_label, description, created_at, updated_at)
                values (?, 'ADMIN_INVITE_SENT', 'AdminInvite', 999, ?, 'Admin sent invite.', now(), now())
                """, ADMIN_EMAIL, email);

        mockMvc.perform(api.jsonPost(DELETE, token, Map.of("confirmEmail", email)))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from ai_chat_sessions where user_id = ?", Long.class, userId))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from ai_chat_messages where session_id = ?", Long.class, sessionId))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from ai_practice_checks where student_user_id = ?", Long.class, userId))
                .isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where target_type = 'AdminInvite' and target_label = ?",
                Long.class, email)).isZero();
    }

    /** Deletion gives an account the address deleted-{id}@deleted.invalid; an account holding it would block that. */
    @Test
    void deletedAccountAddressesCannotBeTakenSoEveryAccountCanBeDeleted() throws Exception {
        String email = uniqueEmail("account-reserved");
        String token = api.registerStudent(email);
        Long userId = user(email).getId();
        String reserved = "deleted-" + userId + "@deleted.invalid";

        mockMvc.perform(api.jsonPost("/api/v1/auth/register/student", null, Map.of(
                        "fullName", "Address squatter",
                        "email", reserved,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already registered."));
        String otherToken = api.registerStudent(uniqueEmail("account-reserved-other"));
        mockMvc.perform(api.jsonPost(EMAIL_CHANGE, otherToken, Map.of("newEmail", reserved, "currentPassword", USER_PASSWORD))
                        .with(fromRemoteAddress(nextClientIp())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already registered."));
        mockMvc.perform(api.jsonPost("/api/v1/admin/admin-invites", api.adminToken(), Map.of("email", reserved)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already registered."));

        mockMvc.perform(api.jsonPost(DELETE, token, Map.of("confirmEmail", email))).andExpect(status().isOk());
        assertThat(userRepository.findById(userId).orElseThrow().getEmail()).isEqualTo(reserved);
    }

    /** Every request e-mails the address typed in: ten links per address in 15 minutes, from wherever they are asked. */
    @Test
    void confirmationLinksToOneAddressAreLimited() throws Exception {
        String recipient = uniqueEmail("account-email-recipient");
        String token = api.registerStudent(uniqueEmail("account-email-sender"));
        Map<String, String> request = Map.of("newEmail", recipient, "currentPassword", USER_PASSWORD);

        for (int sent = 0; sent < 10; sent++) {
            mockMvc.perform(api.jsonPost(EMAIL_CHANGE, token, request).with(fromRemoteAddress(nextClientIp())))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(api.jsonPost(EMAIL_CHANGE, token, request).with(fromRemoteAddress(nextClientIp())))
                .andExpect(status().isTooManyRequests());
    }

    /** "Already registered" tells whether another account holds an address, so asking counts like a sent link. */
    @Test
    void checkingWhetherAnAddressIsRegisteredIsLimitedToo() throws Exception {
        String taken = uniqueEmail("account-email-taken");
        api.registerStudentAccount(taken);
        String token = api.registerStudent(uniqueEmail("account-email-prober"));
        Map<String, String> request = Map.of("newEmail", taken, "currentPassword", USER_PASSWORD);

        // Distinct client IPs ensure the per-email bucket specifically counts already-registered probes.
        for (int asked = 0; asked < 10; asked++) {
            mockMvc.perform(api.jsonPost(EMAIL_CHANGE, token, request).with(fromRemoteAddress(nextClientIp())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Email is already registered."));
        }
        mockMvc.perform(api.jsonPost(EMAIL_CHANGE, token, request).with(fromRemoteAddress(nextClientIp())))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void resetLinkCheckTellsTheAccountOrWhyTheLinkNoLongerWorks() throws Exception {
        String email = uniqueEmail("account-reset-check");
        api.registerStudentAccount(email);
        String clientIp = nextClientIp();
        mockMvc.perform(api.jsonPost("/api/v1/auth/forgot-password", null, Map.of("email", email))
                        .with(fromRemoteAddress(clientIp)))
                .andExpect(status().isOk());
        String rawToken = SecureTokens.newHexToken();
        Long userId = user(email).getId();
        int updated = jdbc.update("update password_reset_tokens set token = ? where user_id = ? and used_at is null",
                SecureTokens.sha256Hex(rawToken), userId);
        assertThat(updated).isEqualTo(1);

        mockMvc.perform(api.jsonPost("/api/v1/auth/reset-password/check", null, Map.of("token", rawToken))
                        .with(fromRemoteAddress(clientIp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
        mockMvc.perform(api.jsonPost("/api/v1/auth/reset-password/check", null, Map.of("token", "f".repeat(64)))
                        .with(fromRemoteAddress(clientIp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Reset token is invalid or has already been used."));
    }

    /** A deleted account keeps a reserved address that receives no mail: asking a reset link for it issues none. */
    @Test
    void noResetLinkIsIssuedForADeletedAccountsAddress() throws Exception {
        String email = uniqueEmail("account-reset-deleted");
        String token = api.registerStudent(email);
        Long userId = user(email).getId();
        mockMvc.perform(api.jsonPost(DELETE, token, Map.of("confirmEmail", email))).andExpect(status().isOk());

        mockMvc.perform(api.jsonPost("/api/v1/auth/forgot-password", null,
                        Map.of("email", AccountGuards.deletedAccountEmail(userId)))
                        .with(fromRemoteAddress(nextClientIp())))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens where user_id = ?", Long.class, userId))
                .isZero();
    }
}
