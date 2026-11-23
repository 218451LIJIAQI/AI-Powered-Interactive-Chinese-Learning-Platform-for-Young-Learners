package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.dto.auth.RefreshTokenIssue;
import com.example.aichinese.entity.RefreshToken;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.RefreshTokenRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.account.AccountGuards;
import com.example.aichinese.util.TextUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
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
    private final EntityManager entityManager;

    /** A new sign-in; AuthService holds the account lock from password verification through this insert. */
    @Transactional
    public RefreshTokenIssue issue(User user, String clientIp, String userAgent, boolean persistent) {
        return saveToken(user, UUID.randomUUID().toString(), persistent, LocalDateTime.now(), clientIp, userAgent);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = ResponseStatusException.class)
    public RefreshSession rotate(String rawToken, String clientIp, String userAgent) {
        String token = TextUtils.trimToNull(rawToken);
        if (token == null || token.length() > MAX_RAW_TOKEN_LENGTH) {
            throw unauthorizedRefresh();
        }

        String currentHash = SecureTokens.sha256Hex(token);
        Long userId = refreshTokenRepository.findUserIdByTokenHash(currentHash)
                .orElseThrow(RefreshTokenService::unauthorizedRefresh);
        User user = userRepository.findById(userId).orElseThrow(RefreshTokenService::unauthorizedRefresh);
        // Match password/email/logout/deletion: account first, then tokens. Refresh bypasses stale managed values.
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        RefreshToken current = refreshTokenRepository.findByTokenHash(currentHash)
                .orElseThrow(RefreshTokenService::unauthorizedRefresh);
        // Single locking SELECT ... FOR UPDATE that also replaces any earlier L1 snapshot.
        entityManager.refresh(current, LockModeType.PESSIMISTIC_WRITE);
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

        if (!AccountGuards.isAvailable(user)) {
            revokeFamilyAndSession(current, now);
            throw unauthorizedRefresh();
        }

        RefreshTokenIssue replacement =
                saveToken(user, current.getFamilyId(), current.isPersistent(), now, clientIp, userAgent);
        current.setRotatedAt(now);
        current.setRevokedAt(now);
        current.setReplacedByTokenHash(SecureTokens.sha256Hex(replacement.rawToken()));
        return new RefreshSession(user, replacement);
    }

    /**
     * Signs one browser out (Account › This device): its sign-in's rotation chain is revoked, so its refresh cookie
     * and access tokens stop working; the account's other sessions stay signed in.
     */
    @Transactional
    public void endSession(User user, String sessionId) {
        refreshTokenRepository.revokeActiveTokenFamilyOfUser(user.getId(), sessionId, LocalDateTime.now());
    }

    /** Whether the browser session an access token names is still signed in (not signed out, not expired). */
    @Transactional(readOnly = true)
    public boolean isSessionActive(Long userId, String sessionId) {
        return refreshTokenRepository.existsByUserIdAndFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(
                userId, sessionId, LocalDateTime.now());
    }

    /** When the browser session started: the first token of its rotation chain; null when unknown. */
    @Transactional(readOnly = true)
    public LocalDateTime sessionStartedAt(Long userId, String sessionId) {
        return sessionId == null ? null : refreshTokenRepository.findSessionStart(userId, sessionId);
    }

    /** Bumps the session version (existing bearer tokens stop working), saves the user and revokes its refresh tokens. */
    @Transactional
    public void invalidateSessions(User user) {
        advanceSessionVersion(user);
        refreshTokenRepository.revokeActiveTokensForUser(user.getId(), LocalDateTime.now());
    }

    /** Account deletion: bearer tokens stop working, and the refresh tokens go with their IP addresses and user agents. */
    @Transactional
    public void removeSessions(User user) {
        advanceSessionVersion(user);
        refreshTokenRepository.deleteByUserId(user.getId());
    }

    /**
     * Revokes active tokens of the sign-in family. Advances {@code sessionVersion} only when at least one active token
     * was revoked, so replaying an already-revoked rotated token cannot repeatedly thrash other active sessions.
     */
    private void revokeFamilyAndSession(RefreshToken refreshToken, LocalDateTime now) {
        User user = refreshToken.getUser();
        int revoked = refreshTokenRepository.revokeActiveTokenFamilyOfUser(user.getId(), refreshToken.getFamilyId(), now);
        if (revoked > 0) {
            advanceSessionVersion(user);
        }
    }

    /** Stores a new token of the browser session {@code sessionId} (its hash; the raw value is for the cookie). */
    private RefreshTokenIssue saveToken(
            User user, String sessionId, boolean persistent, LocalDateTime now, String clientIp, String userAgent) {
        String rawToken = SecureTokens.newRawToken();
        Duration ttl = Duration.ofMillis(securityProperties.refreshTokenExpiration());
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(SecureTokens.sha256Hex(rawToken));
        token.setFamilyId(sessionId);
        token.setExpiresAt(now.plus(ttl));
        token.setCreatedByIp(truncate(clientIp, MAX_IP_LENGTH));
        token.setUserAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH));
        token.setPersistent(persistent);
        refreshTokenRepository.save(token);
        return new RefreshTokenIssue(rawToken, ttl, persistent, sessionId);
    }

    private void advanceSessionVersion(User user) {
        userRepository.save(user);
        // Flush the caller's password/status edits, increment under the DB update lock, and refresh Hibernate's L1
        // snapshot so JWT issuance sees the new sessionVersion without emitting a redundant commit-time UPDATE.
        userRepository.incrementSessionVersion(user.getId(), LocalDateTime.now());
        entityManager.refresh(user);
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
