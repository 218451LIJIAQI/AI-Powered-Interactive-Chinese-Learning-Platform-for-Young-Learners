package com.example.aichinese.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.entity.User;

/**
 * SMTP-backed {@link EmailService} active when
 * {@code app.email.enabled=true}.
 *
 * <p>The {@code @DependsOn("emailConfigurationValidator")} guarantees
 * the fail-fast validator runs before this bean is instantiated, so
 * a malformed SMTP config refuses to start rather than silently
 * mis-sending. Send failures are caught and logged at {@code WARN}
 * — the platform falls back to in-app notifications so a transient
 * SMTP outage cannot block business transactions.</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "true")
@DependsOn("emailConfigurationValidator")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;
    private final String smtpUsername;

    public SmtpEmailService(
            JavaMailSender mailSender,
            EmailProperties emailProperties,
            @Value("${spring.mail.username:}") String smtpUsername) {
        this.mailSender = mailSender;
        this.emailProperties = emailProperties;
        this.smtpUsername = smtpUsername;
    }

    @Override
    public void sendEmail(User recipient, String subject, String body) {
        if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            log.info("Email sending skipped because the recipient email address is missing.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipient.getEmail());
            message.setFrom(emailProperties.resolveFrom(smtpUsername));
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception exception) {
            log.warn("Email sending failed for user id {}. Falling back to in-app notifications only.", recipient.getId(), exception);
        }
    }
}
