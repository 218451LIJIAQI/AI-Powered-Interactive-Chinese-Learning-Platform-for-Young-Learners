package com.example.aichinese.config;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Conditional SMTP / outbound-email validation, only active when
 * {@code app.email.enabled=true}.
 *
 * <p>Registers an {@link InitializingBean} that runs after configuration
 * binding but before any application bean opens an SMTP connection,
 * verifying the {@code spring.mail.*} properties match what the email
 * service needs. Failures throw {@link IllegalStateException} so a
 * mis-configured email pipeline cannot reach production unnoticed.</p>
 *
 * <p>Production-specific gates additionally require the
 * {@code app.email.password-reset-base-url} to be HTTPS and target a
 * non-loopback host — preventing a stray {@code localhost} URL in a
 * password-reset email from ever being sent to a real user.</p>
 *
 * <p>{@code proxyBeanMethods = false} is intentional: this class
 * defines no inter-bean references, so disabling proxying eliminates
 * the CGLIB subclass at startup.</p>
 */
@Configuration(proxyBeanMethods = false)
public class EmailConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "true")
    InitializingBean emailConfigurationValidator(Environment environment, EmailProperties emailProperties) {
        return () -> validateSmtpConfiguration(environment, emailProperties);
    }

    private static void validateSmtpConfiguration(Environment environment, EmailProperties emailProperties) {
        String host = environment.getProperty("spring.mail.host");
        requireText("spring.mail.host", host);

        String username = environment.getProperty("spring.mail.username");
        boolean smtpAuth = Boolean.parseBoolean(
                environment.getProperty("spring.mail.properties.mail.smtp.auth", "true").trim());
        if (smtpAuth) {
            requireText("spring.mail.username", username);
            requireText("spring.mail.password", environment.getProperty("spring.mail.password"));
        }

        emailProperties.resolveFrom(username);
        String passwordResetBaseUrl = emailProperties.resolvePasswordResetBaseUrl();
        if (isProductionProfileActive(environment)) {
            requireProductionPasswordResetBaseUrl(passwordResetBaseUrl);
        }
    }

    private static void requireText(String propertyName, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " must be configured when app.email.enabled=true.");
        }
    }

    private static boolean isProductionProfileActive(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));
    }

    private static void requireProductionPasswordResetBaseUrl(String value) {
        URI uri = URI.create(value);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().trim().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().trim().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme)) {
            throw new IllegalStateException(
                    "app.email.password-reset-base-url must use HTTPS when app.email.enabled=true and the prod profile is active.");
        }
        if (host.isBlank()
                || "localhost".equals(host)
                || "0.0.0.0".equals(host)
                || "::1".equals(host)
                || host.startsWith("127.")) {
            throw new IllegalStateException(
                    "app.email.password-reset-base-url must target a non-loopback public host when app.email.enabled=true and the prod profile is active.");
        }
    }
}
