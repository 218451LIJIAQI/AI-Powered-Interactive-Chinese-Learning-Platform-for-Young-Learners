package com.example.aichinese;

import static com.example.aichinese.support.ConfigFixtures.securityProperties;
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
 * replacement. These tests pin the locking repository call, the
 * compromised-family session invalidation path and the short grace window
 * in which a second tab presenting the just-rotated token is only refused.</p>
 */
class RefreshTokenServiceTests {

    private static final long USER_ID = 10L;
    private static final long ACTIVE_SESSION_VERSION = 2L;
    private static final long COMPROMISED_SESSION_VERSION = 4L;
    private static final int SURROGATE_SAFE_PREFIX_LENGTH = 254;
    private static final String REPLAYED_REFRESH_TOKEN = "replayed-refresh-token";
    private static final String CLIENT_IP = "203.0.113.5";
    private static final String USER_AGENT = "JUnit";
    private static final String INVALID_REFRESH_MESSAGE = "Refresh session is invalid or expired.";

    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenService service = new RefreshTokenService(refreshTokenRepository, userRepository,
            securityProperties("RuntimeStyleJwtSecretForRefreshTests1234567890AB", 3_600_000L, false));

    @Test
    void rotateUsesLockedLookupBeforeMintingReplacement() {
        User user = activeUser(ACTIVE_SESSION_VERSION);
        RefreshToken current = activeToken(user);

        when(refreshTokenRepository.findLockedByTokenHash(anyString())).thenReturn(Optional.of(current));

        RefreshTokenService.RefreshSession session = service.rotate("raw-refresh-token", CLIENT_IP, USER_AGENT);

        assertThat(session.user()).isSameAs(user);
        assertThat(session.refreshToken().rawToken()).isNotBlank();
        assertThat(current.getRotatedAt()).isNotNull();
        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(current.getReplacedByTokenHash()).isNotBlank();
        verify(refreshTokenRepository).findLockedByTokenHash(anyString());
    }

    @Test
    void rotateRevokesFamilyAndBumpsLockedUserSessionOnConsumedTokenReplay() {
        User user = activeUser(COMPROMISED_SESSION_VERSION);
        RefreshToken consumed = activeToken(user);
        LocalDateTime rotatedAt = LocalDateTime.now().minus(RefreshTokenService.ROTATION_GRACE_WINDOW).minusSeconds(1);
        consumed.setRotatedAt(rotatedAt);
        consumed.setRevokedAt(rotatedAt);

        when(refreshTokenRepository.findLockedByTokenHash(anyString())).thenReturn(Optional.of(consumed));
        when(userRepository.findLockedById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.rotate(REPLAYED_REFRESH_TOKEN, CLIENT_IP, USER_AGENT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(INVALID_REFRESH_MESSAGE);

        assertThat(user.getSessionVersion()).isEqualTo(COMPROMISED_SESSION_VERSION + 1);
        verify(refreshTokenRepository).revokeActiveTokenFamily(anyString(), any(LocalDateTime.class));
        verify(userRepository).findLockedById(USER_ID);
        verify(userRepository).save(user);
    }

    @Test
    void rotateRefusesJustRotatedTokenWithoutRevokingTheSignIn() {
        User user = activeUser(ACTIVE_SESSION_VERSION);
        RefreshToken justRotated = activeToken(user);
        // A second tab sends the cookie the first tab rotated a few seconds ago.
        LocalDateTime rotatedAt = LocalDateTime.now().minusSeconds(5);
        justRotated.setRotatedAt(rotatedAt);
        justRotated.setRevokedAt(rotatedAt);

        assertRotationRefusedWithoutSigningOut(user, justRotated);
    }

    @Test
    void rotateRejectsRevokedTokenWithoutSigningOutNewerSessions() {
        User user = activeUser(ACTIVE_SESSION_VERSION);
        RefreshToken revoked = activeToken(user);
        // Revoked by sign-out or a password change, never rotated: presenting it again is not a replay.
        revoked.setRevokedAt(LocalDateTime.now().minusMinutes(1));

        assertRotationRefusedWithoutSigningOut(user, revoked);
    }

    @Test
    void rotateRejectsExpiredTokenWithoutSigningOutOtherSessions() {
        User user = activeUser(ACTIVE_SESSION_VERSION);
        RefreshToken expired = activeToken(user);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        assertRotationRefusedWithoutSigningOut(user, expired);
    }

    @Test
    void issueTruncatesUserAgentWithoutSplittingUnicodeSurrogatePair() {
        ArgumentCaptor<RefreshToken> savedToken = ArgumentCaptor.forClass(RefreshToken.class);

        service.issue(activeUser(ACTIVE_SESSION_VERSION), CLIENT_IP,
                "a".repeat(SURROGATE_SAFE_PREFIX_LENGTH) + "😀tail", true);

        verify(refreshTokenRepository).save(savedToken.capture());
        assertThat(savedToken.getValue().getUserAgent())
                .hasSize(SURROGATE_SAFE_PREFIX_LENGTH)
                .doesNotEndWith("\uD83D");
    }

    @Test
    void rotateRejectsOversizedRawTokenBeforeHashLookup() {
        // Raw tokens are at most 512 characters.
        assertThatThrownBy(() -> service.rotate("x".repeat(513), CLIENT_IP, USER_AGENT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(INVALID_REFRESH_MESSAGE);

        verify(refreshTokenRepository, never()).findLockedByTokenHash(anyString());
    }

    private static RefreshToken activeToken(User user) {
        RefreshToken token = new RefreshToken();
        token.setId(100L);
        token.setUser(user);
        token.setTokenHash("existing-token-hash");
        token.setFamilyId("refresh-family-id");
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        return token;
    }

    private static User activeUser(long sessionVersion) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("refresh@example.com");
        user.setDisplayName("Refresh Test User");
        user.setPasswordHash("password-hash");
        user.setActive(true);
        user.setSessionVersion(sessionVersion);
        return user;
    }

    /** The presented token is refused, but the account keeps its other sessions (no family revocation, no sign-out). */
    private void assertRotationRefusedWithoutSigningOut(User user, RefreshToken presented) {
        when(refreshTokenRepository.findLockedByTokenHash(anyString())).thenReturn(Optional.of(presented));

        assertThatThrownBy(() -> service.rotate(REPLAYED_REFRESH_TOKEN, CLIENT_IP, USER_AGENT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(INVALID_REFRESH_MESSAGE);

        assertThat(user.getSessionVersion()).isEqualTo(ACTIVE_SESSION_VERSION);
        verify(refreshTokenRepository, never()).revokeActiveTokenFamily(anyString(), any(LocalDateTime.class));
        verify(userRepository, never()).findLockedById(USER_ID);
    }
}
