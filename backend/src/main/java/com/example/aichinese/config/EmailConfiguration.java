package com.example.aichinese.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

@Configuration(proxyBeanMethods = false)
public class EmailConfiguration {

    private static final Set<String> SUPPORTED_PROD_PROTOCOLS = Set.of("smtp", "smtps");
    /** Custom socket factories bypass the default certificate validation of the TLS connection. */
    private static final List<String> SOCKET_FACTORY_SUFFIXES = List.of(
            ".ssl.socketFactory.class",
            ".socketFactory.class");

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
            // Checked with e-mail off as well: the reset, confirmation and invite links are built either way, and a bad
            // value would otherwise fail "forgot password" for registered addresses only, telling the caller which
            // addresses have an account.
            String passwordResetBaseUrl = emailProperties.resolvePasswordResetBaseUrl();
            if (productionProfileActive) { // e-mail is on here (checked above)
                HttpUrlValidation.requirePublicHttpsUrl(
                        passwordResetBaseUrl,
                        "app.email.password-reset-base-url",
                        "when app.email.enabled=true and the prod profile is active");
            }
        };
    }

    private static void validateSmtpConfiguration(
            Environment environment,
            EmailProperties emailProperties,
            boolean productionProfileActive) {
        String rawProtocol = environment.getProperty("spring.mail.protocol");
        // Jakarta Mail resolves transport providers case-sensitively and without trimming ("smtp" / "smtps").
        String protocol = rawProtocol == null ? "smtp" : rawProtocol;
        if (productionProfileActive && !SUPPORTED_PROD_PROTOCOLS.contains(protocol)) {
            throw new IllegalStateException(
                    "spring.mail.protocol must be lowercase smtp or smtps when the prod profile is active.");
        }
        // Bind spring.mail.properties through Spring Boot's Binder (matching MailProperties / JavaMailSenderImpl)
        // so bracketed map keys like spring.mail.properties.[mail.smtp.ssl.trust] cannot bypass this check.
        Map<String, String> mailProperties = Binder.get(environment)
                .bind("spring.mail.properties", Bindable.mapOf(String.class, String.class))
                .orElse(Map.of());
        String keyPrefix = "mail." + protocol;
        String propertyPrefix = "spring.mail.properties." + keyPrefix;

        String host = environment.getProperty("spring.mail.host");
        requireText("spring.mail.host", host);

        String username = environment.getProperty("spring.mail.username");
        String password = environment.getProperty("spring.mail.password");
        boolean smtpAuth = jakartaBoolean(mailProperties, keyPrefix + ".auth", true);
        if (smtpAuth) {
            requireText("spring.mail.username", username);
            requireText("spring.mail.password", password);
        }

        // Refuses a From address (or its SMTP username fallback) that is not one clean header line.
        String resolvedFrom = emailProperties.resolveFrom(username);
        if (!productionProfileActive) {
            return;
        }
        rejectPlaceholder("spring.mail.host", host);
        if (smtpAuth) {
            rejectPlaceholder("spring.mail.username", username);
            rejectPlaceholder("spring.mail.password", password);
        }
        rejectPlaceholder("app.email.from", resolvedFrom);
        if (!StringUtils.hasText(emailProperties.from()) && !StringUtils.hasText(username)) {
            throw new IllegalStateException(
                    "app.email.from must be configured when the prod profile is active without an SMTP username.");
        }
        validateInternetAddress(resolvedFrom);
        boolean encryptedTransport;
        boolean checkServerIdentity;
        if ("smtps".equals(protocol)) {
            encryptedTransport = true;
            // Angus Mail defaults mail.smtps.ssl.checkserveridentity to true for implicit TLS.
            checkServerIdentity = jakartaBoolean(mailProperties, keyPrefix + ".ssl.checkserveridentity", true);
        } else {
            boolean startTlsEnabled = jakartaBoolean(mailProperties, keyPrefix + ".starttls.enable", false);
            boolean startTlsRequired = jakartaBoolean(mailProperties, keyPrefix + ".starttls.required", false);
            boolean sslEnabled = jakartaBoolean(mailProperties, keyPrefix + ".ssl.enable", false);
            encryptedTransport = (startTlsEnabled && startTlsRequired) || sslEnabled;
            checkServerIdentity = jakartaBoolean(mailProperties, keyPrefix + ".ssl.checkserveridentity", false);
        }
        if (!encryptedTransport || !checkServerIdentity) {
            throw new IllegalStateException(
                    "SMTP TLS with server identity verification must remain enabled when the prod profile is active.");
        }
        // Jakarta Mail trusts '*' (every host) or each host listed in ssl.trust without validating its certificate
        // chain, and a socket factory replaces the certificate checks altogether: either would leave at most the
        // host-name check.
        if (StringUtils.hasText(mailProperties.get(keyPrefix + ".ssl.trust"))) {
            throw new IllegalStateException(
                    propertyPrefix + ".ssl.trust must not be set when the prod profile is active.");
        }
        for (String suffix : SOCKET_FACTORY_SUFFIXES) {
            if (StringUtils.hasText(mailProperties.get(keyPrefix + suffix))) {
                throw new IllegalStateException(
                        propertyPrefix + suffix + " must not be set when the prod profile is active.");
            }
        }
    }

    /**
     * Matches Jakarta / Angus Mail's {@code PropUtil.getBoolean}: untrimmed, case-insensitive {@code "true"} when the
     * default is false, and anything other than {@code "false"} when the default is true.
     */
    private static boolean jakartaBoolean(Map<String, String> properties, String key, boolean defaultValue) {
        String value = properties.get(key);
        if (value == null) {
            return defaultValue;
        }
        return defaultValue ? !"false".equalsIgnoreCase(value) : "true".equalsIgnoreCase(value);
    }

    private static void requireText(String propertyName, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " must be configured when app.email.enabled=true.");
        }
    }

    private static void rejectPlaceholder(String propertyName, String value) {
        if (ProductionProfiles.isPlaceholder(value)) {
            throw new IllegalStateException(
                    propertyName + " must not use a placeholder value when the prod profile is active.");
        }
    }

    private static void validateInternetAddress(String address) {
        try {
            new InternetAddress(address, true);
        } catch (AddressException invalid) {
            throw new IllegalStateException(
                    "Resolved email From address must be a valid email address when the prod profile is active.",
                    invalid);
        }
    }
}
