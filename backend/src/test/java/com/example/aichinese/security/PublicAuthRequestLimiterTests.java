package com.example.aichinese.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.time.Duration;
import java.time.InstantSource;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit coverage for {@code PublicAuthRequestLimiter}. It pins the separate quotas of sign-up (per IP) and of
 * forgot-password and e-mail change links (per IP and, separately, per e-mail address), the window, and the 429
 * refusal once a quota is used up.
 */
class PublicAuthRequestLimiterTests {

    private static final String IP = "203.0.113.7";
    private static final String USER_EMAIL = "user@example.com";
    private static final String OTHER_EMAIL = "other@example.com";
    private static final String TARGET_EMAIL = "target@example.com";
    private static final String TARGET_IP_1 = "203.0.113.10";
    private static final String TARGET_IP_2 = "203.0.113.11";
    private static final String TARGET_IP_3 = "203.0.113.12";
    private static final String LONG_EMAIL_PREFIX = "a".repeat(400);
    private static final String LONG_IP_PREFIX = "1".repeat(100);
    private static final int REGISTER_THRESHOLD = 3;
    private static final int FORGOT_PASSWORD_THRESHOLD = 2;
    private static final int SINGLE_REQUEST_THRESHOLD = 1;
    private static final int SMALL_CAPACITY = 3;
    private static final int DEFAULT_CAPACITY = 10;
    private static final int HIGH_THRESHOLD = 100;
    private static final int SPRAY_ATTEMPTS = 20;

    @Test
    void registerRequestsAreRejectedAfterThreshold() {
        PublicAuthRequestLimiter limiter = limiter(REGISTER_THRESHOLD, REGISTER_THRESHOLD, DEFAULT_CAPACITY);

        repeat(REGISTER_THRESHOLD, () -> limiter.checkRegisterAllowed(IP));

        assertTooManyRequests(() -> limiter.checkRegisterAllowed(IP));
    }

    /** Refreshes and sign-ins each get three times the sign-up allowance per IP, in isolated buckets. */
    @Test
    void refreshAndLoginRequestsAreRejectedAfterThreeTimesTheRegisterThreshold() {
        PublicAuthRequestLimiter limiter = limiter(REGISTER_THRESHOLD, FORGOT_PASSWORD_THRESHOLD, DEFAULT_CAPACITY);

        repeat(REGISTER_THRESHOLD * 3, () -> limiter.checkRefreshAllowed(IP));
        assertTooManyRequests(() -> limiter.checkRefreshAllowed(IP));

        repeat(REGISTER_THRESHOLD * 3, () -> limiter.checkLoginAllowed(IP));
        assertTooManyRequests(() -> limiter.checkLoginAllowed(IP));

        // Sign-up keeps its own budget on the same address.
        limiter.checkRegisterAllowed(IP);
    }

    /** A registration allowance too large to triple (sign-up throttling switched off) must not break refreshes. */
    @Test
    void hugeRegisterThresholdDoesNotOverflowTheRefreshLimit() {
        PublicAuthRequestLimiter limiter = limiter(Integer.MAX_VALUE, FORGOT_PASSWORD_THRESHOLD, DEFAULT_CAPACITY);

        repeat(SPRAY_ATTEMPTS, () -> limiter.checkRefreshAllowed(IP));
        limiter.checkRegisterAllowed(IP);
    }

    @Test
    void forgotPasswordRequestsAreRejectedByIpAndEmailThresholds() {
        PublicAuthRequestLimiter limiter = limiter(REGISTER_THRESHOLD, FORGOT_PASSWORD_THRESHOLD, DEFAULT_CAPACITY);

        repeat(FORGOT_PASSWORD_THRESHOLD, () -> limiter.checkForgotPasswordAllowed(USER_EMAIL, IP));

        assertTooManyRequests(() -> limiter.checkForgotPasswordAllowed(OTHER_EMAIL, IP));

        PublicAuthRequestLimiter emailLimiter =
                limiter(REGISTER_THRESHOLD, FORGOT_PASSWORD_THRESHOLD, DEFAULT_CAPACITY);
        emailLimiter.checkForgotPasswordAllowed(TARGET_EMAIL, TARGET_IP_1);
        emailLimiter.checkForgotPasswordAllowed(TARGET_EMAIL, TARGET_IP_2);

        assertTooManyRequests(() -> emailLimiter.checkForgotPasswordAllowed(TARGET_EMAIL, TARGET_IP_3));
    }

