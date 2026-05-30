package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.example.aichinese.config.EmailConfiguration;
import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.service.EmailService;
import com.example.aichinese.service.NoOpEmailService;
import com.example.aichinese.service.SmtpEmailService;

/**
 * Unit coverage for {@code EmailConfiguration} — proves that
 * {@code app.email.enabled=false} (default) wires the
 * {@link com.example.aichinese.service.NoOpEmailService}, while
 * {@code enabled=true} wires the SMTP-backed
 * {@link com.example.aichinese.service.SmtpEmailService} and fails
 * fast if production disables SMTP or its credentials are malformed.
 */
class EmailConfigurationTests {

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
        contextRunner.withPropertyValues(
                "app.email.enabled=true",
                "spring.mail.host=smtp.example.com",
                "spring.mail.port=587",
                "spring.mail.username=mailer@example.com",
                "spring.mail.password=secret",
                "spring.mail.properties.mail.smtp.auth=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailService.class);
                    assertThat(context).hasSingleBean(SmtpEmailService.class);
                    assertThat(context).doesNotHaveBean(NoOpEmailService.class);
                });
    }

    @Test
    void enablesSmtpEmailServiceWithoutCredentialsWhenSmtpAuthIsDisabled() {
        contextRunner.withPropertyValues(
                "app.email.enabled=true",
                "spring.mail.host=smtp.example.com",
                "spring.mail.port=25",
                "spring.mail.properties.mail.smtp.auth=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailService.class);
                    assertThat(context).hasSingleBean(SmtpEmailService.class);
                    assertThat(context).doesNotHaveBean(NoOpEmailService.class);
                });
    }

    @Test
    void failsFastWhenSmtpIsEnabledWithoutMailHost() {
        contextRunner.withPropertyValues(
                "app.email.enabled=true",
                "spring.mail.username=mailer@example.com",
                "spring.mail.password=secret")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("spring.mail.host must be configured when app.email.enabled=true.");
                });
    }

    @Test
    void failsFastWhenSmtpAuthIsEnabledWithoutCredentials() {
        contextRunner.withPropertyValues(
                "app.email.enabled=true",
                "spring.mail.host=smtp.example.com",
                "spring.mail.properties.mail.smtp.auth=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("spring.mail.username must be configured when app.email.enabled=true.");
                });
    }

    @Test
    void prodProfileRequiresSmtpToBeEnabled() {
        contextRunner.withPropertyValues(
                "spring.profiles.active=prod",
                "app.email.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("SMTP_ENABLED must be true when the prod profile is active.");
                });
    }

    @Test
    void prodProfileRequiresPublicHttpsPasswordResetBaseUrlWhenEmailIsEnabled() {
        contextRunner.withPropertyValues(
                "spring.profiles.active=prod",
                "app.email.enabled=true",
                "app.email.password-reset-base-url=http://localhost:5174/reset-password",
                "spring.mail.host=smtp.example.com",
                "spring.mail.username=mailer@example.com",
                "spring.mail.password=secret",
                "spring.mail.properties.mail.smtp.auth=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("app.email.password-reset-base-url must use HTTPS when app.email.enabled=true and the prod profile is active.");
                });
    }

    @Test
    void prodProfileAllowsPublicHttpsPasswordResetBaseUrlWhenEmailIsEnabled() {
        contextRunner.withPropertyValues(
                "spring.profiles.active=prod",
                "app.email.enabled=true",
                "app.email.password-reset-base-url=https://app.example.com/reset-password",
                "spring.mail.host=smtp.example.com",
                "spring.mail.username=mailer@example.com",
                "spring.mail.password=secret",
                "spring.mail.properties.mail.smtp.auth=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailService.class);
                    assertThat(context).hasSingleBean(SmtpEmailService.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EmailProperties.class)
    @Import({EmailConfiguration.class, NoOpEmailService.class, SmtpEmailService.class})
    static class TestConfiguration {
    }
}
