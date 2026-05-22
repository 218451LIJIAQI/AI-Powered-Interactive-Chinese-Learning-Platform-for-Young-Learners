package com.example.aichinese.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

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
        emailProperties.resolvePasswordResetBaseUrl();
    }

    private static void requireText(String propertyName, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " must be configured when app.email.enabled=true.");
        }
    }
}
