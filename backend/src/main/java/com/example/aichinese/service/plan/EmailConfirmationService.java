package com.example.aichinese.service.plan;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.dto.plan.EmailConfirmationView;
import com.example.aichinese.entity.EmailConfirmationToken;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.EmailConfirmationTokenRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.SecureTokens;
import com.example.aichinese.service.TransactionalEmailDispatcher;

/**
 * Confirming a new student's e-mail address: a link is e-mailed at sign-up and can be sent again from Home (once a
 * minute, five times a day). Opening it marks the address confirmed; learning is never blocked while it waits.
 * Accounts that existed before, and changed addresses confirmed through Account, count as confirmed.
 */
@Service
public class EmailConfirmationService {

    static final Duration LINK_TTL = Duration.ofDays(3);
    static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    static final int MAX_LINKS_PER_DAY = 5;
    private static final String INVALID_LINK = "This confirmation link is invalid or has already been used.";

    private final UserRepository userRepository;
    private final EmailConfirmationTokenRepository tokenRepository;
    private final TransactionalEmailDispatcher emailDispatcher;
    private final EmailProperties emailProperties;
    private final AuditLogService auditLogService;

    public EmailConfirmationService(
            UserRepository userRepository,
            EmailConfirmationTokenRepository tokenRepository,
            TransactionalEmailDispatcher emailDispatcher,
            EmailProperties emailProperties,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailDispatcher = emailDispatcher;
        this.emailProperties = emailProperties;
        this.auditLogService = auditLogService;
    }

    /** Sign-up: the first link (the account is new, so there is nothing to limit yet). */
    @Transactional
    public void sendFirstLink(User user) {
        issue(user, LocalDateTime.now());
    }

    /** "Send the link again" on Home. */
    @Transactional
    public EmailConfirmationView resend(SecurityUserDetails currentUser) {
        User user = userRepository.findLockedById(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Authenticated user could not be found."));
        if (user.getEmailVerifiedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "Your email address is already confirmed.");
        }
        LocalDateTime now = LocalDateTime.now();
        tokenRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId())
                .filter(last -> last.getCreatedAt().isAfter(now.minus(RESEND_INTERVAL)))
                .ifPresent(last -> {
                    throw new ResponseStatusException(TOO_MANY_REQUESTS,
                            "We just sent a link. Please wait a minute before asking for another one.");
                });
        if (tokenRepository.countByUserIdAndCreatedAtAfter(user.getId(), now.minusDays(1)) >= MAX_LINKS_PER_DAY) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS,
                    "You asked for several links today. Please try again tomorrow.");
        }
        issue(user, now);
        return status(user);
    }

    /** Opens the link (public: the token is the proof). Returns the confirmed address. */
    @Transactional
    public String confirm(String rawToken) {
        EmailConfirmationToken token = tokenRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken.strip()))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, INVALID_LINK));
        LocalDateTime now = LocalDateTime.now();
        User user = token.getUser();
        if (token.getUsedAt() != null || !user.isActive() || user.getDeletedAt() != null
                || !user.getEmail().equalsIgnoreCase(token.getEmail())) {
            throw new ResponseStatusException(BAD_REQUEST, INVALID_LINK);
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(BAD_REQUEST, "This confirmation link has expired. Send a new one from Home.");
        }
        token.setUsedAt(now);
        tokenRepository.save(token);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
            userRepository.save(user);
            auditLogService.safeRecord(user, "EMAIL_CONFIRMED", "User", user.getId(),
                    "User confirmed the email address of their new account.");
        }
        return user.getEmail();
    }

    public EmailConfirmationView status(User user) {
        return new EmailConfirmationView(user.getEmail(), user.getEmailVerifiedAt() != null);
    }

    private void issue(User user, LocalDateTime now) {
        tokenRepository.retireOpenTokensForUser(user.getId(), now);
        String rawToken = SecureTokens.newHexToken();
        EmailConfirmationToken token = new EmailConfirmationToken();
        token.setUser(user);
        token.setEmail(user.getEmail());
        token.setTokenHash(SecureTokens.sha256Hex(rawToken));
        token.setExpiresAt(now.plus(LINK_TTL));
        tokenRepository.save(token);
        emailDispatcher.enqueueSignupConfirmation(user.getEmail(), user.getDisplayName(),
                emailProperties.resolveFrontendUrl("confirm-email")
                        + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8) + "&kind=signup");
    }
}
