package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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
import com.example.aichinese.dto.auth.RegisterTeacherRequest;
import com.example.aichinese.dto.auth.RegistrationResponse;
import com.example.aichinese.dto.auth.ResetPasswordRequest;
import com.example.aichinese.dto.auth.UpdateProfileRequest;
import com.example.aichinese.dto.teacher.TeacherVerificationState;
import com.example.aichinese.entity.PasswordResetToken;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.exception.RetryLaterException;
import com.example.aichinese.repository.PasswordResetTokenRepository;
import com.example.aichinese.repository.RoleRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.JwtService;
import com.example.aichinese.security.LoginAttemptLimiter;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.plan.EmailConfirmationService;
import com.example.aichinese.util.LanguageCodes;
import com.example.aichinese.util.TextUtils;

@Service
public class AuthService {

    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(60);
    private static final String ACCOUNT_UNAVAILABLE_MESSAGE =
            "This account is currently unavailable. Please contact the platform administrator.";
    private static final String GENERIC_RESET_TOKEN_INVALID_MESSAGE =
            "Reset token is invalid or has already been used.";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final AuditLogService auditLogService;
    private final TransactionalEmailDispatcher transactionalEmailDispatcher;
    private final EmailProperties emailProperties;
    private final EmailConfirmationService emailConfirmationService;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            StudentProfileRepository studentProfileRepository,
            TeacherProfileRepository teacherProfileRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            LoginAttemptLimiter loginAttemptLimiter,
            AuditLogService auditLogService,
            TransactionalEmailDispatcher transactionalEmailDispatcher,
            EmailProperties emailProperties,
            EmailConfirmationService emailConfirmationService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.auditLogService = auditLogService;
        this.transactionalEmailDispatcher = transactionalEmailDispatcher;
        this.emailProperties = emailProperties;
        this.emailConfirmationService = emailConfirmationService;
    }

    @Transactional
    public RegistrationResponse registerStudent(RegisterStudentRequest request) {
        User savedUser = createUser(request.fullName(), request.email(), request.password(), request.confirmPassword(),
                LanguageCodes.normalizeOrDefault(request.preferredLanguage()), RoleName.STUDENT);

        StudentProfile profile = new StudentProfile();
        profile.setUser(savedUser);
        studentProfileRepository.save(profile);
        // The student can learn at once; Home asks them to confirm the address with the link sent now.
        emailConfirmationService.sendFirstLink(savedUser);

        return new RegistrationResponse(savedUser.getEmail());
    }

    @Transactional
    public RegistrationResponse registerTeacher(RegisterTeacherRequest request) {
        User savedUser = createUser(request.fullName(), request.email(), request.password(), request.confirmPassword(),
                LanguageCodes.normalizeOrDefault(request.preferredLanguage()), RoleName.TEACHER);

        TeacherProfile profile = new TeacherProfile();
        profile.setUser(savedUser);
        profile.setBio(TextUtils.trimToNull(request.bio()));
        profile.setTeacherStatus(TeacherStatus.PENDING);
        teacherProfileRepository.save(profile);

        return new RegistrationResponse(savedUser.getEmail());
    }

    /** Shared account creation for both public registration paths (FRAUTH01, FRAUTH02). */
    private User createUser(
            String fullName, String rawEmail, String password, String confirmPassword, String language, RoleName roleName) {
        validatePasswords(password, confirmPassword);
        String email = normalizeEmail(rawEmail);
        ensureEmailIsAvailable(email);

        User user = new User();
        user.setDisplayName(fullName);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPreferredLanguage(language);
        user.setActive(true);
        user.setRoles(Set.of(getRole(roleName)));
        return userRepository.save(user);
    }

    public AuthSessionResponse login(LoginRequest request, String clientIp, String userAgent) {
        String email = normalizeEmail(request.email());

        if (!loginAttemptLimiter.isAllowed(email, clientIp)) {
            Duration retryAfter = loginAttemptLimiter.retryAfter(email, clientIp);
            long retrySeconds = Math.max(1L, retryAfter.toSeconds());
            throw new RetryLaterException(
                    "Too many failed login attempts. Please try again in " + retrySeconds + " seconds.", retrySeconds);
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException exception) {
            recordLoginFailure(email, clientIp);
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid email or password.");
        } catch (DisabledException exception) {
            // FRAUTH05: reveal "unavailable" only after the password was verified, so a wrong password
            // never discloses the account state; a correct one is not counted as a failed attempt.
            if (passwordMatchesStoredHash(email, request.password())) {
                throw new ResponseStatusException(FORBIDDEN, ACCOUNT_UNAVAILABLE_MESSAGE);
            }
            recordLoginFailure(email, clientIp);
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid email or password.");
        } catch (AuthenticationException exception) {
            recordLoginFailure(email, clientIp);
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid email or password.", exception);
        }

        User user = userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> {
                    recordLoginFailure(email, clientIp);
                    return new ResponseStatusException(UNAUTHORIZED, "Invalid email or password.");
                });

        loginAttemptLimiter.recordSuccess(email, clientIp);
        return new AuthSessionResponse(
                new AuthResponse(jwtService.generateToken(user)),
                refreshTokenService.issue(user, clientIp, userAgent, request.keepSignedIn()));
    }

    public AuthSessionResponse refreshSession(String rawRefreshToken, String clientIp, String userAgent) {
        RefreshTokenService.RefreshSession refreshed = refreshTokenService.rotate(rawRefreshToken, clientIp, userAgent);
        return new AuthSessionResponse(
                new AuthResponse(jwtService.generateToken(refreshed.user())), refreshed.refreshToken());
    }

    public CurrentUserResponse getCurrentUser(SecurityUserDetails currentUser) {
        User user = userRepository.findByEmailWithRoles(currentUser.getUsername())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Authenticated user could not be found."));

        CurrentUserResponse.StudentProfileSummary studentSummary = studentProfileRepository.findByUserId(user.getId())
                .map(profile -> new CurrentUserResponse.StudentProfileSummary(profile.getTotalPoints()))
                .orElse(null);

        CurrentUserResponse.TeacherProfileSummary teacherSummary = teacherProfileRepository.findByUserId(user.getId())
                .map(profile -> {
                    TeacherVerificationState state = TeacherVerificationState.of(profile);
                    return new CurrentUserResponse.TeacherProfileSummary(
                            profile.getTeacherStatus(),
                            state,
                            state == TeacherVerificationState.APPROVED ? profile.getVerifiedAt() : null);
                })
                .orElse(null);

        return new CurrentUserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPreferredLanguage(),
                extractRoles(user),
                studentSummary,
                teacherSummary);
    }

    @Transactional
    public void logout(SecurityUserDetails currentUser) {
        User user = requireCurrentUser(currentUser);
        refreshTokenService.invalidateSessions(user);

        auditUser(user, "LOGOUT", "User signed out and invalidated existing bearer tokens.");
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findLockedByEmail(email).orElse(null);
        if (user == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.invalidateActiveTokensForUser(user.getId(), now);

        String rawToken = SecureTokens.newHexToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(SecureTokens.sha256Hex(rawToken));
        token.setExpiresAt(now.plus(RESET_TOKEN_TTL));
        passwordResetTokenRepository.save(token);

        String resetLink = emailProperties.resolvePasswordResetBaseUrl()
                + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        transactionalEmailDispatcher.enqueuePasswordReset(user, resetLink, rawToken);

        auditUser(user, "PASSWORD_RESET_REQUESTED", "Password reset token issued.");
    }

    /**
     * The reset page asks whether its link still works before showing the form: the account address, or the reason
     * the link no longer works (the same messages as a reset with that token).
     */
    @Transactional(readOnly = true)
    public LinkTokenResponse checkResetToken(String rawToken) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken.strip()))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, GENERIC_RESET_TOKEN_INVALID_MESSAGE));
        if (token.getUsedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "Reset token has already been used.");
        }
        if (!token.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(BAD_REQUEST, "Reset token has expired. Please request a new one.");
        }
        return new LinkTokenResponse(token.getUser().getEmail());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        validatePasswords(request.password(), request.confirmPassword());

        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(SecureTokens.sha256Hex(request.token()))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, GENERIC_RESET_TOKEN_INVALID_MESSAGE));

        LocalDateTime now = LocalDateTime.now();
        if (token.getUsedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "Reset token has already been used.");
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(BAD_REQUEST, "Reset token has expired. Please request a new one.");
        }

        User user = token.getUser();

        int consumed = passwordResetTokenRepository.consumeActiveToken(token.getId(), now);
        if (consumed != 1) {
            throw new ResponseStatusException(BAD_REQUEST, GENERIC_RESET_TOKEN_INVALID_MESSAGE);
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        refreshTokenService.invalidateSessions(user);

        auditUser(user, "PASSWORD_RESET_COMPLETED", "Password reset completed via valid token.");
    }

    /**
     * FRAUTH07: display name and preferred interface language for every role. The name is kept on the
     * account only (users.display_name), for students as for every other role.
     */
    @Transactional
    public CurrentUserResponse updateProfile(SecurityUserDetails currentUser, UpdateProfileRequest request) {
        User user = requireCurrentUser(currentUser);
        user.setDisplayName(request.displayName());
        user.setPreferredLanguage(request.preferredLanguage());
        userRepository.save(user);
        auditUser(user, "PROFILE_UPDATED", "User updated their display name or preferred language.");
        return getCurrentUser(currentUser);
    }

    /**
     * FRAUTH08: verifies the current password, stores the new BCrypt hash, bumps the session
     * version and revokes every refresh token (other sessions stop working) and returns a fresh
     * session for the caller. Repeated wrong current passwords are rate-limited like sign-in.
     */
    @Transactional
    public AuthSessionResponse changePassword(
            SecurityUserDetails currentUser,
            ChangePasswordRequest request,
            String clientIp,
            String userAgent) {
        User user = requireCurrentUser(currentUser);
        String limiterKey = normalizeEmail(user.getEmail());
        if (!loginAttemptLimiter.isAllowed(limiterKey, clientIp)) {
            long retrySeconds = Math.max(1L, loginAttemptLimiter.retryAfter(limiterKey, clientIp).toSeconds());
            throw new ResponseStatusException(
                    TOO_MANY_REQUESTS,
                    "Too many failed attempts. Please try again in " + retrySeconds + " seconds.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            recordLoginFailure(limiterKey, clientIp);
            throw new ResponseStatusException(BAD_REQUEST, "Current password is incorrect.");
        }
        loginAttemptLimiter.recordSuccess(limiterKey, clientIp);
        validatePasswords(request.newPassword(), request.confirmPassword());
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(BAD_REQUEST, "New password must be different from the current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        User saved = refreshTokenService.invalidateSessions(user);

        auditUser(saved, "PASSWORD_CHANGED", "User changed their password; other sessions were signed out.");

        // The caller's new session is kept like "Keep me signed in".
        return new AuthSessionResponse(
                new AuthResponse(jwtService.generateToken(saved)),
                refreshTokenService.issue(saved, clientIp, userAgent, true));
    }

    private boolean passwordMatchesStoredHash(String email, String rawPassword) {
        return userRepository.findByEmailWithRoles(email)
                .map(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()))
                .orElse(false);
    }

    private void auditUser(User user, String actionType, String description) {
        auditLogService.safeRecord(user, actionType, "USER", user.getId(), description);
    }

    private void recordLoginFailure(String email, String clientIp) {
        boolean nowLocked = loginAttemptLimiter.recordFailure(email, clientIp);
        if (nowLocked) {
            // Written at once: changePassword rolls back after a wrong current password, and the lockout must stay.
            auditLogService.safeRecordInNewTransaction(
                    null,
                    "LOGIN_LOCKOUT",
                    "USER",
                    null,
                    "Login lockout engaged after repeated failed attempts.");
        }
    }

    private User requireCurrentUser(SecurityUserDetails currentUser) {
        return userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Authenticated user could not be found."));
    }

    private void ensureEmailIsAvailable(String email) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(BAD_REQUEST, "Email is already registered.");
        }
    }

    private void validatePasswords(String password, String confirmPassword) {
        if (password == null || confirmPassword == null || !password.equals(confirmPassword)) {
            throw new ResponseStatusException(BAD_REQUEST, "Confirm password does not match.");
        }
    }

    private Role getRole(RoleName roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(roleName + " role is not available."));
    }

    private List<String> extractRoles(User user) {
        return user.getRoles().stream()
                .map(role -> role.getName().name())
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
