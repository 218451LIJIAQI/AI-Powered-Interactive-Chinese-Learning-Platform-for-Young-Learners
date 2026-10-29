package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.USER_PASSWORD;
import static com.example.aichinese.support.LearningApiTestClient.fromRemoteAddress;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static com.example.aichinese.support.LearningApiTestClient.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.aichinese.entity.EmailChangeToken;
import com.example.aichinese.entity.PasswordResetToken;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.EmailChangeTokenRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.PasswordResetTokenRepository;
import com.example.aichinese.repository.SupportRequestRepository;
import com.example.aichinese.repository.TeacherVerificationDocumentRepository;
import com.example.aichinese.service.SecureTokens;
import com.example.aichinese.support.ApiIntegrationTest;

/**
 * The Account page and the account links: settings, notification preferences, e-mail change by confirmation link,
 * account deletion with its role rules, "Keep me signed in", the sign-in lockout's Retry-After and the reset link check.
 */
class AccountIntegrationTests extends ApiIntegrationTest {

    private static final String ACCOUNT = "/api/v1/users/me/account";
    private static final String PREFERENCES = "/api/v1/users/me/notification-preferences";
    private static final String EMAIL_CHANGE = "/api/v1/users/me/email-change";
    private static final String DELETE = "/api/v1/users/me/delete";

    @Autowired
    private EmailChangeTokenRepository emailChangeTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private SupportRequestRepository supportRequestRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TeacherVerificationDocumentRepository documentRepository;

