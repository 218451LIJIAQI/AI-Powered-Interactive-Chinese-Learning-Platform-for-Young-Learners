package com.example.aichinese.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

/**
 * SMTP / outbound-email startup validation.
 *
 * <p>Registers an {@link InitializingBean} that runs after configuration
 * binding but before any application bean opens an SMTP connection. Non-production
 * environments may intentionally use the no-op sender; production must enable
 * SMTP because password reset otherwise issues an unusable reset flow.</p>
 *
 * <p>Production-specific gates additionally require the
 * {@code app.email.password-reset-base-url} to be HTTPS and target a
 * non-loopback host - preventing a stray {@code localhost} URL in a
 * password-reset email from ever being sent to a real user.</p>
 *
 * <p>{@code proxyBeanMethods = false} is intentional: this class
 * defines no inter-bean references, so disabling proxying eliminates
 * the CGLIB subclass at startup.</p>
 */
@Configuration(proxyBeanMethods = false)
public class EmailConfiguration {

    private static final String PROD_CONTEXT = "when app.email.enabled=true and the prod profile is active";
    @Bean
    InitializingBean emailConfigurationValidator(Environment environment, EmailProperties emailProperties) {
        return () -> {
            if (isProductionProfileActive(environment) && !emailProperties.enabled()) {
                throw new IllegalStateException("SMTP_ENABLED must be true when the prod profile is active.");
            }
            if (emailProperties.enabled()) {
                validateSmtpConfiguration(environment, emailProperties);
            }
        };
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
            HttpUrlValidation.requirePublicHttpsUrl(
                    passwordResetBaseUrl,
                    "app.email.password-reset-base-url",
                    PROD_CONTEXT);
        }
    }

    private static void requireText(String propertyName, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " must be configured when app.email.enabled=true.");
        }
    }

    /**
     * {@code prod}/{@code production} resolved via Spring's profile API
     * so {@code spring.profiles.default=prod} (the common Kubernetes
     * pattern) also triggers the fail-fast guard.
     */
    private static final Profiles PROD_PROFILES = Profiles.of("prod", "production");

    private static boolean isProductionProfileActive(Environment environment) {
        return environment.acceptsProfiles(PROD_PROFILES);
    }

}
