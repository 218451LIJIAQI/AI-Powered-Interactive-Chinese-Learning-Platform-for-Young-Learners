package com.example.aichinese.service.account;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.dto.account.EmailConfirmationView;
import com.example.aichinese.entity.EmailConfirmationToken;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.EmailConfirmationTokenRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.SecureTokens;
import com.example.aichinese.service.TransactionalEmailDispatcher;
import com.example.aichinese.service.admin.AuditLogQueryService;

import lombok.RequiredArgsConstructor;

/**
 * Confirming a new student's e-mail address: a link is e-mailed at sign-up and can be sent again from Home (once a
 * minute, five times a day). Opening it marks the address confirmed; learning is never blocked while it waits.
 * Accounts that existed before, and changed addresses confirmed through Account, count as confirmed.
 */
@Service
@RequiredArgsConstructor
public class EmailConfirmationService {

    private static final Duration LINK_TTL = Duration.ofDays(3);
    private static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    private static final int MAX_LINKS_PER_DAY = 5;

    private final CurrentAccount currentAccount;
    private final EmailConfirmationTokenRepository tokenRepository;
    private final TransactionalEmailDispatcher emailDispatcher;
    private final EmailProperties emailProperties;
    private final AuditLogService auditLogService;
    private final EmailVerificationTokenClaimer tokenClaimer;

    /** Sign-up: the first link (the account is new, so there is nothing to limit or retire yet). */
    @Transactional
    public void sendFirstLink(User user) {
        issue(user, LocalDateTime.now());
    }

    /** "Send the link again" on Home. READ_COMMITTED sees concurrent updates under the account lock without gap locks. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmailConfirmationView resend(SecurityUserDetails currentUser) {
        User user = currentAccount.lock(currentUser);
        if (user.getEmailVerifiedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "Your email address is already confirmed.");
        }
        LocalDateTime now = LocalDateTime.now();
        List<EmailConfirmationToken> recent = tokenRepository
                .findByUserIdAndCreatedAtAfterOrderByCreatedAtDescIdDesc(user.getId(), now.minusDays(1));
        if (!recent.isEmpty() && recent.getFirst().getCreatedAt().isAfter(now.minus(RESEND_INTERVAL))) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS,
                    "We just sent a link. Please wait a minute before asking for another one.");
        }
        if (recent.size() >= MAX_LINKS_PER_DAY) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS,
                    "You asked for several links today. Please try again tomorrow.");
        }
        // Only the newest link works.
        tokenRepository.retireOpenTokensForUser(user.getId(), now);
        issue(user, now);
        return status(user);
    }

    /** Opens the link (public: the token is the proof). Returns the confirmed address. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String confirm(String rawToken) {
        EmailVerificationTokenClaimer.Claimed<EmailConfirmationToken> claimed = tokenClaimer.claim(
                tokenRepository,
                rawToken,
                (user, token) -> user.getEmail().equalsIgnoreCase(token.getEmail()),
                () -> new ResponseStatusException(
                        BAD_REQUEST, "This confirmation link has expired. Send a new one from Home."));
        User user = claimed.user();
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(claimed.claimedAt());
            auditLogService.safeRecord(user, "EMAIL_CONFIRMED", AuditLogQueryService.TARGET_USER, user.getId(),
                    "User confirmed the email address of their new account.");
        }
        return user.getEmail();
    }

    public EmailConfirmationView status(User user) {
        return new EmailConfirmationView(user.getEmail(), user.getEmailVerifiedAt() != null);
    }

    private void issue(User user, LocalDateTime now) {
        String rawToken = SecureTokens.newHexToken();
        EmailConfirmationToken token = new EmailConfirmationToken();
        token.initialize(user, SecureTokens.sha256Hex(rawToken), now.plus(LINK_TTL));
        token.setEmail(user.getEmail());
        tokenRepository.save(token);
        emailDispatcher.enqueueSignupConfirmation(user,
                emailProperties.resolveFrontendUrl("confirm-email")
                        + "?token=" + rawToken + "&kind=signup");
    }
}
