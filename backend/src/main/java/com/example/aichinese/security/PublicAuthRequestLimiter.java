package com.example.aichinese.security;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.time.Duration;
import java.time.InstantSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PublicAuthRequestLimiter {

    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_REQUEST_RECORDS = 10_000;
    private static final int MAX_SUBJECT_KEY_LENGTH = 320;
    private static final int MAX_IP_KEY_LENGTH = 64;

    /** Refresh requests get three times the registration allowance per IP (see {@link #checkRefreshAllowed}). */
    private static final int REFRESH_REQUESTS_PER_REGISTER_REQUEST = 3;

    private final FixedWindowCounter requests;
    private final int maxRegisterRequests;
    private final int maxRefreshRequests;
    private final int maxForgotPasswordRequests;

    @Autowired
    public PublicAuthRequestLimiter(
            @Value("${app.security.public-auth-rate-limit.register-requests:100}") int maxRegisterRequests,
            @Value("${app.security.public-auth-rate-limit.forgot-password-requests:10}") int maxForgotPasswordRequests) {
        this(InstantSource.system(), maxRegisterRequests, maxForgotPasswordRequests, MAX_TRACKED_REQUEST_RECORDS);
    }

    PublicAuthRequestLimiter(
            InstantSource clock,
            int maxRegisterRequests,
            int maxForgotPasswordRequests,
            int maxTrackedRequestRecords) {
        if (maxRegisterRequests < 1 || maxForgotPasswordRequests < 1 || maxTrackedRequestRecords < 1) {
            throw new IllegalArgumentException("Rate limit thresholds must be positive");
        }
        this.maxRegisterRequests = maxRegisterRequests;
        // Saturates instead of overflowing: a very large registration allowance (a way to switch sign-up throttling
        // off) must not make every refresh fail with an ArithmeticException.
        this.maxRefreshRequests = (int) Math.min(Integer.MAX_VALUE,
                (long) maxRegisterRequests * REFRESH_REQUESTS_PER_REGISTER_REQUEST);
        this.maxForgotPasswordRequests = maxForgotPasswordRequests;
        this.requests = new FixedWindowCounter(clock, WINDOW, maxTrackedRequestRecords);
    }

    public void checkRegisterAllowed(String clientIp) {
        checkAllowed("REGISTER_IP", "", clientIp, maxRegisterRequests);
    }

    /**
     * Per-IP sign-in cap (3x registration allowance, matching refresh) so shared campus NATs sign in freely while
     * unauthenticated BCrypt CPU-exhaustion and password-spraying floods across distinct emails are bounded.
     */
    public void checkLoginAllowed(String clientIp) {
        checkAllowed("LOGIN_IP", "", clientIp, maxRefreshRequests);
    }

    /**
     * Refresh requests get a generous per-IP bucket (3x registration allowance) so shared campus NATs and multi-tab
     * browsers refresh freely while unauthenticated cookie-probing floods are still capped.
     */
    public void checkRefreshAllowed(String clientIp) {
        checkAllowed("REFRESH_IP", "", clientIp, maxRefreshRequests);
    }

    public void checkForgotPasswordAllowed(String email, String clientIp) {
        checkDualAllowed("FORGOT_PASSWORD_IP", "FORGOT_PASSWORD_EMAIL", email, clientIp, maxForgotPasswordRequests);
    }

    /** Reset-password link checks and submissions get their own per-IP bucket capped at the forgot-password limit. */
    public void checkResetPasswordAllowed(String clientIp) {
        checkAllowed("RESET_PASSWORD_IP", "", clientIp, maxForgotPasswordRequests);
    }

    /** Sign-up email confirmation aligns with the registration per-IP allowance so shared campus NATs do not starve. */
    public void checkSignupConfirmationAllowed(String clientIp) {
        checkAllowed("SIGNUP_CONFIRM_IP", "", clientIp, maxRegisterRequests);
    }

    /** E-mail change confirmation links get their own per-IP bucket, separate from password reset. */
    public void checkEmailConfirmationAllowed(String clientIp) {
        checkAllowed("EMAIL_CONFIRM_IP", "", clientIp, maxForgotPasswordRequests);
    }

    /** Admin invitation link checks and acceptances get their own per-IP bucket, separate from password reset. */
    public void checkAdminInviteLinkAllowed(String clientIp) {
        checkAllowed("ADMIN_INVITE_LINK_IP", "", clientIp, maxForgotPasswordRequests);
    }

    /**
     * An e-mail change link sent to a new address: per IP and per new address, capped at the forgot-password limit,
     * so a signed-in account cannot flood any address with confirmation e-mails.
     */
    public void checkEmailChangeAllowed(String newEmail, String clientIp) {
        checkDualAllowed("EMAIL_CHANGE_IP", "EMAIL_CHANGE_EMAIL", newEmail, clientIp, maxForgotPasswordRequests);
    }

    /** The Help page message form: per IP and per e-mail address, capped at the forgot-password limit. */
    public void checkSupportMessageAllowed(String email, String clientIp) {
        checkDualAllowed("SUPPORT_MESSAGE_IP", "SUPPORT_MESSAGE_EMAIL", email, clientIp, maxForgotPasswordRequests);
    }

    /** Authenticated learner support submissions (with optional 5 MB screenshot): bounded per account. */
    public void checkLearnerSupportAllowed(Long userId) {
        checkAllowed("LEARNER_SUPPORT_USER", userId == null ? "" : userId.toString(), "", maxForgotPasswordRequests);
    }

    int trackedRequestCount() {
        return Math.toIntExact(requests.cleanUpAndEstimatedSize());
    }

    /**
     * Pre-checks the subject bucket before incrementing the IP bucket so a target email already flooded from another
     * address does not burn a legitimate caller's shared-IP budget.
     */
    private void checkDualAllowed(
            String ipAction,
            String subjectAction,
            String subject,
            String clientIp,
            int maxRequests) {
        String subjectKey = key(subjectAction, subject, "");
        if (requests.isExhausted(subjectKey, maxRequests)) {
            throw tooManyRequests();
        }
        checkAllowed(ipAction, "", clientIp, maxRequests);
        if (requests.increment(subjectKey) > maxRequests) {
            throw tooManyRequests();
        }
    }

    /**
     * Counts the request in its window: the n-th request of a window is refused when n is above the limit, and a
     * refused request leaves the window where it started. The reason is replaced by the general 429 sentence before
     * it reaches the client ({@code FriendlyErrorMessageResolver}).
     */
    private void checkAllowed(String action, String subject, String clientIp, int maxRequests) {
        if (requests.increment(key(action, subject, clientIp)) > maxRequests) {
            throw tooManyRequests();
        }
    }

    private static ResponseStatusException tooManyRequests() {
        return new ResponseStatusException(TOO_MANY_REQUESTS, "Too many requests.");
    }

    private static String key(String action, String subject, String clientIp) {
        String normalizedSubject = FixedWindowCounter.normalizeKeyPart(subject, MAX_SUBJECT_KEY_LENGTH, true);
        String normalizedIp = FixedWindowCounter.normalizeKeyPart(clientIp, MAX_IP_KEY_LENGTH, false);
        return action + "|" + normalizedSubject + "|" + normalizedIp;
    }
}
