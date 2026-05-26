package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.entity.User;
import com.example.aichinese.service.EmailService;
import com.example.aichinese.service.SmtpEmailService;

/**
 * Unit coverage for the {@code EmailService} default helpers —
 * proves the {@code safe*} wrappers swallow delivery failures and
 * log at {@code WARN} so a transient SMTP outage cannot abort the
 * triggering business transaction (teacher approval, etc.).
 */
@ExtendWith(OutputCaptureExtension.class)
class EmailServiceTests {

    @Test
    void teacherApprovalAndRejectionSafeEmailMethodsDoNotPropagateDeliveryFailures(CapturedOutput output) {
        EmailService failingEmailService = (recipient, subject, body) -> {
            throw new RuntimeException("SMTP provider unavailable token=secret-token");
        };
        User recipient = new User();
        recipient.setId(99L);
        recipient.setEmail("teacher@example.com");
        recipient.setDisplayName("Teacher");

        assertThatCode(() -> failingEmailService.safeSendTeacherApprovalNotice(recipient, "Approved"))
                .doesNotThrowAnyException();
        assertThatCode(() -> failingEmailService.safeSendTeacherRejectionNotice(recipient, "Documents incomplete"))
                .doesNotThrowAnyException();

        assertThat(output).contains("Teacher approval email delivery failed for user id 99: RuntimeException");
        assertThat(output).contains("Teacher rejection email delivery failed for user id 99: RuntimeException");
        assertThat(output).contains("token=[REDACTED]");
        assertThat(output).doesNotContain("secret-token");
        assertThat(output).doesNotContain("at com.example.aichinese");
    }

    @Test
    void teacherDecisionEmailsStripUnicodeWhitespaceFromNotesAndReasons() {
        List<String> bodies = new ArrayList<>();
        EmailService capturingEmailService = (recipient, subject, body) -> bodies.add(body);
        User recipient = new User();
        recipient.setId(42L);
        recipient.setEmail("teacher@example.com");

        capturingEmailService.sendTeacherApprovalNotice(recipient, "\u3000Approved after review\u3000");
        capturingEmailService.sendTeacherRejectionNotice(recipient, "\u3000Missing certificate\u3000");

        assertThat(bodies).hasSize(2);
        assertThat(bodies.get(0))
                .contains("Note: Approved after review")
                .doesNotContain("\u3000");
        assertThat(bodies.get(1))
                .contains("Reason: Missing certificate")
                .doesNotContain("\u3000");
    }

    @Test
    void teacherDecisionEmailsOmitBlankOnlyNotesAndReasons() {
        List<String> bodies = new ArrayList<>();
        EmailService capturingEmailService = (recipient, subject, body) -> bodies.add(body);
        User recipient = new User();
        recipient.setId(43L);
        recipient.setEmail("teacher@example.com");

        capturingEmailService.sendTeacherApprovalNotice(recipient, "\u3000");
        capturingEmailService.sendTeacherRejectionNotice(recipient, "\u3000");

        assertThat(bodies).hasSize(2);
        assertThat(bodies.get(0)).doesNotContain("Note:");
        assertThat(bodies.get(1)).doesNotContain("Reason:");
    }

    @Test
    void smtpEmailServiceSendsPlainTextMessageThroughJavaMailSender() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailProperties emailProperties = new EmailProperties(true, " no-reply@example.com ", "https://example.com/reset-password");
        SmtpEmailService service = new SmtpEmailService(mailSender, emailProperties, "smtp-user@example.com");
        User recipient = new User();
        recipient.setId(123L);
        recipient.setEmail("student@example.com");

        service.sendEmail(recipient, "Welcome", "Hello from MandarinSpark");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message).isNotNull();
        assertThat(message.getTo()).containsExactly("student@example.com");
        assertThat(message.getFrom()).isEqualTo("no-reply@example.com");
        assertThat(message.getSubject()).isEqualTo("Welcome");
        assertThat(message.getText()).isEqualTo("Hello from MandarinSpark");
    }

    @Test
    void smtpEmailServiceLogsSendFailuresWithoutWarnStackTrace(CapturedOutput output) {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP provider unavailable password=secret"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        EmailProperties emailProperties = new EmailProperties(true, " no-reply@example.com ", "https://example.com/reset-password");
        SmtpEmailService service = new SmtpEmailService(mailSender, emailProperties, "smtp-user@example.com");
        User recipient = new User();
        recipient.setId(123L);
        recipient.setEmail("student@example.com");

        assertThatCode(() -> service.sendEmail(recipient, "Welcome", "Hello from MandarinSpark"))
                .doesNotThrowAnyException();

        assertThat(output).contains("Email sending failed for user id 123");
        assertThat(output).contains("password=[REDACTED]");
        assertThat(output).doesNotContain("password=secret");
        assertThat(output).doesNotContain("at com.example.aichinese");
    }
}
