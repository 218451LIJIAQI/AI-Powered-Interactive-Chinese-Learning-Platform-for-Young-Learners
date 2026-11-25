package com.example.aichinese;

import static com.example.aichinese.support.ConfigFixtures.securityProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/**
 * Unit coverage for the refresh-token rotation boundary.
 *
 * <p>The service must lock the current refresh-token row before checking
 * {@code revokedAt}/{@code rotatedAt}; otherwise two concurrent refresh
 * requests could both observe the same token as active and each mint a
 * replacement. These tests pin the pessimistic write locks, the
 * compromised-family session invalidation path and the short grace window
 * in which a second tab presenting the just-rotated token is only refused.</p>
 */
class RefreshTokenServiceTests {

    private static final long USER_ID = 10L;
    private static final long ACTIVE_SESSION_VERSION = 2L;
    private static final long COMPROMISED_SESSION_VERSION = 4L;
    private static final String REPLAYED_REFRESH_TOKEN = "replayed-refresh-token";
    private static final String INVALID_REFRESH_MESSAGE = "Refresh session is invalid or expired.";

    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final RefreshTokenService service = new RefreshTokenService(refreshTokenRepository, userRepository,
            securityProperties("RuntimeStyleJwtSecretForRefreshTests1234567890AB", 3_600_000L, false), entityManager);

    @Test
    void rotateUsesLockedLookupBeforeMintingReplacement() {
        User user = activeUser(ACTIVE_SESSION_VERSION);
        RefreshToken current = activeToken(user);

        stubToken(current);

        RefreshTokenService.RefreshSession session = service.rotate("raw-refresh-token");

        assertThat(session.user()).isSameAs(user);
        assertThat(session.refreshToken().rawToken()).isNotBlank();
        assertThat(current.getRotatedAt()).isNotNull();
        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(current.getReplacedByTokenHash()).isNotBlank();
        var locks = inOrder(entityManager, refreshTokenRepository);
        locks.verify(entityManager).refresh(user, LockModeType.PESSIMISTIC_WRITE);
        locks.verify(refreshTokenRepository).findByTokenHash(anyString());
        locks.verify(entityManager).refresh(current, LockModeType.PESSIMISTIC_WRITE);
        ArgumentCaptor<RefreshToken> replacement = ArgumentCaptor.forClass(RefreshToken.class);
        locks.verify(refreshTokenRepository).save(replacement.capture());
        assertThat(replacement.getValue()).isNotSameAs(current);
        assertThat(replacement.getValue().getTokenHash()).isEqualTo(current.getReplacedByTokenHash());
    }

    @Test
    void rotateRevokesFamilyAndUsesTheStoredSessionVersionOnConsumedTokenReplay() {
        User user = activeUser(COMPROMISED_SESSION_VERSION);
        RefreshToken consumed = activeToken(user);
        LocalDateTime rotatedAt = LocalDateTime.now().minus(RefreshTokenService.ROTATION_GRACE_WINDOW).minusSeconds(1);
        consumed.setRotatedAt(rotatedAt);
        consumed.setRevokedAt(rotatedAt);

        stubToken(consumed);
        when(refreshTokenRepository.revokeActiveTokenFamilyOfUser(eq(USER_ID), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);
        doAnswer(invocation -> {
            user.setSessionVersion(COMPROMISED_SESSION_VERSION + 1);
            return null;
        }).when(entityManager).refresh(user);

        assertThatThrownBy(() -> service.rotate(REPLAYED_REFRESH_TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(INVALID_REFRESH_MESSAGE);

        assertThat(user.getSessionVersion()).isEqualTo(COMPROMISED_SESSION_VERSION + 1);
        verify(refreshTokenRepository).revokeActiveTokenFamilyOfUser(eq(USER_ID), anyString(), any(LocalDateTime.class));
        var sequence = inOrder(userRepository, entityManager);
        sequence.verify(userRepository).save(user);
        sequence.verify(userRepository).incrementSessionVersion(eq(USER_ID), any(LocalDateTime.class));
        sequence.verify(entityManager).refresh(user);
    }

    @Test
    void rotateDoesNotAdvanceSessionVersionWhenReplayedFamilyWasAlreadyRevoked() {
        User user = activeUser(COMPROMISED_SESSION_VERSION);
        RefreshToken consumed = activeToken(user);
        LocalDateTime rotatedAt = LocalDateTime.now().minus(RefreshTokenService.ROTATION_GRACE_WINDOW).minusSeconds(1);
        consumed.setRotatedAt(rotatedAt);
        consumed.setRevokedAt(rotatedAt);

        stubToken(consumed);
        when(refreshTokenRepository.revokeActiveTokenFamilyOfUser(eq(USER_ID), anyString(), any(LocalDateTime.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.rotate(REPLAYED_REFRESH_TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(INVALID_REFRESH_MESSAGE);

        assertThat(user.getSessionVersion()).isEqualTo(COMPROMISED_SESSION_VERSION);
        verify(refreshTokenRepository).revokeActiveTokenFamilyOfUser(eq(USER_ID), anyString(), any(LocalDateTime.class));
        verify(userRepository, never()).incrementSessionVersion(eq(USER_ID), any(LocalDateTime.class));
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
    void rotateRejectsOversizedRawTokenBeforeHashLookup() {
        // Raw tokens are at most 512 characters.
        assertThatThrownBy(() -> service.rotate("x".repeat(513)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(INVALID_REFRESH_MESSAGE);

        verifyNoInteractions(refreshTokenRepository, userRepository, entityManager);
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
        stubToken(presented);

        assertThatThrownBy(() -> service.rotate(REPLAYED_REFRESH_TOKEN))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(INVALID_REFRESH_MESSAGE);

        assertThat(user.getSessionVersion()).isEqualTo(ACTIVE_SESSION_VERSION);
        verify(refreshTokenRepository, never())
                .revokeActiveTokenFamilyOfUser(any(), anyString(), any(LocalDateTime.class));
        verify(userRepository, never()).incrementSessionVersion(eq(USER_ID), any(LocalDateTime.class));
    }

    private void stubToken(RefreshToken token) {
        when(refreshTokenRepository.findUserIdByTokenHash(anyString())).thenReturn(Optional.of(USER_ID));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(token.getUser()));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
    }
}
