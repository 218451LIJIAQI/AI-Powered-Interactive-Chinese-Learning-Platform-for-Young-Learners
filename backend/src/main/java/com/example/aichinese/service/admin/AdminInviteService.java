package com.example.aichinese.service.admin;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.dto.admin.AdminInviteResponse;
import com.example.aichinese.dto.auth.AcceptAdminInviteRequest;
import com.example.aichinese.dto.auth.LinkTokenResponse;
import com.example.aichinese.entity.AdminInvite;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AdminInviteRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.AuditLogService.AuditTarget;
import com.example.aichinese.service.AuthService;
import com.example.aichinese.service.SecureTokens;
import com.example.aichinese.service.TransactionalEmailDispatcher;
import com.example.aichinese.service.account.AccountGuards;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.util.LanguageCodes;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

/**
 * Inviting another admin: the invitee gets an e-mail link, sets a name and a password, and only then does the admin
 * account exist (it appears in Users). Sending the invite again replaces the link; cancelling it stops the link until
 * the cancel is undone.
 */
@Service
@RequiredArgsConstructor
public class AdminInviteService {

    public static final String TARGET_TYPE = "AdminInvite";

    private static final Duration INVITE_TTL = Duration.ofDays(7);
    private static final String INVALID_INVITE = "This invite link is invalid or has expired. Ask an admin to send a new one.";
    private static final String INVITE_NOT_FOUND = "This invite was not found.";
    private static final String INVITE_ACCEPTED = "This invite was already accepted.";
    private static final String INVITE_EXPIRED = "This invite has expired. Send a new invite.";

    private final AdminInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final TransactionalEmailDispatcher emailDispatcher;
    private final EmailProperties emailProperties;
    private final AuditLogService auditLogService;
    private final CurrentAccount currentAccount;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<AdminInviteResponse> pending() {
        return inviteRepository.findPending(LocalDateTime.now()).stream()
                .map(AdminInviteService::toResponse)
                .toList();
    }

