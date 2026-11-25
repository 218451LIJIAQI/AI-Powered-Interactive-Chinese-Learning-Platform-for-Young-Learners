package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.dto.auth.AuthResponse;
import com.example.aichinese.dto.auth.AuthSessionResponse;
import com.example.aichinese.dto.auth.ChangePasswordRequest;
import com.example.aichinese.dto.auth.CurrentUserResponse;
import com.example.aichinese.dto.auth.ForgotPasswordRequest;
import com.example.aichinese.dto.auth.LinkTokenResponse;
import com.example.aichinese.dto.auth.LoginRequest;
import com.example.aichinese.dto.auth.RegisterStudentRequest;
import com.example.aichinese.dto.auth.RefreshTokenIssue;
import com.example.aichinese.dto.auth.ResetPasswordRequest;
import com.example.aichinese.dto.auth.SessionResponse;
import com.example.aichinese.dto.auth.UpdateProfileRequest;
import com.example.aichinese.entity.PasswordResetToken;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.exception.RetryLaterException;
import com.example.aichinese.repository.AdminInviteRepository;
import com.example.aichinese.repository.EmailChangeTokenRepository;
import com.example.aichinese.repository.PasswordResetTokenRepository;
import com.example.aichinese.repository.RoleRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.JwtService;
import com.example.aichinese.security.LoginAttemptLimiter;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.account.AccountGuards;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.service.account.EmailConfirmationService;
import com.example.aichinese.util.AfterCommit;
import com.example.aichinese.util.LanguageCodes;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String ACTION_ACCOUNT_CREATED = "ACCOUNT_CREATED";
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(60);
    private static final String GENERIC_RESET_TOKEN_INVALID_MESSAGE =
            "Reset token is invalid or has already been used.";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailChangeTokenRepository emailChangeTokenRepository;
    private final AdminInviteRepository adminInviteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final AuditLogService auditLogService;
    private final TransactionalEmailDispatcher transactionalEmailDispatcher;
    private final EmailProperties emailProperties;
    private final EmailConfirmationService emailConfirmationService;
    private final CurrentAccount currentAccount;
    private final EntityManager entityManager;

    /** Lazily encoded once with the active PasswordEncoder so unknown-email logins match real BCrypt timing. */
    private volatile String dummyPasswordHash;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LinkTokenResponse registerStudent(RegisterStudentRequest request) {
        validatePasswords(request.password(), request.confirmPassword());
        String email = normalizeEmail(request.email());
        AccountGuards.requireUnregisteredEmail(userRepository, email);
        // Pre-encode before acquiring row locks so BCrypt(12) never holds write locks on admin_invites or users.
        String encodedPassword = passwordEncoder.encode(request.password());
        // Cancel any open admin invite before claiming the address in users (global lock order: admin_invites -> users).
        adminInviteRepository.cancelOpenForEmail(email, LocalDateTime.now());
        User savedUser = createAccountWithEncodedPassword(request.fullName(), email, encodedPassword,
                LanguageCodes.normalizeOrDefault(request.preferredLanguage()), RoleName.STUDENT);

        StudentProfile profile = new StudentProfile();
        profile.setUser(savedUser);
        studentProfileRepository.save(profile);
        // The learner can learn at once; Home asks them to confirm the address with the link sent now.
        emailConfirmationService.sendFirstLink(savedUser);
        auditUser(savedUser, ACTION_ACCOUNT_CREATED, "User created a learner account.");

        return new LinkTokenResponse(savedUser.getEmail());
    }

    /**
     * An active account of one role for a lower-case address not registered yet: sign-up, an accepted admin invite and
     * the seeded accounts.
     */
    @Transactional
    public User createAccount(String fullName, String email, String password, String language, RoleName roleName) {
        return createAccountWithEncodedPassword(fullName, email, passwordEncoder.encode(password), language, roleName);
    }

    /** Same as {@link #createAccount} when the caller pre-encoded the password before taking row locks. */
    @Transactional
    public User createAccountWithEncodedPassword(
            String fullName, String email, String encodedPassword, String language, RoleName roleName) {
        String normalizedEmail = normalizeEmail(email);
        AccountGuards.requireUnregisteredEmail(userRepository, normalizedEmail);

        User user = new User();
        user.setDisplayName(fullName);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(encodedPassword);
        user.setPreferredLanguage(language);
        user.setActive(true);
        user.setRoles(new HashSet<>(Set.of(roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(roleName + " role is not available.")))));
        return userRepository.save(user);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuthSessionResponse login(LoginRequest request, String clientIp) {
        String email = normalizeEmail(request.email());
        requireNotLockedOut(email, clientIp);

        // Verify BCrypt before locking the account row so repeated wrong passwords cannot hold write locks.
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), timingParityHash());
            throw invalidCredentials(email, clientIp);
        }
        String checkedHash = user.getPasswordHash();
        if (!passwordEncoder.matches(request.password(), checkedHash)) {
            throw invalidCredentials(email, clientIp);
        }

        // Lock and refresh before checking lockout/availability and issuing a session, so concurrent lockout, email,
        // password or status changes committed while BCrypt ran are observed under the write lock.
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        requireNotLockedOut(email, clientIp);
        if (!email.equalsIgnoreCase(user.getEmail())) {
            throw invalidCredentials(email, clientIp);
        }
        if (!checkedHash.equals(user.getPasswordHash())
                && !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials(email, clientIp);
        }
        // FRAUTH05: reveal "unavailable" only after the password matched, without counting a failed attempt.
        if (!AccountGuards.isAvailable(user)) {
            throw new ResponseStatusException(FORBIDDEN, AccountGuards.ACCOUNT_UNAVAILABLE_MESSAGE);
        }

        RefreshTokenIssue refreshToken = refreshTokenService.issue(user, request.keepSignedIn());
        AfterCommit.run(() -> loginAttemptLimiter.recordSuccess(email, clientIp));
        return sessionWithAccessToken(user, refreshToken);
    }

    private String timingParityHash() {
        String cached = dummyPasswordHash;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (dummyPasswordHash == null) {
                dummyPasswordHash = passwordEncoder.encode("timing-parity-placeholder");
            }
            return dummyPasswordHash;
        }
    }

    public AuthSessionResponse refreshSession(String rawRefreshToken) {
        RefreshTokenService.RefreshSession refreshed = refreshTokenService.rotate(rawRefreshToken);
        return sessionWithAccessToken(refreshed.user(), refreshed.refreshToken());
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(SecurityUserDetails currentUser) {
        return currentUserResponse(currentAccount.findWithRolesById(currentUser.getUserId()));
    }

    private CurrentUserResponse currentUserResponse(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPreferredLanguage(),
                user.orderedRoleNames().map(RoleName::name).toList(),
                studentProfileRepository.findSummaryByUser(user).orElse(null));
    }

    /**
     * "Sign out" (Account › This device): ends this browser's session only (its refresh cookie and access tokens); the
     * account's other devices stay signed in. A request whose access token names no session (one issued before
     * sessions were named) signs the account out everywhere. A password change or account deletion always ends every
     * session.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void logout(SecurityUserDetails currentUser) {
        User user = currentAccount.findById(currentUser.getUserId());
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        // Guard against stale tokens: an account deactivated, deleted, or whose session version already moved on
        // must not have a newer session invalidated or a spurious LOGOUT entry recorded.
        if (!AccountGuards.isAvailable(user) || user.getSessionVersion() != currentUser.getSessionVersion()) {
            return;
        }
        if (currentUser.getSessionId() != null) {
            if (refreshTokenService.endSession(user, currentUser.getSessionId()) > 0) {
                auditUser(user, "LOGOUT", "User signed out of this browser; other sessions stay signed in.");
            }
            return;
        }
        refreshTokenService.invalidateSessions(user);
        auditUser(user, "LOGOUT", "User signed out and invalidated existing bearer tokens.");
    }

    /** When this browser's session started (Account › This device: "signed in since 12 May"); null when unknown. */
    @Transactional(readOnly = true)
    public SessionResponse currentSession(SecurityUserDetails currentUser) {
        return new SessionResponse(refreshTokenService.sessionStartedAt(currentUser.getUserId(), currentUser.getSessionId()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return;
        }
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        // Deactivated or deleted accounts receive no reset link, with the same non-enumerating response.
        if (!email.equalsIgnoreCase(user.getEmail()) || !AccountGuards.isAvailable(user)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.retireOpenTokensForUser(user.getId(), now);

        String rawToken = SecureTokens.newHexToken();
        PasswordResetToken token = new PasswordResetToken();
        token.initialize(user, SecureTokens.sha256Hex(rawToken), now.plus(RESET_TOKEN_TTL));
        passwordResetTokenRepository.save(token);

        String resetLink = emailProperties.passwordPageLink("token", rawToken);

        transactionalEmailDispatcher.enqueuePasswordReset(user, resetLink);

        auditUser(user, "PASSWORD_RESET_REQUESTED", "Password reset token issued.");
    }

    /**
     * The reset page asks whether its link still works before showing the form: the account address, or the reason
     * the link no longer works (the same messages as a reset with that token).
     */
    @Transactional(readOnly = true)
    public LinkTokenResponse checkResetToken(String rawToken) {
        PasswordResetToken token = findResetToken(rawToken);
        if (!AccountGuards.isAvailable(token.getUser())) {
            throw new ResponseStatusException(BAD_REQUEST, GENERIC_RESET_TOKEN_INVALID_MESSAGE);
        }
        requireResetTokenUnusedAndUnexpired(token);
        return new LinkTokenResponse(token.getUser().getEmail());
    }

    /**
     * Sets the new password from a valid link. Every session is signed out, and the sign-in pause of the address on
     * the device that reset is lifted: the sign-in page offers the reset as the way out of that pause.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void resetPassword(ResetPasswordRequest request, String clientIp) {
        validatePasswords(request.password(), request.confirmPassword());

        PasswordResetToken token = findResetToken(request.token());
        User user = token.getUser();
        // Check token usability and encode the new password before acquiring row write locks so BCrypt(12) never holds locks.
        if (!AccountGuards.isAvailable(user)) {
            throw new ResponseStatusException(BAD_REQUEST, GENERIC_RESET_TOKEN_INVALID_MESSAGE);
        }
        requireResetTokenUnusedAndUnexpired(token);
        String encodedPassword = passwordEncoder.encode(request.password());

        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        if (!AccountGuards.isAvailable(user)) {
            throw new ResponseStatusException(BAD_REQUEST, GENERIC_RESET_TOKEN_INVALID_MESSAGE);
        }
        entityManager.refresh(token, LockModeType.PESSIMISTIC_WRITE);
        requireResetTokenUnusedAndUnexpired(token);
        LocalDateTime now = LocalDateTime.now();

        if (passwordResetTokenRepository.consumeActiveToken(token.getId(), now) != 1) {
            throw new ResponseStatusException(BAD_REQUEST, GENERIC_RESET_TOKEN_INVALID_MESSAGE);
        }

        user.setPasswordHash(encodedPassword);
        refreshTokenService.invalidateSessions(user);
        // An e-mail change asked for with the old password must not go through after it was replaced.
        emailChangeTokenRepository.retireOpenTokensForUser(user.getId(), now);
        String resetEmail = user.getEmail();
        AfterCommit.run(() -> loginAttemptLimiter.recordSuccess(resetEmail, clientIp));

        auditUser(user, "PASSWORD_RESET_COMPLETED", "Password reset completed via valid token.");
    }

    /**
     * Applies the supplied profile fields to the locked account. Its preferred language also governs e-mail delivery.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CurrentUserResponse updateProfile(SecurityUserDetails currentUser, UpdateProfileRequest request) {
        User user = currentAccount.lock(currentUser);
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.preferredLanguage() != null) {
            user.setPreferredLanguage(request.preferredLanguage());
        }
        auditUser(user, "PROFILE_UPDATED", "User updated their display name or preferred language.");
        return currentUserResponse(user);
    }

    /**
     * FRAUTH08: verifies the current password, stores the new BCrypt hash, bumps the session
     * version and revokes every refresh token (other sessions stop working) and returns a fresh
     * session for the caller, which keeps the "Keep me signed in" choice of the caller's sign-in.
     * Earlier recovery and email-change links expire with the old password.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuthSessionResponse changePassword(
            SecurityUserDetails currentUser,
            ChangePasswordRequest request,
            String clientIp) {
        // Verify session liveness, current password and encode the new one before locking so BCrypt work never holds
        // the account row lock.
        User unlocked = currentAccount.findById(currentUser.getUserId());
        String checkedHash = verifyCurrentPasswordUnlocked(currentUser, request.currentPassword(), clientIp, unlocked);
        validatePasswords(request.newPassword(), request.confirmPassword());
        if (request.newPassword().equals(request.currentPassword())) {
            throw new ResponseStatusException(BAD_REQUEST, "New password must be different from the current password.");
        }
        String encodedNewPassword = passwordEncoder.encode(request.newPassword());

        User user = lockAndRecheckCurrentPassword(currentUser, request.currentPassword(), clientIp, unlocked, checkedHash);
        user.setPasswordHash(encodedNewPassword);
        refreshTokenService.invalidateSessions(user);
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.retireOpenTokensForUser(user.getId(), now);
        emailChangeTokenRepository.retireOpenTokensForUser(user.getId(), now);

        auditUser(user, "PASSWORD_CHANGED", "User changed their password; other sessions were signed out.");

        // A session signed in without "Keep me signed in" (a shared computer) must not come back persistent.
        return sessionWithAccessToken(user, refreshTokenService.issue(user, currentUser.isPersistentSession()));
    }

    /**
     * The signed-in account, write-locked and still available, once its current password is confirmed (e-mail changes;
     * {@link #changePassword} calls the unlocked-check and lock-recheck helpers directly around encoding the new
     * password). Wrong current passwords are limited and their lockout audited like sign-in.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public User requireCurrentPassword(SecurityUserDetails currentUser, String currentPassword, String clientIp) {
        User unlocked = currentAccount.findById(currentUser.getUserId());
        String checkedHash = verifyCurrentPasswordUnlocked(currentUser, currentPassword, clientIp, unlocked);
        return lockAndRecheckCurrentPassword(currentUser, currentPassword, clientIp, unlocked, checkedHash);
    }

    /**
     * Verifies session liveness, lockout and BCrypt before taking the row lock so neither revoked sessions nor wrong
     * passwords hold write locks, and revoked sessions cannot trigger account lockout.
     */
    private String verifyCurrentPasswordUnlocked(
            SecurityUserDetails currentUser,
            String currentPassword,
            String clientIp,
            User user) {
        AccountGuards.requireAvailable(user);
        AccountGuards.requireCurrent(user, currentUser.getSessionVersion());
        if (currentUser.getSessionId() != null
                && !refreshTokenService.isSessionActive(user.getId(), currentUser.getSessionId())) {
            throw new ResponseStatusException(UNAUTHORIZED, FriendlyErrorMessageResolver.sessionExpiredMessage());
        }
        String limiterKey = user.getEmail();
        requireNotLockedOut(limiterKey, clientIp);
        String checkedHash = user.getPasswordHash();
        if (checkedHash == null || !passwordEncoder.matches(currentPassword, checkedHash)) {
            recordLoginFailure(limiterKey, clientIp);
            throw new ResponseStatusException(BAD_REQUEST, "Current password is incorrect.");
        }
        return checkedHash;
    }

    private User lockAndRecheckCurrentPassword(
            SecurityUserDetails currentUser,
            String currentPassword,
            String clientIp,
            User user,
            String checkedHash) {
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        AccountGuards.requireAvailable(user);
        AccountGuards.requireCurrent(user, currentUser.getSessionVersion());
        currentAccount.requireActiveSession(user.getId(), currentUser.getSessionId());
        String limiterKey = user.getEmail();
        requireNotLockedOut(limiterKey, clientIp);
        if (!checkedHash.equals(user.getPasswordHash())
                && !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            recordLoginFailure(limiterKey, clientIp);
            throw new ResponseStatusException(BAD_REQUEST, "Current password is incorrect.");
        }
        AfterCommit.run(() -> loginAttemptLimiter.recordSuccess(limiterKey, clientIp));
        return user;
    }

    private void requireNotLockedOut(String email, String clientIp) {
        Duration retryAfter = loginAttemptLimiter.retryAfter(email, clientIp);
        if (!retryAfter.isZero()) {
            long retrySeconds = Math.max(1L, retryAfter.toSeconds());
            throw new RetryLaterException("Too many failed attempts.", retrySeconds);
        }
    }

    /** Sign-up, password reset and change, and an accepted admin invite: the password typed twice must match. */
    public static void validatePasswords(String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new ResponseStatusException(BAD_REQUEST, "Confirm password does not match.");
        }
    }

    /**
     * An e-mail address in the form it is stored and compared in (accounts, admin invites, support requests): stripped
     * and lower case, with Locale.ROOT so the server's locale cannot change a letter.
     */
    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private AuthSessionResponse sessionWithAccessToken(User user, RefreshTokenIssue refreshToken) {
        return new AuthSessionResponse(
                new AuthResponse(jwtService.generateToken(user, refreshToken.persistent(), refreshToken.sessionId())),
                refreshToken);
    }

    private PasswordResetToken findResetToken(String rawToken) {
        return passwordResetTokenRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken.strip()))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, GENERIC_RESET_TOKEN_INVALID_MESSAGE));
    }

    private static void requireResetTokenUnusedAndUnexpired(PasswordResetToken token) {
        if (token.getUsedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "Reset token has already been used.");
        }
        if (!token.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(BAD_REQUEST, "Reset token has expired. Please request a new one.");
        }
    }

    /** A failed sign-in counts against the address and client, with one answer whether or not the account exists. */
    private ResponseStatusException invalidCredentials(String email, String clientIp) {
        recordLoginFailure(email, clientIp);
        return new ResponseStatusException(UNAUTHORIZED, "Invalid email or password.");
    }

    private void auditUser(User user, String actionType, String description) {
        auditLogService.safeRecord(user, actionType, AuditLogService.TARGET_USER, user.getId(), description);
    }

    private void recordLoginFailure(String email, String clientIp) {
        if (loginAttemptLimiter.recordFailure(email, clientIp)) {
            // Written at once: a wrong current password rolls its request back, and the lockout must stay.
            auditLogService.safeRecordInNewTransaction("LOGIN_LOCKOUT", AuditLogService.TARGET_USER,
                    "Login lockout engaged after repeated failed attempts.");
        }
    }
}