    @Test
    void settingsShowOnlyThePreferencesOfTheRole() throws Exception {
        String studentToken = api.registerStudent(uniqueEmail("account-student"));

        mockMvc.perform(api.authGet(ACCOUNT, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications.liveSessionReminders").value(true))
                .andExpect(jsonPath("$.data.notifications.feedback").value(true))
                .andExpect(jsonPath("$.data.notifications.contentReviews").doesNotExist())
                .andExpect(jsonPath("$.data.deletion.blockedBy").doesNotExist());
        mockMvc.perform(api.jsonPut(PREFERENCES, studentToken, Map.of("feedback", false, "reviewQueues", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedback").value(false))
                .andExpect(jsonPath("$.data.reviewQueues").doesNotExist());

        String adminToken = api.adminToken();
        mockMvc.perform(api.authGet(ACCOUNT, adminToken))
                .andExpect(jsonPath("$.data.notifications.reviewQueues").value(true))
                .andExpect(jsonPath("$.data.notifications.liveSessionReminders").doesNotExist())
                .andExpect(jsonPath("$.data.deletion.blockedBy").value("ADMIN_ACCOUNT"));
        String teacherToken = api.registerTeacher(uniqueEmail("account-teacher"));
        mockMvc.perform(api.authGet(ACCOUNT, teacherToken))
                .andExpect(jsonPath("$.data.notifications.contentReviews").value(true))
                .andExpect(jsonPath("$.data.notifications.feedback").doesNotExist());
    }

    @Test
    void emailChangesOnlyWhenTheLinkSentToTheNewAddressIsOpened() throws Exception {
        String email = uniqueEmail("account-email");
        String token = api.registerStudent(email);
        String newEmail = uniqueEmail("account-email-new");

        mockMvc.perform(api.jsonPost(EMAIL_CHANGE, token, Map.of("newEmail", newEmail, "currentPassword", "Wrong123!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect."));
        mockMvc.perform(api.jsonPost(EMAIL_CHANGE, token, Map.of("newEmail", ADMIN_EMAIL, "currentPassword", USER_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already registered."));
        mockMvc.perform(api.jsonPost(EMAIL_CHANGE, token, Map.of("newEmail", newEmail, "currentPassword", USER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingEmail").value(newEmail));
        mockMvc.perform(api.authGet(ACCOUNT, token))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.pendingEmail").value(newEmail));

        String rawToken = knownEmailChangeToken(user(email));
        mockMvc.perform(api.jsonPost("/api/v1/auth/confirm-email", null, Map.of("token", rawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(newEmail));
        assertThat(api.login(newEmail, USER_PASSWORD)).isNotBlank();
        mockMvc.perform(api.authGet("/api/v1/auth/me", token)).andExpect(status().isUnauthorized());
        mockMvc.perform(api.jsonPost("/api/v1/auth/confirm-email", null, Map.of("token", rawToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This confirmation link is invalid or has already been used."));
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
        assertThat(supportRequestRepository.findByRequesterId(userId)).isEmpty();
        assertThat(notificationRepository.findAll()).noneMatch(notification -> notification.getUser().getId().equals(userId));
        mockMvc.perform(api.authGet("/api/v1/auth/me", token)).andExpect(status().isUnauthorized());
        mockMvc.perform(api.loginRequest(email, USER_PASSWORD)).andExpect(status().isUnauthorized());
        mockMvc.perform(api.authGet("/api/v1/admin/users?keyword=" + email, api.adminToken()))
                .andExpect(jsonPath("$.data.totalItems").value(0));
    }

    @Test
    void applicantDeletionRemovesTheVerificationDocuments() throws Exception {
        String email = uniqueEmail("account-applicant");
        Long teacherId = api.registerTeacherWithDocument(email).getId();
        String token = api.login(email, USER_PASSWORD);

        mockMvc.perform(api.jsonPost(DELETE, token, Map.of("confirmEmail", email))).andExpect(status().isOk());

        assertThat(documentRepository.findByTeacherId(teacherId)).isEmpty();
        assertThat(teacherProfileRepository.findById(teacherId).orElseThrow().getBio()).isNull();
    }

    @Test
    void teachersWithAPublishedClassAndAdminsCannotDeleteTheirAccount() throws Exception {
        String teacherEmail = uniqueEmail("account-published");
        String teacherToken = api.approvedTeacher(teacherEmail);
        Long packageId = api.publishedClass(teacherToken, "Mandarin for Beginners " + uniqueSuffix()).classId();

        mockMvc.perform(api.authGet(ACCOUNT, teacherToken))
                .andExpect(jsonPath("$.data.deletion.blockedBy").value("PUBLISHED_CLASSES"))
                .andExpect(jsonPath("$.data.deletion.publishedClasses[0].id").value(packageId));
        mockMvc.perform(api.jsonPost(DELETE, teacherToken, Map.of("confirmEmail", teacherEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Archive your published classes before you delete your account."));
        mockMvc.perform(api.jsonPost(DELETE, api.adminToken(), Map.of("confirmEmail", ADMIN_EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Admin accounts can't be deleted. Another admin can deactivate this account."));
    }

    @Test
    void keepMeSignedInDecidesWhetherTheRefreshCookieOutlivesTheBrowser() throws Exception {
        String email = uniqueEmail("account-remember");
        api.registerStudent(email);

        mockMvc.perform(api.jsonPost("/api/v1/auth/login", null,
                        Map.of("email", email, "password", USER_PASSWORD, "keepSignedIn", false)))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie", hasItem(
                        allOf(containsString("/api/v1/auth/refresh"), not(containsString("Max-Age"))))));
        mockMvc.perform(api.jsonPost("/api/v1/auth/login", null,
                        Map.of("email", email, "password", USER_PASSWORD, "keepSignedIn", true)))
                .andExpect(header().stringValues("Set-Cookie", hasItem(
                        allOf(containsString("/api/v1/auth/refresh"), containsString("Max-Age=")))));
    }

    @Test
    void lockedSignInSaysHowLongToWait() throws Exception {
        String email = uniqueEmail("account-locked");
        api.registerStudent(email);
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(api.loginRequest(email, "Wrong-password-1").with(
                    fromRemoteAddress("203.0.113.77")));
        }

        mockMvc.perform(api.loginRequest(email, USER_PASSWORD).with(
                        fromRemoteAddress("203.0.113.77")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void resetLinkCheckTellsTheAccountOrWhyTheLinkNoLongerWorks() throws Exception {
        String email = uniqueEmail("account-reset-check");
        api.registerStudent(email);
        mockMvc.perform(api.jsonPost("/api/v1/auth/forgot-password", null, Map.of("email", email))
                        .with(fromRemoteAddress("203.0.113.78")))
                .andExpect(status().isOk());
        String rawToken = SecureTokens.newHexToken();
        Long userId = user(email).getId();
        passwordResetTokenRepository.findAll().stream()
                .filter(token -> token.getUser().getId().equals(userId))
                .findFirst()
                .ifPresent(token -> {
                    passwordResetTokenRepository.delete(token);
                    PasswordResetToken replacement =
                            new PasswordResetToken();
                    replacement.setUser(token.getUser());
                    replacement.setTokenHash(SecureTokens.sha256Hex(rawToken));
                    replacement.setExpiresAt(token.getExpiresAt());
                    passwordResetTokenRepository.save(replacement);
                });

        mockMvc.perform(api.jsonPost("/api/v1/auth/reset-password/check", null, Map.of("token", rawToken))
                        .with(fromRemoteAddress("203.0.113.78")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
        mockMvc.perform(api.jsonPost("/api/v1/auth/reset-password/check", null, Map.of("token", "f".repeat(64)))
                        .with(fromRemoteAddress("203.0.113.78")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Reset token is invalid or has already been used."));
    }

    /** The raw link only exists in the e-mail: give the user's open change link a known token. */
    private String knownEmailChangeToken(User user) {
        String rawToken = SecureTokens.newHexToken();
        emailChangeTokenRepository.findAll().stream()
                .filter(token -> token.getUser().getId().equals(user.getId()) && token.getUsedAt() == null)
                .forEach(token -> {
                    emailChangeTokenRepository.delete(token);
                    EmailChangeToken replacement =
                            new EmailChangeToken();
                    replacement.setUser(token.getUser());
                    replacement.setNewEmail(token.getNewEmail());
                    replacement.setTokenHash(SecureTokens.sha256Hex(rawToken));
                    replacement.setExpiresAt(token.getExpiresAt());
                    emailChangeTokenRepository.save(replacement);
                });
        return rawToken;
    }
}
