package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.dto.auth.AuthResponse;
import com.example.aichinese.dto.auth.AuthSessionResponse;
import com.example.aichinese.dto.auth.CurrentUserResponse;
import com.example.aichinese.dto.auth.ForgotPasswordRequest;
import com.example.aichinese.dto.auth.LoginRequest;
import com.example.aichinese.dto.auth.RegisterStudentRequest;
import com.example.aichinese.dto.auth.RegisterTeacherRequest;
import com.example.aichinese.dto.auth.RegistrationResponse;
import com.example.aichinese.dto.auth.ResetPasswordRequest;
import com.example.aichinese.entity.PasswordResetToken;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.PasswordResetTokenRepository;
import com.example.aichinese.repository.RoleRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.JwtService;
import com.example.aichinese.security.LoginAttemptLimiter;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.TextUtils;

@Service
public class AuthService {

    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(60);

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
            EmailProperties emailProperties) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository");
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "studentProfileRepository");
        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "teacherProfileRepository");
        this.passwordResetTokenRepository = Objects.requireNonNull(passwordResetTokenRepository, "passwordResetTokenRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.authenticationManager = Objects.requireNonNull(authenticationManager, "authenticationManager");
        this.jwtService = Objects.requireNonNull(jwtService, "jwtService");
        this.refreshTokenService = Objects.requireNonNull(refreshTokenService, "refreshTokenService");
        this.loginAttemptLimiter = Objects.requireNonNull(loginAttemptLimiter, "loginAttemptLimiter");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
        this.transactionalEmailDispatcher = Objects.requireNonNull(transactionalEmailDispatcher, "transactionalEmailDispatcher");
        this.emailProperties = Objects.requireNonNull(emailProperties, "emailProperties");
    }

    @Transactional
    public RegistrationResponse registerStudent(RegisterStudentRequest request) {
        validatePasswords(request.password(), request.confirmPassword());
        String email = normalizeEmail(request.email());
        ensureEmailIsAvailable(email);

        Role studentRole = getRole(RoleName.STUDENT);

        User user = new User();
        user.setDisplayName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPreferredLanguage(normalizePreferredLanguage(request.preferredLanguage()));
        user.setActive(true);
        user.setRoles(Set.of(studentRole));
        User savedUser = userRepository.save(user);

        StudentProfile profile = new StudentProfile();
        profile.setUser(savedUser);
        profile.setDisplayName(savedUser.getDisplayName());
        studentProfileRepository.save(profile);

        return toRegistrationResponse(savedUser, null);
    }

    @Transactional
    public RegistrationResponse registerTeacher(RegisterTeacherRequest request) {
        validatePasswords(request.password(), request.confirmPassword());
        String email = normalizeEmail(request.email());
        ensureEmailIsAvailable(email);

        Role teacherRole = getRole(RoleName.TEACHER);

        User user = new User();
        user.setDisplayName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPreferredLanguage("en");
        user.setActive(true);
        user.setRoles(Set.of(teacherRole));
        User savedUser = userRepository.save(user);

        TeacherProfile profile = new TeacherProfile();
        profile.setUser(savedUser);
        profile.setBio(TextUtils.trimToNull(request.bio()));
        profile.setTeacherStatus(TeacherStatus.PENDING);
        profile.setPublicVisible(false);
        teacherProfileRepository.save(profile);

        return toRegistrationResponse(savedUser, profile.getTeacherStatus());
    }

    public AuthSessionResponse login(LoginRequest request, String clientIp, String userAgent) {
        String email = normalizeEmail(request.email());

        if (!loginAttemptLimiter.isAllowed(email, clientIp)) {
            Duration retryAfter = loginAttemptLimiter.retryAfter(email, clientIp);
            long retrySeconds = Math.max(1L, retryAfter.toSeconds());
            throw new ResponseStatusException(
                    TOO_MANY_REQUESTS,
                    "Too many failed login attempts. Please try again in " + retrySeconds + " seconds.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException exception) {
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

        TeacherStatus teacherStatus = teacherProfileRepository.findByUserId(user.getId())
                .map(TeacherProfile::getTeacherStatus)
                .orElse(null);

        loginAttemptLimiter.recordSuccess(email, clientIp);

        AuthResponse authResponse = toAuthResponse(user, teacherStatus);
        return new AuthSessionResponse(
                authResponse,
                refreshTokenService.issue(user, clientIp, userAgent));
    }

    public AuthSessionResponse refreshSession(String rawRefreshToken, String clientIp, String userAgent) {
        RefreshTokenService.RefreshSession refreshed = refreshTokenService.rotate(rawRefreshToken, clientIp, userAgent);
        User user = refreshed.user();
        TeacherStatus teacherStatus = teacherProfileRepository.findByUserId(user.getId())
                .map(TeacherProfile::getTeacherStatus)
                .orElse(null);
        return new AuthSessionResponse(toAuthResponse(user, teacherStatus), refreshed.refreshToken());
    }

    private AuthResponse toAuthResponse(User user, TeacherStatus teacherStatus) {
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, new AuthResponse.UserSummary(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                extractRoles(user),
                teacherStatus));
    }

    public CurrentUserResponse getCurrentUser(SecurityUserDetails currentUser) {
        User user = userRepository.findByEmailWithRoles(currentUser.getUsername())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Authenticated user could not be found."));

        CurrentUserResponse.StudentProfileSummary studentSummary = studentProfileRepository.findByUserId(user.getId())
                .map(profile -> new CurrentUserResponse.StudentProfileSummary(
                        profile.getId(),
                        profile.getDisplayName(),
                        profile.getCurrentLevel() != null ? profile.getCurrentLevel().getName() : null,
                        profile.getTotalPoints()))
                .orElse(null);

        CurrentUserResponse.TeacherProfileSummary teacherSummary = teacherProfileRepository.findByUserId(user.getId())
                .map(profile -> new CurrentUserResponse.TeacherProfileSummary(
                        profile.getId(),
                        profile.getTeacherStatus(),
                        profile.getBio(),
                        profile.getRejectionReason()))
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
        rotateSessionVersion(user);
        userRepository.save(user);
        refreshTokenService.revokeActiveTokensForUser(user.getId());

        auditLogService.safeRecord(
                user,
                "LOGOUT",
                "USER",
                user.getId(),
                "User signed out and invalidated existing bearer tokens.",
                null);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        Optional<User> userLookup = userRepository.findLockedByEmail(email);
        if (userLookup.isEmpty()) {
            return;
        }

        User user = userLookup.orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.invalidateActiveTokensForUser(user.getId(), now);

        String rawToken = generateUniqueToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hashResetToken(rawToken));
        token.setExpiresAt(now.plus(RESET_TOKEN_TTL));
        PasswordResetToken saved = passwordResetTokenRepository.save(token);

        String resetLink = emailProperties.resolvePasswordResetBaseUrl()
                + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        transactionalEmailDispatcher.enqueuePasswordReset(user, resetLink, rawToken);

        auditLogService.safeRecord(
                user,
                "PASSWORD_RESET_REQUESTED",
                "USER",
                user.getId(),
                "Password reset token issued.",
                null);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        validatePasswords(request.password(), request.confirmPassword());
        String rawToken = TextUtils.trimToNull(request.token());
        if (rawToken == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Reset token is required.");
        }

        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hashResetToken(rawToken))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Reset token is invalid or has already been used."));

        LocalDateTime now = LocalDateTime.now();
        if (token.getUsedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "Reset token has already been used.");
        }
        if (token.getExpiresAt() == null || !token.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(BAD_REQUEST, "Reset token has expired. Please request a new one.");
        }

        User user = token.getUser();
        if (user == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Reset token is invalid or has already been used.");
        }

        int consumed = passwordResetTokenRepository.consumeActiveToken(token.getId(), now);
        if (consumed != 1) {
            throw new ResponseStatusException(BAD_REQUEST, "Reset token is invalid or has already been used.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        rotateSessionVersion(user);
        userRepository.save(user);
        refreshTokenService.revokeActiveTokensForUser(user.getId());

        auditLogService.safeRecord(
                user,
                "PASSWORD_RESET_COMPLETED",
                "USER",
                user.getId(),
                "Password reset completed via valid token.",
                null);
    }

    private String generateUniqueToken() {
        for (int attempt = 0; attempt < 3; attempt++) {
            String candidate = (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "");
            if (passwordResetTokenRepository.findByTokenHash(hashResetToken(candidate)).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique password reset token.");
    }

    private String hashResetToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable for password-reset token hashing.", exception);
        }
    }

    private void recordLoginFailure(String email, String clientIp) {
        boolean nowLocked = loginAttemptLimiter.recordFailure(email, clientIp);
        if (nowLocked) {
            auditLogService.safeRecord(
                    null,
                    "LOGIN_LOCKOUT",
                    "USER",
                    null,
                    "Login lockout engaged after repeated failed attempts.",
                    null);
        }
    }

    private User requireCurrentUser(SecurityUserDetails currentUser) {
        return userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Authenticated user could not be found."));
    }

    private void rotateSessionVersion(User user) {
        user.setSessionVersion(user.getSessionVersion() + 1);
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

    private RegistrationResponse toRegistrationResponse(User user, TeacherStatus teacherStatus) {
        return new RegistrationResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPreferredLanguage(),
                extractRoles(user),
                teacherStatus);
    }

    private List<String> extractRoles(User user) {
        return user.getRoles().stream()
                .map(role -> role.getName().name())
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String normalizeEmail(String email) {
        return TextUtils.trimToOptional(email)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Email is required."));
    }

    private String normalizePreferredLanguage(String preferredLanguage) {
        return TextUtils.trimToOptional(preferredLanguage)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse("en");
    }
}
