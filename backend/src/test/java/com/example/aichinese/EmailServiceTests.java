package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.service.SmtpEmailService;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/** Unit coverage for {@code SmtpEmailService}. */
@ExtendWith(OutputCaptureExtension.class)
class EmailServiceTests {

    private static final String STUDENT_EMAIL = "student@example.com";
    private static final String FROM_ADDRESS = "no-reply@example.com";
    private static final String SMTP_USERNAME = "smtp-user@example.com";
    private static final String PASSWORD_RESET_BASE_URL = "https://example.com/reset-password";
    private static final String WELCOME_SUBJECT = "Welcome";
    private static final String WELCOME_BODY = "Hello from MandarinSpark";

    @Test
    void smtpEmailServiceSendsPlainTextMessageThroughJavaMailSender() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEmailService service = smtpEmailService(mailSender);

        service.sendEmail(STUDENT_EMAIL, WELCOME_SUBJECT, WELCOME_BODY);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getTo()).containsExactly(STUDENT_EMAIL);
        assertThat(message.getFrom()).isEqualTo(FROM_ADDRESS);
        assertThat(message.getSubject()).isEqualTo(WELCOME_SUBJECT);
        assertThat(message.getText()).isEqualTo(WELCOME_BODY);
    }

    @Test
    void smtpEmailServiceLogsSendFailuresWithoutWarnStackTrace(CapturedOutput output) {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        // A password-reset send can fail with the address and credentials in the provider's message.
        doThrow(new RuntimeException("SMTP failed for " + STUDENT_EMAIL
                + " password=secret reset_token=raw-token Authorization: Bearer raw-bearer"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        SmtpEmailService service = smtpEmailService(mailSender);

        assertThatCode(() -> service.sendEmail(STUDENT_EMAIL, WELCOME_SUBJECT, WELCOME_BODY))
                .doesNotThrowAnyException();

        assertThat(output)
                .contains("Email sending failed", "password=[REDACTED]", "reset_token=[REDACTED]", "Authorization: [REDACTED]")
                .doesNotContain("password=secret", "raw-token", "raw-bearer", STUDENT_EMAIL, "at com.example.aichinese");
    }

    @Test
    void debugDiagnosticsKeepTheFailingFrameWithoutPrintingPrivateMessages(CapturedOutput output) {
        JavaMailSender sender = mock(JavaMailSender.class);
        var providerFailure = new IllegalStateException("private transport detail for " + STUDENT_EMAIL);
        doThrow(new RuntimeException("SMTP password=test-password", providerFailure))
                .when(sender).send(any(SimpleMailMessage.class));
        Logger logger = (Logger) LoggerFactory.getLogger(SmtpEmailService.class);
        Level previousLevel = logger.getLevel();
        try {
            logger.setLevel(Level.DEBUG);
            smtpEmailService(sender).sendEmail(STUDENT_EMAIL, WELCOME_SUBJECT, WELCOME_BODY);
        } finally {
            logger.setLevel(previousLevel);
        }

        assertThat(output).contains("Failure origin java.lang.IllegalStateException", "EmailServiceTests.java")
                .doesNotContain("test-password", "private transport detail", STUDENT_EMAIL);
    }

    private static SmtpEmailService smtpEmailService(JavaMailSender mailSender) {
        EmailProperties emailProperties = new EmailProperties(
                true,
                " " + FROM_ADDRESS + " ",
                PASSWORD_RESET_BASE_URL);
        return new SmtpEmailService(mailSender, emailProperties, SMTP_USERNAME);
    }
}
