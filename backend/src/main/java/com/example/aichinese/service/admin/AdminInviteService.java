package com.example.aichinese.service.admin;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.dto.admin.AdminInviteResponse;
import com.example.aichinese.dto.auth.AcceptAdminInviteRequest;
import com.example.aichinese.dto.auth.LinkTokenResponse;
import com.example.aichinese.dto.auth.RegistrationResponse;
import com.example.aichinese.entity.AdminInvite;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AdminInviteRepository;
import com.example.aichinese.repository.RoleRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.AuditLogService.AuditTarget;
import com.example.aichinese.service.SecureTokens;
import com.example.aichinese.service.TransactionalEmailDispatcher;

/**
 * Inviting another admin: the invitee gets an e-mail link, sets a name and a password, and only then does the admin
 * account exist (it appears in Users). Sending the invite again replaces the link.
 */
@Service
public class AdminInviteService {

    static final Duration INVITE_TTL = Duration.ofDays(7);
    private static final String INVALID_INVITE = "This invite link is invalid or has expired. Ask an admin to send a new one.";

    private final AdminInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionalEmailDispatcher emailDispatcher;
    private final EmailProperties emailProperties;
    private final AuditLogService auditLogService;

    public AdminInviteService(
            AdminInviteRepository inviteRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            TransactionalEmailDispatcher emailDispatcher,
            EmailProperties emailProperties,
            AuditLogService auditLogService) {
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailDispatcher = emailDispatcher;
        this.emailProperties = emailProperties;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<AdminInviteResponse> pending() {
        return inviteRepository.findPending(LocalDateTime.now()).stream()
                .map(AdminInviteService::toResponse)
                .toList();
    }

    @Transactional
    public AdminInviteResponse invite(SecurityUserDetails currentUser, String rawEmail) {
        User admin = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Admin user could not be found."));
        String email = rawEmail.strip().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(BAD_REQUEST, "Email is already registered.");
        }

        LocalDateTime now = LocalDateTime.now();
        AdminInvite invite = inviteRepository.findFirstByEmailAndAcceptedAtIsNullOrderByCreatedAtDesc(email)
                .orElseGet(AdminInvite::new);
        invite.setEmail(email);
        String rawToken = SecureTokens.newHexToken();
        invite.setTokenHash(SecureTokens.sha256Hex(rawToken));
        invite.setInvitedBy(admin);
        invite.setExpiresAt(now.plus(INVITE_TTL));
        inviteRepository.save(invite);

        emailDispatcher.enqueueAdminInvite(email, admin.getDisplayName(),
                emailProperties.resolveFrontendUrl("reset-password")
                        + "?invite=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8));
        auditLogService.record(admin, "ADMIN_INVITE_SENT", new AuditTarget("AdminInvite", invite.getId(), email), null,
                "Admin invited " + email + " to become an admin.");
        return toResponse(invite);
    }

    @Transactional(readOnly = true)
    public LinkTokenResponse check(String rawToken) {
        AdminInvite invite = requireUsable(rawToken);
        return new LinkTokenResponse(invite.getEmail());
    }

    /** Creates the admin account (active) with the invitee's name and password. */
    @Transactional
    public RegistrationResponse accept(AcceptAdminInviteRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new ResponseStatusException(BAD_REQUEST, "Confirm password does not match.");
        }
        AdminInvite invite = requireUsable(request.token());
        if (userRepository.existsByEmailIgnoreCase(invite.getEmail())) {
            throw new ResponseStatusException(BAD_REQUEST, "Email is already registered.");
        }

        User user = new User();
        user.setEmail(invite.getEmail());
        user.setDisplayName(request.fullName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPreferredLanguage("en");
        user.setActive(true);
        user.setRoles(Set.of(roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role is not available."))));
        User saved = userRepository.save(user);

        invite.setAcceptedAt(LocalDateTime.now());
        invite.setAcceptedUser(saved);
        inviteRepository.save(invite);
        auditLogService.record(saved, "ADMIN_INVITE_ACCEPTED", new AuditTarget("User", saved.getId(), saved.getDisplayName()),
                null, "The invited admin set a password; the admin account is active.");
        return new RegistrationResponse(saved.getEmail());
    }

    private AdminInvite requireUsable(String rawToken) {
        AdminInvite invite = inviteRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken.strip()))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, INVALID_INVITE));
        if (invite.getAcceptedAt() != null || !invite.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(BAD_REQUEST, INVALID_INVITE);
        }
        return invite;
    }

    private static AdminInviteResponse toResponse(AdminInvite invite) {
        return new AdminInviteResponse(
                invite.getId(),
                invite.getEmail(),
                invite.getUpdatedAt() == null ? invite.getCreatedAt() : invite.getUpdatedAt(),
                invite.getExpiresAt());
    }
}
