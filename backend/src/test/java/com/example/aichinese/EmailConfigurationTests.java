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

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EmailProperties.class)
    @Import({EmailConfiguration.class, NoOpEmailService.class, SmtpEmailService.class})
    static class TestConfiguration {
    }
}
