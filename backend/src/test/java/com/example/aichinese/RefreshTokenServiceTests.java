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

    @Test
    void rotateUsesLockedLookupBeforeMintingReplacement() {
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        User user = activeUser(10L, 2L);
        RefreshToken current = activeToken(user);

        when(refreshTokenRepository.findLockedByTokenHash(anyString())).thenReturn(Optional.of(current));
        when(refreshTokenRepository.existsByTokenHash(anyString())).thenReturn(false);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.RefreshSession session = service(refreshTokenRepository, userRepository)
                .rotate("raw-refresh-token", "203.0.113.5", "JUnit");

        assertThat(session.user()).isSameAs(user);
        assertThat(session.refreshToken().rawToken()).isNotBlank();
        assertThat(current.getRotatedAt()).isNotNull();
        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(current.getReplacedByTokenHash()).isNotBlank();
        verify(refreshTokenRepository).findLockedByTokenHash(anyString());
    }

    @Test
    void rotateRevokesFamilyAndBumpsLockedUserSessionOnConsumedTokenReplay() {
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        User user = activeUser(10L, 4L);
        RefreshToken consumed = activeToken(user);
        consumed.setRevokedAt(LocalDateTime.now().minusMinutes(1));

        when(refreshTokenRepository.findLockedByTokenHash(anyString())).thenReturn(Optional.of(consumed));
        when(userRepository.findLockedById(10L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service(refreshTokenRepository, userRepository)
                .rotate("replayed-refresh-token", "203.0.113.5", "JUnit"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Refresh session is invalid or expired.");

        assertThat(user.getSessionVersion()).isEqualTo(5L);
        verify(refreshTokenRepository).revokeActiveTokenFamily(anyString(), any(LocalDateTime.class));
        verify(userRepository).findLockedById(10L);
        verify(userRepository).save(user);
    }

    @Test
    void issueTruncatesUserAgentWithoutSplittingUnicodeSurrogatePair() {
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        User user = activeUser(10L, 2L);
        ArgumentCaptor<RefreshToken> savedToken = ArgumentCaptor.forClass(RefreshToken.class);

        when(refreshTokenRepository.existsByTokenHash(anyString())).thenReturn(false);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String userAgent = "a".repeat(254) + "\uD83D\uDE00tail";
        service(refreshTokenRepository, userRepository).issue(user, "203.0.113.5", userAgent);

        verify(refreshTokenRepository).save(savedToken.capture());
        assertThat(savedToken.getValue().getUserAgent())
                .hasSize(254)
                .doesNotEndWith("\uD83D");
    }

    @Test
    void rotateRejectsOversizedRawTokenBeforeHashLookup() {
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        assertThatThrownBy(() -> service(refreshTokenRepository, userRepository)
                .rotate("x".repeat(513), "203.0.113.5", "JUnit"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Refresh session is invalid or expired.");

        verify(refreshTokenRepository, never()).findLockedByTokenHash(anyString());
    }

    private RefreshTokenService service(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository) {
        return new RefreshTokenService(
                refreshTokenRepository,
                userRepository,
                new SecurityProperties(
                        "RuntimeStyleJwtSecretForRefreshTests1234567890AB",
                        3_600_000L,
                        604_800_000L,
                        "mandarin_spark_refresh",
                        false,
                        "Strict"));
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

    private static User activeUser(Long id, long sessionVersion) {
        User user = new User();
        user.setId(id);
        user.setEmail("refresh@example.com");
        user.setDisplayName("Refresh Test User");
        user.setPasswordHash("password-hash");
        user.setActive(true);
        user.setSessionVersion(sessionVersion);
        return user;
    }
}
