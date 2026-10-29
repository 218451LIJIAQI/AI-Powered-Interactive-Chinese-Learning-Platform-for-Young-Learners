package com.example.aichinese.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class EmailConfiguration {

    private static final String PROD_CONTEXT = "when app.email.enabled=true and the prod profile is active";

    @Bean
    InitializingBean emailConfigurationValidator(Environment environment, EmailProperties emailProperties) {
        return () -> {
            boolean productionProfileActive = ProductionProfiles.isActive(environment);
            if (productionProfileActive && !emailProperties.enabled()) {
                throw new IllegalStateException("SMTP_ENABLED must be true when the prod profile is active.");
            }
            if (emailProperties.enabled()) {
                validateSmtpConfiguration(environment, emailProperties, productionProfileActive);
            }
        };
    }

    private static void validateSmtpConfiguration(
            Environment environment,
            EmailProperties emailProperties,
            boolean productionProfileActive) {
        String host = environment.getProperty("spring.mail.host");
        requireText("spring.mail.host", host);

        String username = environment.getProperty("spring.mail.username");
        boolean smtpAuth = parseBooleanProperty(
                environment.getProperty("spring.mail.properties.mail.smtp.auth"),
                true);
        if (smtpAuth) {
            requireText("spring.mail.username", username);
            requireText("spring.mail.password", environment.getProperty("spring.mail.password"));
        }

        emailProperties.resolveFrom(username);
        String passwordResetBaseUrl = emailProperties.resolvePasswordResetBaseUrl();
        if (productionProfileActive) {
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

    private static boolean parseBooleanProperty(String value, boolean defaultValue) {
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }
}
