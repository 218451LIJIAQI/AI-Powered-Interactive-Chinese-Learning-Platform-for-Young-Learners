package com.example.aichinese.config;

import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Rejects documented placeholder JWT signing secrets and an insecure refresh-token cookie in production.
 *
 * <p>Length validation alone cannot identify a copied example value: a
 * predictable 32-character placeholder signs attacker-forgeable tokens just
 * as successfully as a random key. A refresh cookie without the {@code Secure}
 * attribute would let the long-lived refresh token travel over clear text (NFRSEC07).
 * Local and demo runs keep the examples and the plain-HTTP default
 * {@code JWT_REFRESH_COOKIE_SECURE=false}; an unchanged production template fails closed.</p>
 */
@Component
public class JwtProductionGuard {

    private static final Set<String> DEVELOPMENT_SECRETS = Set.of(
            "LocalComposeOnlyJwtSecret1234567890AB",
            "DemoProfileJwtSecretForFypLocalRun1234567890AB");

    public JwtProductionGuard(Environment environment, SecurityProperties securityProperties) {
        if (!ProductionProfiles.isActive(environment)) {
            return;
        }
        String secret = securityProperties.secret();
        if (ProductionProfiles.isPlaceholder(secret) || DEVELOPMENT_SECRETS.contains(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET must use a deployment-specific signing key when the prod profile is active.");
        }
        if (!securityProperties.refreshTokenCookieSecure()) {
            throw new IllegalStateException(
                    "JWT_REFRESH_COOKIE_SECURE must be true when the prod profile is active.");
        }
    }
}
