package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.entity.User;
import com.example.aichinese.security.JwtService;

class JwtServiceTests {

    @Test
    void shouldGenerateTokenWithRuntimeStylePlainTextSecret() {
        JwtService jwtService = new JwtService(new SecurityProperties(
                "RuntimeStyleJwtSecretForTests1234567890AB",
                3_600_000L));

        String token = jwtService.generateToken(buildUser("plain-text@example.com"));

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("plain-text@example.com");
        assertThat(jwtService.isTokenValid(token, "plain-text@example.com")).isTrue();
    }

    @Test
    void shouldGenerateTokenWithExplicitBase64SecretPrefix() {
        String secretBytes = "0123456789abcdefghijklmnopqrstuvwxyzABCDEF";
        String base64Secret = "base64:" + Base64.getEncoder()
                .encodeToString(secretBytes.getBytes(StandardCharsets.UTF_8));
        JwtService jwtService = new JwtService(new SecurityProperties(base64Secret, 3_600_000L));

        String token = jwtService.generateToken(buildUser("base64@example.com"));

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("base64@example.com");
    }

    @Test
    void shouldRejectShortSecretsWithClearMessage() {
        assertThatThrownBy(() -> new JwtService(new SecurityProperties("too-short-secret", 3_600_000L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret is too short");
    }

    private User buildUser(String email) {
        User user = new User();
        user.setId(99L);
        user.setEmail(email);
        user.setDisplayName("JWT Test User");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setActive(true);
        return user;
    }
}
