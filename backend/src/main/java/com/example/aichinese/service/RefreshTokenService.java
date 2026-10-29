package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.dto.auth.RefreshTokenIssue;
import com.example.aichinese.entity.RefreshToken;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.RefreshTokenRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.util.TextUtils;

@Service
public class RefreshTokenService {

    private static final int MAX_USER_AGENT_LENGTH = 255;
    private static final int MAX_IP_LENGTH = 64;
    private static final int MAX_RAW_TOKEN_LENGTH = 512;

    /**
     * Two browser tabs that start together send the same refresh cookie: the first rotates it and the second finds it
     * rotated a moment ago. Inside this window that second request only gets a 401, so the first tab keeps its new
     * session; a rotated token presented later is treated as stolen.
     */
    public static final Duration ROTATION_GRACE_WINDOW = Duration.ofSeconds(30);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            SecurityProperties securityProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.securityProperties = securityProperties;
    }

    /** A new sign-in; {@code persistent} is "Keep me signed in" and is kept by every rotation of this sign-in. */
    @Transactional
    public RefreshTokenIssue issue(User user, String clientIp, String userAgent, boolean persistent) {
        String rawToken = SecureTokens.newRawToken();
        Duration ttl = refreshTokenTtl();
        RefreshToken token = newRefreshToken(
                user, rawToken, UUID.randomUUID().toString(), LocalDateTime.now().plus(ttl), clientIp, userAgent);
        token.setPersistent(persistent);
        refreshTokenRepository.save(token);
        return new RefreshTokenIssue(rawToken, ttl, persistent);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public RefreshSession rotate(String rawToken, String clientIp, String userAgent) {
        String token = TextUtils.trimToNull(rawToken);
        if (token == null || token.length() > MAX_RAW_TOKEN_LENGTH) {
            throw unauthorizedRefresh();
        }

        String currentHash = SecureTokens.sha256Hex(token);
        RefreshToken current = refreshTokenRepository.findLockedByTokenHash(currentHash)
                .orElseThrow(RefreshTokenService::unauthorizedRefresh);
        LocalDateTime now = LocalDateTime.now();

        if (current.getRotatedAt() != null) {
            if (now.isBefore(current.getRotatedAt().plus(ROTATION_GRACE_WINDOW))) {
                // A second tab refreshing with the same cookie: refused, but the sign-in stays valid.
                throw unauthorizedRefresh();
            }
            // Reuse of a rotated token: revoke every token of the same sign-in (SDD 3.4).
            revokeFamilyAndSession(current, now);
            throw unauthorizedRefresh();
        }
        if (current.getRevokedAt() != null || !current.getExpiresAt().isAfter(now)) {
            // Signed out, password changed or expired: not a replay, so the user's newer sessions stay signed in.
            throw unauthorizedRefresh();
        }

        User user = current.getUser();
        if (!user.isActive()) {
            revokeFamilyAndSession(current, now);
            throw unauthorizedRefresh();
        }

        String replacementRawToken = SecureTokens.newRawToken();
        Duration ttl = refreshTokenTtl();
        RefreshToken replacement = newRefreshToken(
                user, replacementRawToken, current.getFamilyId(), now.plus(ttl), clientIp, userAgent);
        replacement.setPersistent(current.isPersistent());

        current.setRotatedAt(now);
        current.setRevokedAt(now);
        current.setReplacedByTokenHash(replacement.getTokenHash());
        refreshTokenRepository.save(current);
        refreshTokenRepository.save(replacement);

        return new RefreshSession(user, new RefreshTokenIssue(replacementRawToken, ttl, current.isPersistent()));
    }

    /** Bumps the session version (existing bearer tokens stop working), saves the user and revokes its refresh tokens. */
    @Transactional
    public User invalidateSessions(User user) {
        rotateSessionVersion(user);
        User saved = userRepository.save(user);
        refreshTokenRepository.revokeActiveTokensForUser(saved.getId(), LocalDateTime.now());
        return saved;
    }

    private void revokeFamilyAndSession(RefreshToken refreshToken, LocalDateTime now) {
        refreshTokenRepository.revokeActiveTokenFamily(refreshToken.getFamilyId(), now);
        userRepository.findLockedById(refreshToken.getUser().getId()).ifPresent(lockedUser -> {
            rotateSessionVersion(lockedUser);
            userRepository.save(lockedUser);
        });
    }

    private Duration refreshTokenTtl() {
        return Duration.ofMillis(securityProperties.refreshTokenExpiration());
    }

    private static RefreshToken newRefreshToken(
            User user, String rawToken, String familyId, LocalDateTime expiresAt, String clientIp, String userAgent) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(SecureTokens.sha256Hex(rawToken));
        token.setFamilyId(familyId);
        token.setExpiresAt(expiresAt);
        token.setCreatedByIp(truncate(clientIp, MAX_IP_LENGTH));
        token.setUserAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH));
        return token;
    }

    private static void rotateSessionVersion(User user) {
        user.setSessionVersion(user.getSessionVersion() + 1);
    }

    private static ResponseStatusException unauthorizedRefresh() {
        return new ResponseStatusException(UNAUTHORIZED, "Refresh session is invalid or expired.");
    }

    private static String truncate(String value, int maxLength) {
        return TextUtils.safeTruncate(TextUtils.trimToNull(value), maxLength);
    }

    public record RefreshSession(User user, RefreshTokenIssue refreshToken) {
    }
}
