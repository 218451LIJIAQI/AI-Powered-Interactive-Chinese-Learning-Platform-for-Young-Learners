package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.entity.RefreshToken;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.RefreshTokenRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.RefreshTokenService;

/**
 * Unit coverage for the refresh-token rotation boundary.
 *
 * <p>The service must lock the current refresh-token row before checking
 * {@code revokedAt}/{@code rotatedAt}; otherwise two concurrent refresh
 * requests could both observe the same token as active and each mint a
 * replacement. These tests pin the locking repository call and the
 * compromised-family session invalidation path.</p>
 */
class RefreshTokenServiceTests {

    private static final long USER_ID = 10L;
    private static final long ACTIVE_SESSION_VERSION = 2L;
    private static final long COMPROMISED_SESSION_VERSION = 4L;
    private static final long ROTATED_SESSION_VERSION = COMPROMISED_SESSION_VERSION + 1;
    private static final long TOKEN_ID = 100L;
    private static final int MAX_RAW_TOKEN_LENGTH = 512;
    private static final int SURROGATE_SAFE_PREFIX_LENGTH = 254;

    private static final String RAW_REFRESH_TOKEN = "raw-refresh-token";
    private static final String REPLAYED_REFRESH_TOKEN = "replayed-refresh-token";
    private static final String CLIENT_IP = "203.0.113.5";
    private static final String USER_AGENT = "JUnit";
    private static final String INVALID_REFRESH_MESSAGE = "Refresh session is invalid or expired.";
    private static final String EXISTING_TOKEN_HASH = "existing-token-hash";
    private static final String REFRESH_FAMILY_ID = "refresh-family-id";
    private static final String USER_EMAIL = "refresh@example.com";
    private static final String DISPLAY_NAME = "Refresh Test User";
    private static final String PASSWORD_HASH = "password-hash";
    private static final String HIGH_SURROGATE = "\uD83D";
    private static final String SURROGATE_PAIR_WITH_TAIL = "\uD83D\uDE00tail";
    private static final String JWT_SECRET = "RuntimeStyleJwtSecretForRefreshTests1234567890AB";
    private static final long JWT_EXPIRATION_MS = 3_600_000L;
    private static final long REFRESH_TOKEN_EXPIRATION_MS = 604_800_000L;
    private static final String REFRESH_TOKEN_COOKIE_NAME = "mandarin_spark_refresh";
    private static final String REFRESH_TOKEN_SAME_SITE = "Strict";
    private static final SecurityProperties SECURITY_PROPERTIES = new SecurityProperties(
            JWT_SECRET,
            JWT_EXPIRATION_MS,
            REFRESH_TOKEN_EXPIRATION_MS,
            REFRESH_TOKEN_COOKIE_NAME,
            false,
            REFRESH_TOKEN_SAME_SITE);

    @Test
    void rotateUsesLockedLookupBeforeMintingReplacement() {
        ServiceFixture fixture = serviceFixture();
        User user = activeUser(ACTIVE_SESSION_VERSION);
        RefreshToken current = activeToken(user);

        when(fixture.refreshTokenRepository().findLockedByTokenHash(anyString())).thenReturn(Optional.of(current));
        when(fixture.refreshTokenRepository().existsByTokenHash(anyString())).thenReturn(false);

        RefreshTokenService.RefreshSession session = fixture.service()
                .rotate(RAW_REFRESH_TOKEN, CLIENT_IP, USER_AGENT);

        assertThat(session.user()).isSameAs(user);
        assertThat(session.refreshToken().rawToken()).isNotBlank();
        assertThat(current.getRotatedAt()).isNotNull();
        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(current.getReplacedByTokenHash()).isNotBlank();
        verify(fixture.refreshTokenRepository()).findLockedByTokenHash(anyString());
    }

    @Test
    void rotateRevokesFamilyAndBumpsLockedUserSessionOnConsumedTokenReplay() {
        ServiceFixture fixture = serviceFixture();
        User user = activeUser(COMPROMISED_SESSION_VERSION);
        RefreshToken consumed = activeToken(user);
        markConsumed(consumed);

        when(fixture.refreshTokenRepository().findLockedByTokenHash(anyString())).thenReturn(Optional.of(consumed));
        when(fixture.userRepository().findLockedById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> fixture.service().rotate(REPLAYED_REFRESH_TOKEN, CLIENT_IP, USER_AGENT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(INVALID_REFRESH_MESSAGE);

        assertThat(user.getSessionVersion()).isEqualTo(ROTATED_SESSION_VERSION);
        verify(fixture.refreshTokenRepository()).revokeActiveTokenFamily(anyString(), any(LocalDateTime.class));
        verify(fixture.userRepository()).findLockedById(USER_ID);
        verify(fixture.userRepository()).save(user);
    }

    @Test
    void issueTruncatesUserAgentWithoutSplittingUnicodeSurrogatePair() {
        ServiceFixture fixture = serviceFixture();
        User user = activeUser(ACTIVE_SESSION_VERSION);
        ArgumentCaptor<RefreshToken> savedToken = ArgumentCaptor.forClass(RefreshToken.class);

        when(fixture.refreshTokenRepository().existsByTokenHash(anyString())).thenReturn(false);

        fixture.service().issue(user, CLIENT_IP, oversizedUserAgentWithSurrogatePair());

        verify(fixture.refreshTokenRepository()).save(savedToken.capture());
        assertThat(savedToken.getValue().getUserAgent())
                .hasSize(SURROGATE_SAFE_PREFIX_LENGTH)
                .doesNotEndWith(HIGH_SURROGATE);
    }

    @Test
    void rotateRejectsOversizedRawTokenBeforeHashLookup() {
        ServiceFixture fixture = serviceFixture();

        assertThatThrownBy(() -> fixture.service()
                .rotate(oversizedRawToken(), CLIENT_IP, USER_AGENT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(INVALID_REFRESH_MESSAGE);

        verify(fixture.refreshTokenRepository(), never()).findLockedByTokenHash(anyString());
    }

    private static ServiceFixture serviceFixture() {
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenService service = new RefreshTokenService(
                refreshTokenRepository,
                userRepository,
                SECURITY_PROPERTIES);
        return new ServiceFixture(refreshTokenRepository, userRepository, service);
    }

    private static RefreshToken activeToken(User user) {
        RefreshToken token = new RefreshToken();
        token.setId(TOKEN_ID);
        token.setUser(user);
        token.setTokenHash(EXISTING_TOKEN_HASH);
        token.setFamilyId(REFRESH_FAMILY_ID);
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        return token;
    }

    private static User activeUser(long sessionVersion) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(USER_EMAIL);
        user.setDisplayName(DISPLAY_NAME);
        user.setPasswordHash(PASSWORD_HASH);
        user.setActive(true);
        user.setSessionVersion(sessionVersion);
        return user;
    }

    private static void markConsumed(RefreshToken token) {
        token.setRevokedAt(LocalDateTime.now().minusMinutes(1));
    }

    private static String oversizedUserAgentWithSurrogatePair() {
        return "a".repeat(SURROGATE_SAFE_PREFIX_LENGTH) + SURROGATE_PAIR_WITH_TAIL;
    }

    private static String oversizedRawToken() {
        return "x".repeat(MAX_RAW_TOKEN_LENGTH + 1);
    }

    private record ServiceFixture(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            RefreshTokenService service) {
    }
}
