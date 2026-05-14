package com.example.aichinese.service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.auth.AuthResponse;
import com.example.aichinese.dto.auth.CurrentUserResponse;
import com.example.aichinese.dto.auth.ForgotPasswordRequest;
import com.example.aichinese.dto.auth.LoginRequest;
import com.example.aichinese.dto.auth.RegisterStudentRequest;
import com.example.aichinese.dto.auth.RegisterTeacherRequest;
import com.example.aichinese.dto.auth.RegistrationResponse;
import com.example.aichinese.dto.auth.ResetPasswordRequest;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.RoleRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.JwtService;
import com.example.aichinese.security.LoginAttemptLimiter;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.TextUtils;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final AuditLogService auditLogService;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            StudentProfileRepository studentProfileRepository,
            TeacherProfileRepository teacherProfileRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            LoginAttemptLimiter loginAttemptLimiter,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.auditLogService = auditLogService;
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

    /**
     * Authenticates a user. The {@code clientIp} is used to scope the
     * brute-force rate limiter so a misbehaving network does not lock out
     * every user account behind the same gateway.
     */
    public AuthResponse login(LoginRequest request, String clientIp) {
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

    /**
     * Accepts a forgot-password request without confirming whether the email exists, to avoid
     * leaking which addresses are registered. The actual reset email is not delivered in this
     * prototype because token-based email delivery is intentionally not configured. The public
     * controller response always says the same thing regardless of email existence.
     */
    public void forgotPassword(ForgotPasswordRequest request) {
        normalizeEmail(request.email());
    }

    /**
     * Reset password is intentionally unavailable in this prototype because the email-based
     * delivery of reset tokens is not implemented. The endpoint validates the request shape and
     * then returns a clear 503 so that automated tests, the frontend, and examiners all get a
     * consistent and honest "feature not available" signal rather than a fake success.
     */
    public void resetPassword(ResetPasswordRequest request) {
        validatePasswords(request.password(), request.confirmPassword());
        if (TextUtils.trimToNull(request.token()) == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Reset token is required.");
        }
        throw new ResponseStatusException(
                SERVICE_UNAVAILABLE,
                "Password reset is currently unavailable because email-based token delivery is not configured. "
                        + "Please contact an administrator for assistance.");
    }

    /**
     * Records a login failure against the rate limiter and, when that failure
     * is the one that trips the lockout threshold, emits a structured audit
     * log entry so admins viewing {@code /admin/audit-logs} can see brute
     * force / credential-stuffing attempts. Uses {@code safeRecord} so a
     * transient audit-log persistence failure never hides the 401 we owe
     * the caller.
     */
    private void recordLoginFailure(String email, String clientIp) {
        boolean nowLocked = loginAttemptLimiter.recordFailure(email, clientIp);
        if (nowLocked) {
            auditLogService.safeRecord(
                    null,
                    "LOGIN_LOCKOUT",
                    "USER",
                    null,
                    "Login lockout engaged for " + email + " after repeated failed attempts.",
                    clientIp);
        }
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