    /** E-mail change links: one sender cannot mail many addresses, and many senders cannot flood one address. */
    @Test
    void emailChangeLinksAreLimitedPerSenderAndPerRecipient() {
        PublicAuthRequestLimiter limiter = limiter(REGISTER_THRESHOLD, FORGOT_PASSWORD_THRESHOLD, DEFAULT_CAPACITY);
        limiter.checkEmailChangeAllowed(USER_EMAIL, IP);
        limiter.checkEmailChangeAllowed(OTHER_EMAIL, IP);
        assertTooManyRequests(() -> limiter.checkEmailChangeAllowed(TARGET_EMAIL, IP));

        PublicAuthRequestLimiter recipientLimiter =
                limiter(REGISTER_THRESHOLD, FORGOT_PASSWORD_THRESHOLD, DEFAULT_CAPACITY);
        recipientLimiter.checkEmailChangeAllowed(TARGET_EMAIL, TARGET_IP_1);
        recipientLimiter.checkEmailChangeAllowed(TARGET_EMAIL.toUpperCase(), TARGET_IP_2);
        assertTooManyRequests(() -> recipientLimiter.checkEmailChangeAllowed(TARGET_EMAIL, TARGET_IP_3));
        // The forgot-password budget of the address is its own.
        recipientLimiter.checkForgotPasswordAllowed(TARGET_EMAIL, TARGET_IP_3);
    }

    @Test
    void requestBudgetResetsExactlyAtWindowBoundary() {
        MutableInstantSource clock = new MutableInstantSource();
        PublicAuthRequestLimiter limiter = new PublicAuthRequestLimiter(
                clock,
                SINGLE_REQUEST_THRESHOLD,
                SINGLE_REQUEST_THRESHOLD,
                DEFAULT_CAPACITY);

        limiter.checkRegisterAllowed(IP);
        assertTooManyRequests(() -> limiter.checkRegisterAllowed(IP));

        clock.advance(PublicAuthRequestLimiter.WINDOW.minusSeconds(1));
        assertTooManyRequests(() -> limiter.checkRegisterAllowed(IP));

        clock.advance(Duration.ofSeconds(1));
        limiter.checkRegisterAllowed(IP);
        assertTooManyRequests(() -> limiter.checkRegisterAllowed(IP));
    }

    @Test
    void remainingAuthLinkAndSupportLimitersUseIsolatedBuckets() {
        PublicAuthRequestLimiter limiter = limiter(REGISTER_THRESHOLD, FORGOT_PASSWORD_THRESHOLD, DEFAULT_CAPACITY);

        // Reset-password, email-confirmation, and admin-invite link checks each cap at forgot-password threshold independently.
        repeat(FORGOT_PASSWORD_THRESHOLD, () -> limiter.checkResetPasswordAllowed(IP));
        assertTooManyRequests(() -> limiter.checkResetPasswordAllowed(IP));

        repeat(FORGOT_PASSWORD_THRESHOLD, () -> limiter.checkEmailConfirmationAllowed(IP));
        assertTooManyRequests(() -> limiter.checkEmailConfirmationAllowed(IP));

        repeat(FORGOT_PASSWORD_THRESHOLD, () -> limiter.checkAdminInviteLinkAllowed(IP));
        assertTooManyRequests(() -> limiter.checkAdminInviteLinkAllowed(IP));

        // Signup confirmation aligns with maxRegisterRequests rather than maxForgotPasswordRequests.
        repeat(REGISTER_THRESHOLD, () -> limiter.checkSignupConfirmationAllowed(IP));
        assertTooManyRequests(() -> limiter.checkSignupConfirmationAllowed(IP));

        // Visitor support messages enforce both per-IP and per-email budgets.
        repeat(FORGOT_PASSWORD_THRESHOLD, () -> limiter.checkSupportMessageAllowed(USER_EMAIL, TARGET_IP_1));
        assertTooManyRequests(() -> limiter.checkSupportMessageAllowed(OTHER_EMAIL, TARGET_IP_1));
        PublicAuthRequestLimiter supportEmailLimiter = limiter(REGISTER_THRESHOLD, FORGOT_PASSWORD_THRESHOLD, DEFAULT_CAPACITY);
        supportEmailLimiter.checkSupportMessageAllowed(TARGET_EMAIL, TARGET_IP_1);
        supportEmailLimiter.checkSupportMessageAllowed(TARGET_EMAIL, TARGET_IP_2);
        assertTooManyRequests(() -> supportEmailLimiter.checkSupportMessageAllowed(TARGET_EMAIL, TARGET_IP_3));

        // Authenticated learner support isolates each userId and handles null gracefully.
        repeat(FORGOT_PASSWORD_THRESHOLD, () -> limiter.checkLearnerSupportAllowed(42L));
        assertTooManyRequests(() -> limiter.checkLearnerSupportAllowed(42L));
        limiter.checkLearnerSupportAllowed(43L);
        repeat(FORGOT_PASSWORD_THRESHOLD, () -> limiter.checkLearnerSupportAllowed(null));
        assertTooManyRequests(() -> limiter.checkLearnerSupportAllowed(null));

        // AI report submission uses its own isolated per-userId bucket.
        repeat(FORGOT_PASSWORD_THRESHOLD, () -> limiter.checkAiReportAllowed(42L));
        assertTooManyRequests(() -> limiter.checkAiReportAllowed(42L));
        limiter.checkAiReportAllowed(43L);
        repeat(FORGOT_PASSWORD_THRESHOLD, () -> limiter.checkAiReportAllowed(null));
        assertTooManyRequests(() -> limiter.checkAiReportAllowed(null));
    }

