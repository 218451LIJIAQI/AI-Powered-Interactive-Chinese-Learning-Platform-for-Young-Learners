package com.example.aichinese;

import static com.example.aichinese.support.ConcurrentTransactions.withEarlierRead;
import static com.example.aichinese.support.LearningApiTestClient.USER_PASSWORD;
import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.account.NotificationPreferences;
import com.example.aichinese.dto.auth.ChangePasswordRequest;
import com.example.aichinese.dto.auth.LoginRequest;
import com.example.aichinese.dto.auth.UpdateProfileRequest;
import com.example.aichinese.entity.EmailChangeToken;
import com.example.aichinese.entity.EmailConfirmationToken;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.repository.EmailChangeTokenRepository;
import com.example.aichinese.repository.EmailConfirmationTokenRepository;
import com.example.aichinese.repository.RefreshTokenRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.AuthService;
import com.example.aichinese.service.NotificationService;
import com.example.aichinese.service.SecureTokens;
import com.example.aichinese.service.account.AccountDeletionService;
import com.example.aichinese.service.account.AccountEmailService;
import com.example.aichinese.service.account.AccountService;
import com.example.aichinese.service.account.EmailConfirmationService;
import com.example.aichinese.service.admin.AdminUserService;
import com.example.aichinese.support.ApiIntegrationTest;

/** Real concurrent transactions: an earlier account edit must not restore unrelated, stale account fields. */
class UserConcurrentUpdateTests extends ApiIntegrationTest {

    private static final String NEW_PASSWORD = "UpdatedPassword456!";

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccountEmailService accountEmailService;

    @Autowired
    private EmailConfirmationService emailConfirmationService;

    @Autowired
    private EmailChangeTokenRepository emailChangeTokenRepository;

    @Autowired
    private EmailConfirmationTokenRepository emailConfirmationTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private AccountDeletionService accountDeletionService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private NotificationService notificationService;

    private User student;
    private SecurityUserDetails principal;

