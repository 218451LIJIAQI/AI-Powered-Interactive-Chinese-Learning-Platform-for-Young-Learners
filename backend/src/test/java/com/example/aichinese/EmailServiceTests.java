package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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

/** Unit coverage for the {@code EmailService} default message helpers and {@code SmtpEmailService}. */
@ExtendWith(OutputCaptureExtension.class)
class EmailServiceTests {

    private static final long STUDENT_ID = 123L;
    private static final String TEACHER_EMAIL = "teacher@example.com";
    private static final String STUDENT_EMAIL = "student@example.com";
    private static final String FROM_ADDRESS = "no-reply@example.com";
    private static final String SMTP_USERNAME = "smtp-user@example.com";
    private static final String PASSWORD_RESET_BASE_URL = "https://example.com/reset-password";
    private static final String WELCOME_SUBJECT = "Welcome";
    private static final String WELCOME_BODY = "Hello from MandarinSpark";

    @Test
    void teacherDecisionEmailsStripUnicodeWhitespaceFromNotesAndReasons() {
        CapturingEmailService capturingEmailService = new CapturingEmailService();
        User recipient = user(42L, TEACHER_EMAIL);

        capturingEmailService.sendTeacherApprovalNotice(recipient, "\u3000Approved after review\u3000");
        capturingEmailService.sendTeacherRejectionNotice(recipient, "\u3000Missing certificate\u3000");

        assertThat(capturingEmailService.bodies()).hasSize(2);
        assertThat(capturingEmailService.bodies().get(0))
                .contains("Note: Approved after review")
                .doesNotContain("\u3000");
        assertThat(capturingEmailService.bodies().get(1))
                .contains("Reason: Missing certificate")
                .doesNotContain("\u3000");
    }

    @Test
    void teacherDecisionEmailsOmitBlankOnlyNotesAndReasons() {
        CapturingEmailService capturingEmailService = new CapturingEmailService();
        User recipient = user(43L, TEACHER_EMAIL);

        capturingEmailService.sendTeacherApprovalNotice(recipient, "\u3000");
        capturingEmailService.sendTeacherRejectionNotice(recipient, "\u3000");

        assertThat(capturingEmailService.bodies()).hasSize(2);
        assertThat(capturingEmailService.bodies().get(0)).doesNotContain("Note:");
        assertThat(capturingEmailService.bodies().get(1)).doesNotContain("Reason:");
    }

    @Test
    void smtpEmailServiceSendsPlainTextMessageThroughJavaMailSender() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEmailService service = smtpEmailService(mailSender);
        User recipient = user(STUDENT_ID, STUDENT_EMAIL);

        service.sendEmail(recipient, WELCOME_SUBJECT, WELCOME_BODY);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message).isNotNull();
        assertThat(message.getTo()).containsExactly(STUDENT_EMAIL);
        assertThat(message.getFrom()).isEqualTo(FROM_ADDRESS);
        assertThat(message.getSubject()).isEqualTo(WELCOME_SUBJECT);
        assertThat(message.getText()).isEqualTo(WELCOME_BODY);
    }

    @Test
    void smtpEmailServiceLogsSendFailuresWithoutWarnStackTrace(CapturedOutput output) {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new RuntimeException("SMTP provider unavailable password=secret"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        SmtpEmailService service = smtpEmailService(mailSender);
        User recipient = user(STUDENT_ID, STUDENT_EMAIL);

        assertThatCode(() -> service.sendEmail(recipient, WELCOME_SUBJECT, WELCOME_BODY))
                .doesNotThrowAnyException();

        assertThat(output).contains("Email sending failed for user id " + STUDENT_ID);
        assertThat(output).contains("password=[REDACTED]");
        assertThat(output).doesNotContain("password=secret");
        assertThat(output).doesNotContain("at com.example.aichinese");
    }

    private static SmtpEmailService smtpEmailService(JavaMailSender mailSender) {
        EmailProperties emailProperties = new EmailProperties(
                true,
                " " + FROM_ADDRESS + " ",
                PASSWORD_RESET_BASE_URL);
        return new SmtpEmailService(mailSender, emailProperties, SMTP_USERNAME);
    }

    private static User user(long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private static final class CapturingEmailService implements EmailService {
        private final List<String> bodies = new ArrayList<>();

        @Override
        public void sendEmail(User recipient, String subject, String body) {
            bodies.add(body);
        }

        private List<String> bodies() {
            return bodies;
        }
    }
}