    @Test
    void constructorRejectsNonPositiveThresholdsOrCapacity() {
        assertThatThrownBy(() -> limiter(0, FORGOT_PASSWORD_THRESHOLD, DEFAULT_CAPACITY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter(REGISTER_THRESHOLD, 0, DEFAULT_CAPACITY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter(REGISTER_THRESHOLD, FORGOT_PASSWORD_THRESHOLD, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trackedRequestRecordsStayWithinCapacity() {
        PublicAuthRequestLimiter limiter = limiter(HIGH_THRESHOLD, HIGH_THRESHOLD, SMALL_CAPACITY);

        for (int i = 0; i < SPRAY_ATTEMPTS; i++) {
            limiter.checkRegisterAllowed("203.0.113." + i);
        }

        assertThat(limiter.trackedRequestCount()).isLessThanOrEqualTo(SMALL_CAPACITY);
    }

    @Test
    void exhaustedEmailBucketDoesNotBurnLegitimateCallerIpBudget() {
        PublicAuthRequestLimiter limiter = limiter(REGISTER_THRESHOLD, FORGOT_PASSWORD_THRESHOLD, DEFAULT_CAPACITY);
        limiter.checkForgotPasswordAllowed(TARGET_EMAIL, TARGET_IP_1);
        limiter.checkForgotPasswordAllowed(TARGET_EMAIL, TARGET_IP_2);

        // Victim IP trying the already-exhausted email is rejected without losing its own IP budget for other emails.
        assertTooManyRequests(() -> limiter.checkForgotPasswordAllowed(TARGET_EMAIL, TARGET_IP_3));
        assertTooManyRequests(() -> limiter.checkForgotPasswordAllowed(TARGET_EMAIL, TARGET_IP_3));
        limiter.checkForgotPasswordAllowed(USER_EMAIL, TARGET_IP_3);
        limiter.checkForgotPasswordAllowed(OTHER_EMAIL, TARGET_IP_3);
    }

    @Test
    void oversizedRequestKeysAreNormalisedToBoundedEntries() {
        PublicAuthRequestLimiter limiter = limiter(HIGH_THRESHOLD, HIGH_THRESHOLD, DEFAULT_CAPACITY);

        limiter.checkForgotPasswordAllowed(LONG_EMAIL_PREFIX + "first@example.com", LONG_IP_PREFIX + "1");
        limiter.checkForgotPasswordAllowed(LONG_EMAIL_PREFIX + "second@example.com", LONG_IP_PREFIX + "2");

        assertThat(limiter.trackedRequestCount()).isEqualTo(2);
    }

    private static PublicAuthRequestLimiter limiter(
            int maxRegisterRequests,
            int maxForgotPasswordRequests,
            int capacity) {
        return new PublicAuthRequestLimiter(
                InstantSource.system(), maxRegisterRequests, maxForgotPasswordRequests, capacity);
    }

    private static void assertTooManyRequests(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(TOO_MANY_REQUESTS));
    }

    private static void repeat(int times, Runnable action) {
        for (int i = 0; i < times; i++) {
            action.run();
        }
    }
}
