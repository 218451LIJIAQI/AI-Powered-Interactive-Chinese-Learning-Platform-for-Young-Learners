package com.example.aichinese.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.authentication.AuthenticationManager;
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
import com.example.aichinese.security.SecurityUserDetails;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
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

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            StudentProfileRepository studentProfileRepository,
            TeacherProfileRepository teacherProfileRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
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
        profile.setBio(trimToNull(request.bio()).orElse(null));
        profile.setTeacherStatus(TeacherStatus.PENDING);
        profile.setPublicVisible(false);
        teacherProfileRepository.save(profile);

        return toRegistrationResponse(savedUser, profile.getTeacherStatus());
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException exception) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid email or password.");
        }

        User user = userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid email or password."));

        TeacherStatus teacherStatus = teacherProfileRepository.findByUserId(user.getId())
                .map(TeacherProfile::getTeacherStatus)
                .orElse(null);

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

    public void forgotPassword(ForgotPasswordRequest request) {
        normalizeEmail(request.email());
    }

    public void resetPassword(ResetPasswordRequest request) {
        validatePasswords(request.password(), request.confirmPassword());
        if (trimToNull(request.token()).isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Reset token is required.");
        }
    }

    public boolean isTeacherApproved(User user) {
        return teacherProfileRepository.findByUserId(user.getId())
                .map(profile -> profile.getTeacherStatus() == TeacherStatus.APPROVED)
                .orElse(false);
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
        return trimToNull(email)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Email is required."));
    }

    private String normalizePreferredLanguage(String preferredLanguage) {
        return trimToNull(preferredLanguage).orElse("en");
    }

    private Optional<String> trimToNull(String value) {
        if (value == null) {
            return Optional.empty();
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }
}
