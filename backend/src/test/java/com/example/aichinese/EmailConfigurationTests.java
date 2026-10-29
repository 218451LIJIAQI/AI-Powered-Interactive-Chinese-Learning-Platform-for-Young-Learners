package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.example.aichinese.config.EmailConfiguration;
import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.service.EmailService;
import com.example.aichinese.service.NoOpEmailService;
import com.example.aichinese.service.SmtpEmailService;

/**
 * Unit coverage for {@code EmailConfiguration} - proves that
 * {@code app.email.enabled=false} (default) wires the
 * {@link com.example.aichinese.service.NoOpEmailService}, while
 * {@code enabled=true} wires the SMTP-backed
 * {@link com.example.aichinese.service.SmtpEmailService} and fails
 * fast if production disables SMTP or its credentials are malformed.
 */
class EmailConfigurationTests {

    private static final String APP_EMAIL_ENABLED = "app.email.enabled=true";
    private static final String APP_EMAIL_DISABLED = "app.email.enabled=false";
    private static final String PROD_PROFILE = "spring.profiles.active=prod";
    private static final String SMTP_HOST = "spring.mail.host=smtp.example.com";
    private static final String SMTP_PORT_25 = "spring.mail.port=25";
    private static final String SMTP_PORT_587 = "spring.mail.port=587";
    private static final String SMTP_USERNAME = "spring.mail.username=mailer@example.com";
    private static final String SMTP_PASSWORD = "spring.mail.password=secret";
    private static final String SMTP_AUTH_DISABLED = "spring.mail.properties.mail.smtp.auth=false";
    private static final String SMTP_AUTH_ENABLED = "spring.mail.properties.mail.smtp.auth=true";
    private static final String HOST_REQUIRED_MESSAGE =
            "spring.mail.host must be configured when app.email.enabled=true.";
    private static final String USERNAME_REQUIRED_MESSAGE =
            "spring.mail.username must be configured when app.email.enabled=true.";
    private static final String PROD_SMTP_REQUIRED_MESSAGE =
            "SMTP_ENABLED must be true when the prod profile is active.";
    private static final String PROD_PASSWORD_RESET_URL_REQUIRED_MESSAGE =
            "app.email.password-reset-base-url must use HTTPS when app.email.enabled=true and the prod profile is active.";
    private static final String LOCAL_PASSWORD_RESET_BASE_URL =
            "app.email.password-reset-base-url=http://localhost:5174/reset-password";
    private static final String PUBLIC_PASSWORD_RESET_BASE_URL =
            "app.email.password-reset-base-url=https://app.example.com/reset-password";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void defaultsToNoOpEmailServiceWhenEmailIsDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EmailService.class);
            assertThat(context).hasSingleBean(NoOpEmailService.class);
            assertThat(context).doesNotHaveBean(SmtpEmailService.class);
        });
    }

    @Test
    void enablesSmtpEmailServiceWhenSpringMailIsConfigured() {
        contextRunner.withPropertyValues(authenticatedSmtpProperties())
                .run(EmailConfigurationTests::assertSmtpEmailService);
    }

    @Test
    void enablesSmtpEmailServiceWithoutCredentialsWhenSmtpAuthIsDisabled() {
        contextRunner.withPropertyValues(APP_EMAIL_ENABLED, SMTP_HOST, SMTP_PORT_25, SMTP_AUTH_DISABLED)
                .run(EmailConfigurationTests::assertSmtpEmailService);
    }

    @Test
    void failsFastWhenSmtpIsEnabledWithoutMailHost() {
        contextRunner.withPropertyValues(APP_EMAIL_ENABLED, SMTP_USERNAME, SMTP_PASSWORD)
                .run(context -> assertStartupFailure(context, HOST_REQUIRED_MESSAGE));
    }

    @Test
    void failsFastWhenSmtpAuthIsEnabledWithoutCredentials() {
        contextRunner.withPropertyValues(APP_EMAIL_ENABLED, SMTP_HOST, SMTP_AUTH_ENABLED)
                .run(context -> assertStartupFailure(context, USERNAME_REQUIRED_MESSAGE));
    }

    @Test
    void prodProfileRequiresSmtpToBeEnabled() {
        contextRunner.withPropertyValues(PROD_PROFILE, APP_EMAIL_DISABLED)
                .run(context -> assertStartupFailure(context, PROD_SMTP_REQUIRED_MESSAGE));
    }

    @Test
    void prodProfileRequiresPublicHttpsPasswordResetBaseUrlWhenEmailIsEnabled() {
        contextRunner.withPropertyValues(authenticatedSmtpProperties())
                .withPropertyValues(PROD_PROFILE, LOCAL_PASSWORD_RESET_BASE_URL)
                .run(context -> assertStartupFailure(context, PROD_PASSWORD_RESET_URL_REQUIRED_MESSAGE));
    }

    @Test
    void prodProfileAllowsPublicHttpsPasswordResetBaseUrlWhenEmailIsEnabled() {
        contextRunner.withPropertyValues(authenticatedSmtpProperties())
                .withPropertyValues(PROD_PROFILE, PUBLIC_PASSWORD_RESET_BASE_URL)
                .run(EmailConfigurationTests::assertSmtpEmailService);
    }

    private static String[] authenticatedSmtpProperties() {
        return new String[] {
                APP_EMAIL_ENABLED,
                SMTP_HOST,
                SMTP_PORT_587,
                SMTP_USERNAME,
                SMTP_PASSWORD,
                SMTP_AUTH_ENABLED
        };
    }

    private static void assertSmtpEmailService(AssertableApplicationContext context) {
        assertThat(context).hasSingleBean(EmailService.class);
        assertThat(context).hasSingleBean(SmtpEmailService.class);
        assertThat(context).doesNotHaveBean(NoOpEmailService.class);
    }

    private static void assertStartupFailure(AssertableApplicationContext context, String rootCauseMessage) {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure()).hasRootCauseMessage(rootCauseMessage);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EmailProperties.class)
    @Import({EmailConfiguration.class, NoOpEmailService.class, SmtpEmailService.class})
    static class TestConfiguration {
    }
}