    /**
     * Uniqueness of unaccepted invites per email is enforced by {@code uk_admin_invites_unaccepted_email}, avoiding
     * SERIALIZABLE full-table shared locks on {@code users} and S-to-X lock-upgrade deadlocks.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminInviteResponse invite(SecurityUserDetails currentUser, String rawEmail) {
        User admin = currentAccount.lock(currentUser);
        String email = AuthService.normalizeEmail(rawEmail);

        LocalDateTime now = LocalDateTime.now();
        // A cancelled invite is sent again as well ("Resend" after an Undo, or a new invite to the same address).
        AdminInvite invite = inviteRepository.findFirstByEmailAndAcceptedAtIsNullOrderByCreatedAtDesc(email)
                .orElseGet(AdminInvite::new);
        // Check after acquiring the invite lock so an accept() that committed while waiting is visible under READ_COMMITTED.
        AccountGuards.requireUnregisteredEmail(userRepository, email);
        invite.setEmail(email);
        String rawToken = SecureTokens.newHexToken();
        invite.setTokenHash(SecureTokens.sha256Hex(rawToken));
        invite.setExpiresAt(now.plus(INVITE_TTL));
        invite.setCancelledAt(null);
        // Flushed at once: a sent-again invite's sentAt (its updated_at, set on flush) must be this sending.
        inviteRepository.saveAndFlush(invite);

        emailDispatcher.enqueueAdminInvite(email, admin, emailProperties.passwordPageLink("invite", rawToken));
        auditLogService.record(admin, "ADMIN_INVITE_SENT", new AuditTarget(TARGET_TYPE, invite.getId(), email), null,
                "Admin sent an admin invite.");
        return toResponse(invite);
    }

    /** Keeps the token so an admin can undo the cancellation before it expires. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(SecurityUserDetails currentUser, Long inviteId) {
        User admin = currentAccount.lock(currentUser);
        AdminInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, INVITE_NOT_FOUND));
        lockUnaccepted(invite);
        if (invite.getCancelledAt() != null) {
            throw new ResponseStatusException(NOT_FOUND, INVITE_NOT_FOUND);
        }
        if (!invite.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(CONFLICT, INVITE_EXPIRED);
        }
        inviteRepository.markCancelled(invite.getId(), LocalDateTime.now());
        auditLogService.record(admin, "ADMIN_INVITE_CANCELLED",
                new AuditTarget(TARGET_TYPE, invite.getId(), invite.getEmail()), null,
                "Admin cancelled the admin invite.");
    }

    /**
     * The Undo of "Cancel invite": the invite is pending again, with the link, the expiry and the sending time it had.
     * An invite that is not cancelled (never cancelled, or sent again meanwhile) stays as it is.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminInviteResponse restore(SecurityUserDetails currentUser, Long inviteId) {
        User admin = currentAccount.lock(currentUser);
        AdminInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, INVITE_NOT_FOUND));
        lockUnaccepted(invite);
        if (!invite.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(CONFLICT, INVITE_EXPIRED);
        }
        AccountGuards.requireUnregisteredEmail(userRepository, invite.getEmail());
        AdminInviteResponse response = toResponse(invite);
        if (inviteRepository.clearCancelled(invite.getId()) > 0) {
            auditLogService.record(admin, "ADMIN_INVITE_RESTORED",
                    new AuditTarget(TARGET_TYPE, response.id(), response.email()), null,
                    "Admin undid the cancelling of the admin invite; its link works again.");
        }
        return response;
    }

    private void lockUnaccepted(AdminInvite invite) {
        try {
            entityManager.refresh(invite, LockModeType.PESSIMISTIC_WRITE);
        } catch (EntityNotFoundException deleted) {
            throw new ResponseStatusException(NOT_FOUND, INVITE_NOT_FOUND, deleted);
        }
        if (invite.getAcceptedAt() != null) {
            throw new ResponseStatusException(CONFLICT, INVITE_ACCEPTED);
        }
    }

    @Transactional(readOnly = true)
    public LinkTokenResponse check(String rawToken) {
        AdminInvite invite = requireUsable(rawToken);
        AccountGuards.requireUnregisteredEmail(userRepository, invite.getEmail());
        return new LinkTokenResponse(invite.getEmail());
    }

    /**
     * Creates the admin account (active) with the invitee's name and password, and the interface language the invitee
     * accepted in (English when the request names none), like a sign-up.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LinkTokenResponse accept(AcceptAdminInviteRequest request) {
        AuthService.validatePasswords(request.password(), request.confirmPassword());
        AdminInvite invite = requireUsable(request.token());
        AccountGuards.requireUnregisteredEmail(userRepository, invite.getEmail());
        // Pre-encode before acquiring the invite update lock so BCrypt(12) never holds row write locks.
        String encodedPassword = passwordEncoder.encode(request.password());
        LocalDateTime now = LocalDateTime.now();
        // Used up by a conditional update, like the other e-mailed links: one cancelled, sent again or accepted since
        // it was read creates no account.
        if (inviteRepository.markAccepted(invite.getTokenHash(), now) != 1) {
            throw new ResponseStatusException(BAD_REQUEST, INVALID_INVITE);
        }
        User saved = authService.createAccountWithEncodedPassword(request.fullName(), invite.getEmail(), encodedPassword,
                LanguageCodes.normalizeOrDefault(request.preferredLanguage()), RoleName.ADMIN);
        saved.setEmailVerifiedAt(now);

        auditLogService.record(saved, "ADMIN_INVITE_ACCEPTED",
                new AuditTarget(AuditLogService.TARGET_USER, saved.getId(), saved.getDisplayName()),
                null, "The invited admin set a password; the admin account is active.");
        return new LinkTokenResponse(saved.getEmail());
    }

    /** The invite of a link that still works: not accepted, not cancelled and not expired. */
    private AdminInvite requireUsable(String rawToken) {
        AdminInvite invite = inviteRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken.strip()))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, INVALID_INVITE));
        if (invite.getAcceptedAt() != null || invite.getCancelledAt() != null
                || !invite.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(BAD_REQUEST, INVALID_INVITE);
        }
        return invite;
    }

    private static AdminInviteResponse toResponse(AdminInvite invite) {
        return new AdminInviteResponse(invite.getId(), invite.getEmail(), invite.getUpdatedAt());
    }
}
