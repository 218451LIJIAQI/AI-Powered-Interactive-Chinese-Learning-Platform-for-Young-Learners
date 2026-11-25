package com.example.aichinese.service.account;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.dto.account.EmailChangeResponse;
import com.example.aichinese.entity.EmailChangeToken;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AdminInviteRepository;
import com.example.aichinese.repository.EmailChangeTokenRepository;
import com.example.aichinese.repository.PasswordResetTokenRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.PublicAuthRequestLimiter;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.AuditLogService.AuditTarget;
import com.example.aichinese.service.AuthService;
import com.example.aichinese.service.RefreshTokenService;
import com.example.aichinese.service.SecureTokens;
import com.example.aichinese.service.TransactionalEmailDispatcher;

import lombok.RequiredArgsConstructor;

/**
 * Changing the sign-in e-mail: the current password proves the owner asks, and a link sent to the new address proves
 * the owner can read it. The address changes only when that link is opened, once and within 60 minutes; opening it
 * signs the account out everywhere, so every device signs in again with the new address. While the change waits, the
 * Account page can send the link again (a new link replaces the old one) or cancel the change (the link stops working).
 */
@Service
@RequiredArgsConstructor
public class AccountEmailService {

    private static final Duration LINK_TTL = Duration.ofMinutes(60);
    private static final String NO_PENDING_CHANGE =
            "No email change is waiting for confirmation. Use Change email to start again.";

    private final UserRepository userRepository;
    private final EmailChangeTokenRepository tokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AdminInviteRepository adminInviteRepository;
    private final AuthService authService;
    private final TransactionalEmailDispatcher emailDispatcher;
    private final EmailProperties emailProperties;
    private final AuditLogService auditLogService;
    private final RefreshTokenService refreshTokenService;
    private final PublicAuthRequestLimiter requestLimiter;
    private final CurrentAccount currentAccount;
    private final EmailVerificationTokenClaimer tokenClaimer;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmailChangeResponse requestChange(
            SecurityUserDetails currentUser, String rawNewEmail, String currentPassword, String clientIp) {
        User user = authService.requireCurrentPassword(currentUser, currentPassword, clientIp);

        String newEmail = AuthService.normalizeEmail(rawNewEmail);
        if (newEmail.equals(user.getEmail())) {
            throw new ResponseStatusException(BAD_REQUEST, "This is already your email address.");
        }
        return sendLink(user, newEmail, clientIp,
                "User asked to change their email address; a confirmation link was sent to the new address.");
    }

    /**
     * "Resend link": a new link to the address that waits for confirmation, which replaces the link sent before. The
     * current password already confirmed this change, so it is not asked again; the new link is limited like the first,
     * and an address another account took meanwhile is refused.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmailChangeResponse resendLink(SecurityUserDetails currentUser, String clientIp) {
        User user = currentAccount.lock(currentUser);
        String newEmail = tokenRepository.findLockedPendingToken(user.getId(), LocalDateTime.now())
                .map(EmailChangeToken::getNewEmail)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, NO_PENDING_CHANGE));
        return sendLink(user, newEmail, clientIp, "User asked for the confirmation link of their email change again;"
                + " a new link was sent to the new address.");
    }

    /**
     * "Cancel change": the waiting change ends and its link stops working; the address stays as it is. Nothing waiting
     * (the link was opened, or it ran out meanwhile) is no error: the change is over either way.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(SecurityUserDetails currentUser) {
        User user = currentAccount.lock(currentUser);
        LocalDateTime now = LocalDateTime.now();
        // Only retire and audit when an unexpired confirmation link is actually waiting.
        if (tokenRepository.findLockedPendingToken(user.getId(), now).isEmpty()) {
            return;
        }
        tokenRepository.retireOpenTokensForUser(user.getId(), now);
        audit(user, "EMAIL_CHANGE_CANCELLED",
                "User cancelled their email change; its confirmation link no longer works.");
    }

    /** The address a change waits to confirm: that of the newest link that can still be opened. */
    public Optional<String> pendingEmail(Long userId) {
        return tokenRepository.findPendingEmail(userId, LocalDateTime.now());
    }

    /**
     * Retires the open links of the account and e-mails a new one, valid for 60 minutes, to {@code newEmail}. Limited
     * like "Forgot password", per sender and recipient: each request either e-mails an address the requester typed or
     * says it belongs to another account, so the limit comes first and checking addresses counts too.
     */
    private EmailChangeResponse sendLink(User user, String newEmail, String clientIp, String auditDescription) {
        requestLimiter.checkEmailChangeAllowed(newEmail, clientIp);
        AccountGuards.requireUnregisteredEmail(userRepository, newEmail);
        LocalDateTime now = LocalDateTime.now();
        tokenRepository.retireOpenTokensForUser(user.getId(), now);
        String rawToken = SecureTokens.newHexToken();
        EmailChangeToken token = new EmailChangeToken();
        token.initialize(user, SecureTokens.sha256Hex(rawToken), now.plus(LINK_TTL));
        token.setNewEmail(newEmail);
        tokenRepository.save(token);
        emailDispatcher.enqueueEmailChangeConfirmation(user, newEmail, emailProperties.resolveFrontendUrl("confirm-email")
                + "?token=" + rawToken);
        audit(user, "EMAIL_CHANGE_REQUESTED", auditDescription);
        return new EmailChangeResponse(newEmail);
    }

    private void audit(User user, String action, String description) {
        auditLogService.record(user, action,
                new AuditTarget(AuditLogService.TARGET_USER, user.getId(), user.getDisplayName()), null, description);
    }

    /** Opens the link: the new address replaces the old one and every session is signed out. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String confirm(String rawToken) {
        // Consume before checking email uniqueness so a retired/replayed link never reveals address availability.
        EmailVerificationTokenClaimer.Claimed<EmailChangeToken> claimed = tokenClaimer.claim(
                tokenRepository,
                rawToken,
                (user, token) -> true,
                () -> new ResponseStatusException(
                        BAD_REQUEST, "This confirmation link has expired. Ask for a new one in Account."));
        EmailChangeToken token = claimed.token();
        User user = claimed.user();
        LocalDateTime now = claimed.claimedAt();
        String newEmail = token.getNewEmail();
        AccountGuards.requireUnregisteredEmail(userRepository, newEmail);
        // Any open admin invite for the newly claimed address is cancelled before writing users.email
        // (global lock order: existing users -> admin_invites -> users.idx_users_email).
        adminInviteRepository.cancelOpenForEmail(newEmail, now);
        // Re-check under READ_COMMITTED so an admin invite accepted while waiting is rejected cleanly as 400.
        AccountGuards.requireUnregisteredEmail(userRepository, newEmail);
        user.setEmail(newEmail);
        // The link went to the new address, so opening it confirms that address too.
        user.setEmailVerifiedAt(now);
        refreshTokenService.invalidateSessions(user);
        // A reset link sent to the old address must not reach the account any more.
        passwordResetTokenRepository.retireOpenTokensForUser(user.getId(), now);
        audit(user, "EMAIL_CHANGED", "User confirmed a new email address.");
        return user.getEmail();
    }
}
