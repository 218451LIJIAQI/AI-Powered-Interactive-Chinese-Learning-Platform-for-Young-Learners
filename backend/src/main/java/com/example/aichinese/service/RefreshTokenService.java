package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
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

    private static final int TOKEN_BYTES = 32;
    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final int MAX_USER_AGENT_LENGTH = 255;
    private static final int MAX_IP_LENGTH = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            SecurityProperties securityProperties) {
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.securityProperties = Objects.requireNonNull(securityProperties, "securityProperties");
    }

    @Transactional
    public RefreshTokenIssue issue(User user, String clientIp, String userAgent) {
        Objects.requireNonNull(user, "user");
        String rawToken = generateUniqueRawToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setFamilyId(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(LocalDateTime.now().plus(refreshTokenTtl()));
        refreshToken.setCreatedByIp(truncate(clientIp, MAX_IP_LENGTH));
        refreshToken.setUserAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH));
        refreshTokenRepository.save(refreshToken);
        return new RefreshTokenIssue(rawToken, refreshTokenTtl());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public RefreshSession rotate(String rawToken, String clientIp, String userAgent) {
        String token = TextUtils.trimToNull(rawToken);
        if (token == null) {
            throw unauthorizedRefresh();
        }

        String currentHash = hash(token);
        RefreshToken current = refreshTokenRepository.findByTokenHash(currentHash)
                .orElseThrow(RefreshTokenService::unauthorizedRefresh);
        LocalDateTime now = LocalDateTime.now();

        if (isConsumed(current) || isExpired(current, now)) {
            revokeFamilyAndSession(current, now);
            throw unauthorizedRefresh();
        }

        User user = current.getUser();
        if (user == null || !user.isActive()) {
            revokeFamilyAndSession(current, now);
            throw unauthorizedRefresh();
        }

        String replacementRawToken = generateUniqueRawToken();
        RefreshToken replacement = new RefreshToken();
        replacement.setUser(user);
        replacement.setTokenHash(hash(replacementRawToken));
        replacement.setFamilyId(current.getFamilyId());
        replacement.setExpiresAt(now.plus(refreshTokenTtl()));
        replacement.setCreatedByIp(truncate(clientIp, MAX_IP_LENGTH));
        replacement.setUserAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH));

        current.setRotatedAt(now);
        current.setRevokedAt(now);
        current.setReplacedByTokenHash(replacement.getTokenHash());
        refreshTokenRepository.save(current);
        refreshTokenRepository.save(replacement);

        return new RefreshSession(user, new RefreshTokenIssue(replacementRawToken, refreshTokenTtl()));
    }

    @Transactional
    public void revokeRawToken(String rawToken) {
        String token = TextUtils.trimToNull(rawToken);
        if (token == null) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(token)).ifPresent(refreshToken -> {
            if (refreshToken.getRevokedAt() == null) {
                refreshToken.setRevokedAt(LocalDateTime.now());
                refreshTokenRepository.save(refreshToken);
            }
        });
    }

    @Transactional
    public void revokeActiveTokensForUser(Long userId) {
        if (userId == null) {
            return;
        }
        refreshTokenRepository.revokeActiveTokensForUser(userId, LocalDateTime.now());
    }

    private void revokeFamilyAndSession(RefreshToken refreshToken, LocalDateTime now) {
        refreshTokenRepository.revokeActiveTokenFamily(refreshToken.getFamilyId(), now);
        User user = refreshToken.getUser();
        if (user != null && user.getId() != null) {
            userRepository.findById(user.getId()).ifPresent(lockedUser -> {
                lockedUser.setSessionVersion(lockedUser.getSessionVersion() + 1);
                userRepository.save(lockedUser);
            });
        }
    }

    private String generateUniqueRawToken() {
        for (int attempt = 0; attempt < 5; attempt++) {
            byte[] bytes = new byte[TOKEN_BYTES];
            secureRandom.nextBytes(bytes);
            String candidate = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (!refreshTokenRepository.existsByTokenHash(hash(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique refresh token.");
    }

    private Duration refreshTokenTtl() {
        return Duration.ofMillis(securityProperties.refreshTokenExpiration());
    }

    private static boolean isConsumed(RefreshToken token) {
        return token.getRevokedAt() != null || token.getRotatedAt() != null;
    }

    private static boolean isExpired(RefreshToken token, LocalDateTime now) {
        return token.getExpiresAt() == null || !token.getExpiresAt().isAfter(now);
    }

    private static ResponseStatusException unauthorizedRefresh() {
        return new ResponseStatusException(UNAUTHORIZED, "Refresh session is invalid or expired.");
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX_FORMAT.formatHex(digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable for refresh-token hashing.", exception);
        }
    }

    private static String truncate(String value, int maxLength) {
        String stripped = TextUtils.trimToNull(value);
        if (stripped == null) {
            return null;
        }
        return stripped.length() <= maxLength ? stripped : stripped.substring(0, maxLength);
    }

    public record RefreshSession(User user, RefreshTokenIssue refreshToken) {
    }
}
