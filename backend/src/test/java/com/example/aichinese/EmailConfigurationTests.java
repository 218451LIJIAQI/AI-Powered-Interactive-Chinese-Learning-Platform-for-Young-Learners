package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.example.aichinese.config.EmailConfiguration;
import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.service.EmailService;
import com.example.aichinese.service.NoOpEmailService;
import com.example.aichinese.service.SmtpEmailService;

/**
 * Unit coverage for {@code EmailConfiguration}: {@code app.email.enabled=false} (the default) wires the
 * {@link NoOpEmailService} and {@code true} the {@link SmtpEmailService}, which requires TLS; start-up fails if
 * production disables SMTP, the SMTP host or credentials are missing, or the link base URL is not an absolute HTTP(S)
 * URL (e-mail on or off).
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
    private static final String PASSWORD_RESET_BASE_URL_WITHOUT_SCHEME =
            "app.email.password-reset-base-url=localhost:5174/reset-password";
    private static final String ABSOLUTE_PASSWORD_RESET_URL_REQUIRED_MESSAGE =
            "Password reset base URL must be an absolute HTTP(S) URL.";

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
    void smtpVerifiesTheTlsServerHostnameByDefault() {
        contextRunner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(authenticatedSmtpProperties())
                .run(context -> {
                    assertSmtpEmailService(context);
                    assertThat(context.getBean(JavaMailSenderImpl.class).getJavaMailProperties())
                            .containsEntry("mail.smtp.starttls.enable", "true")
                            .containsEntry("mail.smtp.ssl.checkserveridentity", "true");
                });
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true"})
    void smtpRequiresTlsUnlessLocalOverrideIsExplicit(boolean advertisesStartTls, boolean allowPlaintext) throws Exception {
        // Loopback-only protocol stub: never accepts credentials or delivers a message.
        // Declare executor before server so try-with-resources closes server first and aborts accept() on early failures.
        try (var executor = Executors.newSingleThreadExecutor();
                ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(5000);
            var commands = executor.submit(() -> rejectUnencryptedSmtp(server, advertisesStartTls));
            var configured = contextRunner.withInitializer(new ConfigDataApplicationContextInitializer())
                    .withPropertyValues(authenticatedSmtpProperties())
                    .withPropertyValues("spring.mail.host=127.0.0.1", "spring.mail.port=" + server.getLocalPort());
            if (allowPlaintext) {
                configured = configured.withPropertyValues("spring.mail.properties.mail.smtp.starttls.required=false");
            }
            configured.run(context -> {
                assertSmtpEmailService(context);
                Throwable failure = catchThrowable(context.getBean(JavaMailSenderImpl.class)::testConnection);
                List<String> received = commands.get(6, TimeUnit.SECONDS);
                assertThat(received).anyMatch(command -> command.startsWith("EHLO "));
                if (allowPlaintext) {
                    assertThat(received).anyMatch(command -> command.startsWith("AUTH "));
                    assertThat(failure).hasMessageContaining("Authentication rejected by test stub");
                } else {
                    assertThat(received).noneMatch(command -> command.startsWith("AUTH "));
                    assertThat(failure).hasMessageContaining("TLS");
                }
            });
        }
    }

    private static List<String> rejectUnencryptedSmtp(ServerSocket server, boolean advertisesStartTls) throws Exception {
        List<String> commands = new ArrayList<>();
        try (var socket = server.accept();
                var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                var output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.US_ASCII)) {
            socket.setSoTimeout(5000);
            output.print("220 loopback SMTP test\r\n");
            output.flush();
            for (String command; (command = input.readLine()) != null;) {
                commands.add(command);
                if (command.startsWith("EHLO ")) {
                    output.print("250-loopback\r\n");
                    if (advertisesStartTls) output.print("250-STARTTLS\r\n");
                    output.print("250 AUTH PLAIN\r\n");
                } else if (command.equals("STARTTLS")) {
                    output.print("454 TLS temporarily unavailable\r\n");
                } else if (command.startsWith("AUTH ")) {
                    output.print("535 Authentication rejected by test stub\r\n");
                } else if (command.equals("QUIT")) {
                    output.print("221 Bye\r\n");
                    output.flush();
                    break;
                } else {
                    throw new IllegalStateException("Unexpected SMTP command: " + command);
                }
                output.flush();
            }
        }
        return commands;
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
    void failsFastOnAnInvalidPasswordResetBaseUrlWhenEmailIsDisabled() {
        // The links are built with e-mail off too; started anyway, "forgot password" would fail for registered
        // addresses only and so tell a caller which addresses have an account.
        contextRunner.withPropertyValues(APP_EMAIL_DISABLED, PASSWORD_RESET_BASE_URL_WITHOUT_SCHEME)
                .run(context -> assertStartupFailure(context, ABSOLUTE_PASSWORD_RESET_URL_REQUIRED_MESSAGE));
    }

    @Test
    void prodProfileRequiresSmtpToBeEnabled() {
        contextRunner.withPropertyValues(PROD_PROFILE, APP_EMAIL_DISABLED)
                .run(context -> assertStartupFailure(context, PROD_SMTP_REQUIRED_MESSAGE));
    }

    @Test
    void prodProfileRequiresPublicHttpsPasswordResetBaseUrlWhenEmailIsEnabled() {
        prodSmtpRunner(LOCAL_PASSWORD_RESET_BASE_URL)
                .run(context -> assertStartupFailure(context, PROD_PASSWORD_RESET_URL_REQUIRED_MESSAGE));
    }

    @Test
    void prodProfileAllowsPublicHttpsPasswordResetBaseUrlWhenEmailIsEnabled() {
        prodSmtpRunner().run(EmailConfigurationTests::assertSmtpEmailService);
    }

    @Test
    void prodProfileRejectsMissingTlsAndWildcardSslTrust() {
        contextRunner.withPropertyValues(authenticatedSmtpProperties())
                .withPropertyValues(PROD_PROFILE, PUBLIC_PASSWORD_RESET_BASE_URL)
                .run(context -> assertStartupFailure(context,
                        "SMTP TLS with server identity verification must remain enabled when the prod profile is active."));

        prodSmtpRunner("spring.mail.properties.mail.smtp.ssl.trust=*")
                .run(context -> assertStartupFailure(context,
                        "spring.mail.properties.mail.smtp.ssl.trust must not be set when the prod profile is active."));
    }

    /** A listed trusted host or a custom socket factory skips certificate-chain validation, so production refuses both. */
    @Test
    void prodProfileRejectsTrustedHostListsAndCustomSocketFactories() {
        prodSmtpRunner("spring.mail.properties.mail.smtp.ssl.trust=smtp.example.com")
                .run(context -> assertStartupFailure(context,
                        "spring.mail.properties.mail.smtp.ssl.trust must not be set when the prod profile is active."));

        prodSmtpRunner("spring.mail.properties.mail.smtp.ssl.socketFactory.class=javax.net.ssl.SSLSocketFactory")
                .run(context -> assertStartupFailure(context, "spring.mail.properties.mail.smtp.ssl.socketFactory.class"
                        + " must not be set when the prod profile is active."));

        // Outside production a local SMTP test server may still be trusted by name.
        contextRunner.withPropertyValues(authenticatedSmtpProperties())
                .withPropertyValues("spring.mail.properties.mail.smtp.ssl.trust=smtp.example.com")
                .run(EmailConfigurationTests::assertSmtpEmailService);
    }

    /**
     * Spring Boot's Binder unwraps bracketed map keys and Jakarta Mail only treats exact "true" (case-insensitive,
     * untrimmed) as true for default-false flags; neither may bypass the production TLS guard.
     */
    @Test
    void prodProfileRejectsBracketedMapKeyBypassesAndNonJakartaBooleanTlsValues() {
        prodSmtpRunner("spring.mail.properties.[mail.smtp.ssl.trust]=*")
                .run(context -> assertStartupFailure(context,
                        "spring.mail.properties.mail.smtp.ssl.trust must not be set when the prod profile is active."));

        prodSmtpRunner("spring.mail.properties.[mail.smtp.ssl.socketFactory.class]=javax.net.ssl.SSLSocketFactory")
                .run(context -> assertStartupFailure(context, "spring.mail.properties.mail.smtp.ssl.socketFactory.class"
                        + " must not be set when the prod profile is active."));

        prodSmtpRunner("spring.mail.properties.[mail.smtp.starttls.required]=false")
                .run(context -> assertStartupFailure(context,
                        "SMTP TLS with server identity verification must remain enabled when the prod profile is active."));

        prodSmtpRunner("spring.mail.properties.[mail.smtp.ssl.checkserveridentity]=false")
                .run(context -> assertStartupFailure(context,
                        "SMTP TLS with server identity verification must remain enabled when the prod profile is active."));

        for (String nonJakartaTrue : List.of("yes", "1", "on", "true ")) {
            prodSmtpRunner()
                    .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(
                            new MapPropertySource("raw-tls-flag",
                                    Map.of("spring.mail.properties.mail.smtp.starttls.required", nonJakartaTrue))))
                    .run(context -> assertStartupFailure(context,
                            "SMTP TLS with server identity verification must remain enabled when the prod profile is active."));
        }
    }

    @Test
    void prodProfileValidatesSmtpsProtocolAndRejectsUnsupportedProtocols() {
        prodSmtpRunner("spring.mail.protocol=smtps")
                .run(context -> {
                    assertSmtpEmailService(context);
                    JavaMailSenderImpl sender = context.getBean(JavaMailSenderImpl.class);
                    assertThat(sender.getJavaMailProperties().getProperty("mail.smtps.auth")).isEqualTo("true");
                    assertThat(sender.getJavaMailProperties().getProperty("mail.smtps.ssl.checkserveridentity"))
                            .isEqualTo("true");
                });

        prodSmtpRunner("spring.mail.protocol=smtps",
                "spring.mail.properties.mail.smtps.ssl.checkserveridentity=false")
                .run(context -> assertStartupFailure(context,
                        "SMTP TLS with server identity verification must remain enabled when the prod profile is active."));

        prodSmtpRunner("spring.mail.protocol=smtps",
                "spring.mail.properties.mail.smtps.ssl.trust=*")
                .run(context -> assertStartupFailure(context,
                        "spring.mail.properties.mail.smtps.ssl.trust must not be set when the prod profile is active."));

        for (String invalidProtocol : List.of("imap", "SMTPS", " smtps ", "")) {
            prodSmtpRunner()
                    .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(
                            new MapPropertySource("raw-mail-protocol", Map.of("spring.mail.protocol", invalidProtocol))))
                    .run(context -> assertStartupFailure(context,
                            "spring.mail.protocol must be lowercase smtp or smtps when the prod profile is active."));
        }
    }

    @Test
    void prodProfileRejectsPlaceholderCredentialsDisabledTlsAndInvalidFromAddress() {
        prodSmtpRunner("spring.mail.password=replace-with-smtp-pass")
                .run(context -> assertStartupFailure(context,
                        "spring.mail.password must not use a placeholder value when the prod profile is active."));

        prodSmtpRunner("spring.mail.properties.mail.smtp.starttls.required=false")
                .run(context -> assertStartupFailure(context,
                        "SMTP TLS with server identity verification must remain enabled when the prod profile is active."));

        prodSmtpRunner("spring.mail.username=apikey")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("Resolved email From address must be a valid email address when the prod profile is active.");
                });
    }

    private ApplicationContextRunner prodSmtpRunner(String... extraProperties) {
        return contextRunner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(authenticatedSmtpProperties())
                .withPropertyValues(PROD_PROFILE, PUBLIC_PASSWORD_RESET_BASE_URL)
                .withPropertyValues(extraProperties);
    }

    private static String[] authenticatedSmtpProperties() {
        return new String[] {
                APP_EMAIL_ENABLED,
                "app.email.from=",
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
