package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.entity.User;
import com.example.aichinese.service.EmailService;
import com.example.aichinese.service.SmtpEmailService;

class EmailServiceTests {

    @Test
    void teacherApprovalAndRejectionSafeEmailMethodsDoNotPropagateDeliveryFailures() {
        EmailService failingEmailService = (recipient, subject, body) -> {
            throw new RuntimeException("SMTP provider unavailable");
        };
        User recipient = new User();
        recipient.setEmail("teacher@example.com");
        recipient.setDisplayName("Teacher");

        assertThatCode(() -> failingEmailService.safeSendTeacherApprovalNotice(recipient, "Approved"))
                .doesNotThrowAnyException();
        assertThatCode(() -> failingEmailService.safeSendTeacherRejectionNotice(recipient, "Documents incomplete"))
                .doesNotThrowAnyException();
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
}