    @BeforeEach
    void createAccount() throws Exception {
        String email = uniqueEmail("concurrent-account");
        api.registerStudentAccount(email);
        student = user(email);
        principal = new SecurityUserDetails(student);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lateProfileOrPreferencesSaveCannotRestorePasswordAndRevokedSession(boolean preferences) throws Exception {
        String oldAccessToken = api.login(student.getEmail(), USER_PASSWORD);
        if (preferences) {
            makeAdmin();
        }
        assertThatThrownBy(() -> withEarlierAccountRead(
                preferences ? this::updatePreferences : this::updateProfile,
                () -> authService.changePassword(principal,
                        new ChangePasswordRequest(USER_PASSWORD, NEW_PASSWORD, NEW_PASSWORD),
                        "203.0.113.121", "Concurrent account test")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(401));

        User saved = user(student.getEmail());
        assertThat(passwordEncoder.matches(NEW_PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(saved.getSessionVersion()).isGreaterThan(student.getSessionVersion());
        mockMvc.perform(api.authGet("/api/v1/auth/me", oldAccessToken)).andExpect(status().isUnauthorized());
        assertThat(api.login(student.getEmail(), NEW_PASSWORD)).isNotBlank();
        if (preferences) {
            assertThat(saved.isNotifyReviewQueues()).isTrue();
        } else {
            assertThat(saved.getDisplayName()).isEqualTo(student.getDisplayName());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lateProfileOrPreferencesSaveCannotRunOnADisabledAccount(boolean preferences) throws Exception {
        if (preferences) {
            makeAdmin();
        }
        String oldAccessToken = api.login(student.getEmail(), USER_PASSWORD);
        assertThatThrownBy(() -> withEarlierAccountRead(
                preferences ? this::updatePreferences : this::updateProfile, () ->
                        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                            User current = userRepository.findById(student.getId()).orElseThrow();
                            current.setActive(false);
                        })))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(401));

        User saved = user(student.getEmail());
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getDisplayName()).isEqualTo(student.getDisplayName());
        assertThat(saved.isNotifyReviewQueues()).isTrue();
        mockMvc.perform(api.authGet("/api/v1/auth/me", oldAccessToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void lateProfileSaveKeepsPreferencesChangedInAnotherRequest() throws Exception {
        makeAdmin();
        withEarlierAccountRead(this::updateProfile, this::updatePreferences);

        User saved = user(student.getEmail());
        assertThat(saved.getDisplayName()).isEqualTo("Updated profile");
        assertThat(saved.getPreferredLanguage()).isEqualTo("ms");
        assertThat(saved.isNotifyReviewQueues()).isFalse();
    }

    @Test
    void lateWritesAfterDeletionKeepTheActorAnonymousAndLeaveNoNotification() {
        accountDeletionService.delete(principal, student.getEmail());
        auditLogService.safeRecord(student, "LATE_EVENT", "User", student.getId(), "Earlier account activity.");
        notificationService.safeCreateNotification(student, NotificationType.SYSTEM, "Earlier account activity",
                "A late notice for " + student.getEmail(), null, null, null);

        assertThat(jdbc.queryForObject(
                "select actor_email from audit_logs where actor_user_id = ? and action_type = 'LATE_EVENT'",
                String.class, student.getId())).isEqualTo("deleted-" + student.getId() + "@deleted.invalid");
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ?",
                Long.class, student.getId())).isZero();
    }

    @Test
    void aDelayedProfileSavePreservesAccountAnonymisation() {
        assertThatThrownBy(() -> withEarlierAccountRead(this::updateProfile,
                () -> accountDeletionService.delete(principal, student.getEmail())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(401));

        User deleted = userRepository.findById(student.getId()).orElseThrow();
        assertThat(deleted.getDisplayName()).isEqualTo("Deleted account");
        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    @Test
    void logoutWithAnEarlierAccountReadStillRevokesTheNewerPasswordSession() throws Exception {
        AtomicReference<String> passwordSession = new AtomicReference<>();
        withEarlierAccountRead(() -> authService.logout(principal), () -> {
            var changed = authService.changePassword(principal,
                    new ChangePasswordRequest(USER_PASSWORD, NEW_PASSWORD, NEW_PASSWORD),
                    "203.0.113.122", "Concurrent password change");
            passwordSession.set(changed.authResponse().token());
        });

        User saved = user(student.getEmail());
        mockMvc.perform(api.authGet("/api/v1/auth/me", passwordSession.get())).andExpect(status().isUnauthorized());
        assertThat(saved.getSessionVersion()).isEqualTo(student.getSessionVersion() + 2);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, saved.getPasswordHash())).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void anEarlierLinkReadCannotConsumeAUsedOrRetiredEmailToken(boolean signup, boolean used) throws Exception {
        String raw = createEmailLink(signup, uniqueEmail("first-address"));
        String hash = SecureTokens.sha256Hex(raw);
        assertThatThrownBy(() -> withEarlierRead(transactionManager, () -> readEmailLink(signup, hash),
                () -> confirmEmail(signup, raw), () -> {
            if (used) {
                confirmEmail(signup, raw);
                if (!signup) {
                    // The old destination becomes free again; replay must not revert the account to that address.
                    confirmEmail(false, createEmailLink(false, uniqueEmail("second-address")));
                }
            } else {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    if (signup) {
                        emailConfirmationTokenRepository.retireOpenTokensForUser(student.getId(), LocalDateTime.now());
                    } else {
                        emailChangeTokenRepository.retireOpenTokensForUser(student.getId(), LocalDateTime.now());
                    }
                });
            }
        })).isInstanceOfSatisfying(ResponseStatusException.class, error -> {
            assertThat(error.getStatusCode().value()).isEqualTo(400);
            assertThat(error.getReason()).isEqualTo("This confirmation link is invalid or has already been used.");
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anEarlierPasswordReadCannotAuthorizeAnotherPasswordOrEmailChange(boolean emailChange) {
        Runnable change = emailChange
                ? () -> accountEmailService.requestChange(principal, uniqueEmail("late-email"), USER_PASSWORD, "203.0.113.123")
                : () -> authService.changePassword(principal,
                        new ChangePasswordRequest(USER_PASSWORD, "AnotherPassword789!", "AnotherPassword789!"),
                        "203.0.113.123", "Earlier password read");

        assertThatThrownBy(() -> withEarlierAccountRead(change, () -> authService.changePassword(principal,
                new ChangePasswordRequest(USER_PASSWORD, NEW_PASSWORD, NEW_PASSWORD),
                "203.0.113.124", "Concurrent password change")))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(400);
                    assertThat(error.getReason()).isEqualTo("Current password is incorrect.");
                });
        assertThat(passwordEncoder.matches(NEW_PASSWORD, user(student.getEmail()).getPasswordHash())).isTrue();
        assertThat(emailChangeTokenRepository.findPendingEmail(student.getId(), LocalDateTime.now()))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anEarlierLinkReadCannotConfirmAfterAccountDeactivation(boolean signup) {
        String raw = createEmailLink(signup, uniqueEmail("disabled-address"));
        String hash = SecureTokens.sha256Hex(raw);
        assertThatThrownBy(() -> withEarlierRead(transactionManager, () -> readEmailLink(signup, hash),
                () -> confirmEmail(signup, raw), () ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                        userRepository.findById(student.getId()).orElseThrow().setActive(false))))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(400);
                    assertThat(error.getReason()).isEqualTo("This confirmation link is invalid or has already been used.");
                });
        User saved = userRepository.findById(student.getId()).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo(student.getEmail());
        assertThat(saved.getEmailVerifiedAt()).isNull();
    }

    @Test
    void loginWithAnEarlierPasswordReadCannotCreateASessionAfterPasswordChange() {
        assertThatThrownBy(() -> withEarlierAccountRead(() -> authService.login(
                new LoginRequest(student.getEmail(), USER_PASSWORD, true), "203.0.113.125", "Delayed login"),
                () -> authService.changePassword(principal,
                        new ChangePasswordRequest(USER_PASSWORD, NEW_PASSWORD, NEW_PASSWORD),
                        "203.0.113.126", "Concurrent password change")))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(401);
                    assertThat(error.getReason()).isEqualTo("Invalid email or password.");
                });
    }

    @Test
    void rotationWithAnEarlierTokenReadCannotRestoreASignedOutSession() {
        String raw = authService.login(new LoginRequest(student.getEmail(), USER_PASSWORD, true),
                "203.0.113.127", "Refresh test").refreshToken().rawToken();
        String hash = SecureTokens.sha256Hex(raw);
        Long tokenId = jdbc.queryForObject("select id from refresh_tokens where token_hash = ?", Long.class, hash);
        assertThatThrownBy(() -> withEarlierRead(transactionManager, () -> refreshTokenRepository.findById(tokenId).orElseThrow(),
                () -> authService.refreshSession(raw, "203.0.113.127", "Delayed refresh"),
                () -> authService.logout(principal)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(401);
                    assertThat(error.getReason()).isEqualTo("Refresh session is invalid or expired.");
                });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emailResendUsesTheLatestSendHistoryAfterWaitingForTheAccount(boolean dailyLimit) {
        if (dailyLimit) {
            for (int count = 0; count < 3; count++) createEmailLink(true, student.getEmail());
        }
        ageConfirmationLinks();
        assertThatThrownBy(() -> withEarlierAccountRead(() -> emailConfirmationService.resend(principal), () -> {
            emailConfirmationService.resend(principal);
            if (dailyLimit) ageConfirmationLinks();
        })).isInstanceOfSatisfying(ResponseStatusException.class, error -> {
            assertThat(error.getStatusCode().value()).isEqualTo(429);
            assertThat(error.getReason()).isEqualTo(dailyLimit
                    ? "You asked for several links today. Please try again tomorrow."
                    : "We just sent a link. Please wait a minute before asking for another one.");
        });
        assertThat(jdbc.queryForObject("select count(*) from email_confirmation_tokens where user_id = ?",
                Long.class, student.getId())).isEqualTo(dailyLimit ? 5L : 2L);
    }

    @Test
    void resendingAnEmailChangeRespectsACancellationThatCommittedWhileItWaited() {
        createEmailLink(false, uniqueEmail("cancelled-destination"));
        assertThatThrownBy(() -> withEarlierAccountRead(
                () -> accountEmailService.resendLink(principal, "203.0.113.130"),
                () -> accountEmailService.cancel(principal)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                        assertThat(error.getStatusCode().value()).isEqualTo(400));
        assertThat(accountEmailService.pendingEmail(student.getId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void adminStatusChangeUsesCurrentStateInsteadOfSkippingAnEarlierMatchingState(boolean active) throws Exception {
        String oldAccessToken = api.login(student.getEmail(), USER_PASSWORD);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                userRepository.findById(student.getId()).orElseThrow().setActive(active));
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        withEarlierAccountRead(() -> changeAccountStatus(admin, student.getId(), active),
                () -> changeAccountStatus(admin, student.getId(), !active));

        assertThat(userRepository.findById(student.getId()).orElseThrow().isActive()).isEqualTo(active);
        mockMvc.perform(api.authGet("/api/v1/auth/me", oldAccessToken)).andExpect(status().isUnauthorized());
    }

    /**
     * A deletion that read the account before an admin deactivated it decides on the account it finds once locked: the
     * request's session no longer holds, so nothing is deleted.
     */
    @Test
    void anAccountDeactivatedWhileItsDeletionWaitedIsNotDeleted() {
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        assertThatThrownBy(() -> withEarlierAccountRead(
                () -> accountDeletionService.delete(principal, student.getEmail()),
                () -> changeAccountStatus(admin, student.getId(), false)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(401);
                    assertThat(error.getReason()).isEqualTo(FriendlyErrorMessageResolver.sessionExpiredMessage());
                });
        User kept = userRepository.findById(student.getId()).orElseThrow();
        assertThat(kept.isActive()).isFalse();
        assertThat(kept.getDeletedAt()).isNull();
        assertThat(kept.getEmail()).isEqualTo(student.getEmail());
    }

    @Test
    void adminCannotReactivateAnAccountDeletedAfterTheEarlierRead() {
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        assertThatThrownBy(() -> withEarlierAccountRead(
                () -> adminUserService.reactivate(admin, student.getId(), "Earlier account read"),
                () -> accountDeletionService.delete(principal, student.getEmail())))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(400);
                    assertThat(error.getReason()).isEqualTo("A deleted account can't be reactivated.");
                });
        User deleted = userRepository.findById(student.getId()).orElseThrow();
        assertThat(deleted.isActive()).isFalse();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getEmail()).isEqualTo("deleted-" + student.getId() + "@deleted.invalid");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anAdminWhoseSessionEndedCannotChangeAnotherAccountAfterAnEarlierRead(boolean deactivated) {
        SecurityUserDetails admin = newAdmin();
        SecurityUserDetails otherAdmin = newAdmin();
        assertThatThrownBy(() -> withEarlierRead(transactionManager,
                () -> userRepository.findWithRolesById(admin.getUserId()).orElseThrow(),
                () -> adminUserService.deactivate(admin, student.getId(), "Delayed admin action"),
                () -> {
                    if (deactivated) adminUserService.deactivate(otherAdmin, admin.getUserId(), "Account unavailable");
                    else authService.logout(admin);
                })).isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(401);
                    assertThat(error.getReason()).isEqualTo("Your session has expired. Please sign in again to continue.");
                });
        assertThat(userRepository.findById(student.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void twoAdminsDeactivatingEachOtherHaveOneWinnerAndNoDeadlock() throws Exception {
        SecurityUserDetails first = newAdmin();
        SecurityUserDetails second = newAdmin();
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> deactivateAfterStart(start, first, second.getUserId()));
            var secondResult = executor.submit(() -> deactivateAfterStart(start, second, first.getUserId()));
            assertThat(firstResult.get(10, TimeUnit.SECONDS)).isNotEqualTo(secondResult.get(10, TimeUnit.SECONDS));
        }
        User firstSaved = userRepository.findById(first.getUserId()).orElseThrow();
        User secondSaved = userRepository.findById(second.getUserId()).orElseThrow();
        assertThat(firstSaved.isActive()).isNotEqualTo(secondSaved.isActive());
        assertThat(firstSaved.getSessionVersion() + secondSaved.getSessionVersion()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs "
                        + "where action_type = 'ADMIN_USER_DEACTIVATE' and target_id in (?, ?)",
                Long.class, first.getUserId(), second.getUserId())).isEqualTo(1L);
    }

    private boolean deactivateAfterStart(CyclicBarrier start, SecurityUserDetails actor, Long targetId) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            adminUserService.deactivate(actor, targetId, "Concurrent admin decision");
            return true;
        } catch (ResponseStatusException error) {
            assertThat(error.getStatusCode().value()).isEqualTo(401);
            return false;
        }
    }

    private SecurityUserDetails newAdmin() {
        User seededAdmin = user(ADMIN_EMAIL);
        User admin = new User();
        admin.setEmail(uniqueEmail("concurrent-admin"));
        admin.setDisplayName("Concurrency test admin");
        admin.setPasswordHash(seededAdmin.getPasswordHash());
        admin.setRoles(new HashSet<>(seededAdmin.getRoles()));
        return new SecurityUserDetails(userRepository.save(admin));
    }

    private void changeAccountStatus(SecurityUserDetails admin, Long userId, boolean active) {
        if (active) adminUserService.reactivate(admin, userId, "Concurrent reactivation");
        else adminUserService.deactivate(admin, userId, "Concurrent deactivation");
    }

    private void ageConfirmationLinks() {
        jdbc.update("update email_confirmation_tokens set created_at = ? where user_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(2)), student.getId());
    }

    private String createEmailLink(boolean signup, String email) {
        String raw = SecureTokens.newHexToken();
        if (signup) {
            EmailConfirmationToken token = new EmailConfirmationToken();
            token.initialize(student, SecureTokens.sha256Hex(raw), LocalDateTime.now().plusHours(1));
            token.setEmail(student.getEmail());
            emailConfirmationTokenRepository.save(token);
        } else {
            EmailChangeToken token = new EmailChangeToken();
            token.initialize(student, SecureTokens.sha256Hex(raw), LocalDateTime.now().plusHours(1));
            token.setNewEmail(email);
            emailChangeTokenRepository.save(token);
        }
        return raw;
    }

    private void confirmEmail(boolean signup, String raw) {
        if (signup) emailConfirmationService.confirm(raw);
        else accountEmailService.confirm(raw);
    }

    private void readEmailLink(boolean signup, String hash) {
        if (signup) {
            emailConfirmationTokenRepository.findByTokenHash(hash).orElseThrow();
        } else {
            emailChangeTokenRepository.findByTokenHash(hash).orElseThrow();
        }
    }

    private void updateProfile() {
        authService.updateProfile(principal, new UpdateProfileRequest("Updated profile", "ms"));
    }

    /** The admins' "Daily summary" e-mail: learners have no e-mail switch. */
    private void updatePreferences() {
        accountService.updatePreferences(principal, new NotificationPreferences(false));
    }

    /** The account becomes an admin's, whose Account page has the e-mail switch. */
    private void makeAdmin() {
        jdbc.update("update user_roles set role_id = (select id from roles where name = 'ADMIN') where user_id = ?",
                student.getId());
        student = user(student.getEmail());
        principal = new SecurityUserDetails(student);
    }

    /** Pause an ordinary managed-entity read before another request commits, then let the first request save. */
    private void withEarlierAccountRead(Runnable delayedSave, Runnable concurrentSave) throws Exception {
        withEarlierRead(transactionManager, () -> userRepository.findWithRolesById(student.getId()).orElseThrow(),
                delayedSave, concurrentSave);
    }
}
