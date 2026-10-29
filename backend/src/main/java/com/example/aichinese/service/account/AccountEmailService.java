package com.example.aichinese.service.account;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.dto.account.EmailChangeResponse;
import com.example.aichinese.entity.EmailChangeToken;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.EmailChangeTokenRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.LoginAttemptLimiter;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.AuditLogService.AuditTarget;
import com.example.aichinese.service.RefreshTokenService;
import com.example.aichinese.service.SecureTokens;
import com.example.aichinese.service.TransactionalEmailDispatcher;

/**
 * Changing the sign-in e-mail: the current password proves the owner asks, and a link sent to the new address proves
 * the owner can read it. The address changes only when that link is opened, once and within 60 minutes; opening it
 * signs the account out everywhere, so every device signs in again with the new address.
 */
@Service
public class AccountEmailService {

    private static final Duration LINK_TTL = Duration.ofMinutes(60);
    private static final String INVALID_LINK = "This confirmation link is invalid or has already been used.";

    private final UserRepository userRepository;
    private final EmailChangeTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final TransactionalEmailDispatcher emailDispatcher;
    private final EmailProperties emailProperties;
    private final AuditLogService auditLogService;
    private final RefreshTokenService refreshTokenService;

    public AccountEmailService(
            UserRepository userRepository,
            EmailChangeTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            LoginAttemptLimiter loginAttemptLimiter,
            TransactionalEmailDispatcher emailDispatcher,
            EmailProperties emailProperties,
            AuditLogService auditLogService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.emailDispatcher = emailDispatcher;
        this.emailProperties = emailProperties;
        this.auditLogService = auditLogService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public EmailChangeResponse requestChange(
            SecurityUserDetails currentUser, String rawNewEmail, String currentPassword, String clientIp) {
        User user = userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Authenticated user could not be found."));
        String limiterKey = user.getEmail();
        if (!loginAttemptLimiter.isAllowed(limiterKey, clientIp)) {
            long retrySeconds = Math.max(1L, loginAttemptLimiter.retryAfter(limiterKey, clientIp).toSeconds());
            throw new ResponseStatusException(
                    TOO_MANY_REQUESTS, "Too many failed attempts. Please try again in " + retrySeconds + " seconds.");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            loginAttemptLimiter.recordFailure(limiterKey, clientIp);
            throw new ResponseStatusException(BAD_REQUEST, "Current password is incorrect.");
        }
        loginAttemptLimiter.recordSuccess(limiterKey, clientIp);

        String newEmail = rawNewEmail.strip().toLowerCase(Locale.ROOT);
        if (newEmail.equals(user.getEmail())) {
            throw new ResponseStatusException(BAD_REQUEST, "This is already your email address.");
        }
        if (userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new ResponseStatusException(BAD_REQUEST, "Email is already registered.");
        }

        LocalDateTime now = LocalDateTime.now();
        tokenRepository.retireOpenTokensForUser(user.getId(), now);
        String rawToken = SecureTokens.newHexToken();
        EmailChangeToken token = new EmailChangeToken();
        token.setUser(user);
        token.setNewEmail(newEmail);
        token.setTokenHash(SecureTokens.sha256Hex(rawToken));
        token.setExpiresAt(now.plus(LINK_TTL));
        tokenRepository.save(token);

        emailDispatcher.enqueueEmailChangeConfirmation(newEmail, emailProperties.resolveFrontendUrl("confirm-email")
                + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8));
        auditLogService.record(user, "EMAIL_CHANGE_REQUESTED",
                new AuditTarget("User", user.getId(), user.getDisplayName()), null,
                "User asked to change their email address; a confirmation link was sent to the new address.");
        return new EmailChangeResponse(newEmail);
    }

    /** Opens the link: the new address replaces the old one and every session is signed out. */
    @Transactional
    public String confirm(String rawToken) {
        EmailChangeToken token = tokenRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken.strip()))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, INVALID_LINK));
        LocalDateTime now = LocalDateTime.now();
        if (token.getUsedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, INVALID_LINK);
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(BAD_REQUEST, "This confirmation link has expired. Ask for a new one in Account.");
        }
        User user = token.getUser();
        if (!user.isActive() || user.getDeletedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, INVALID_LINK);
        }
        if (userRepository.existsByEmailIgnoreCase(token.getNewEmail())) {
            throw new ResponseStatusException(BAD_REQUEST, "Email is already registered.");
        }

        String previousEmail = user.getEmail();
        token.setUsedAt(now);
        tokenRepository.save(token);
        user.setEmail(token.getNewEmail());
        // The link went to the new address, so opening it confirms that address too.
        user.setEmailVerifiedAt(now);
        refreshTokenService.invalidateSessions(user);
        auditLogService.record(user, "EMAIL_CHANGED",
                new AuditTarget("User", user.getId(), user.getDisplayName()), null,
                "User confirmed a new email address (previously " + previousEmail + ").");
        return user.getEmail();
    }
}
