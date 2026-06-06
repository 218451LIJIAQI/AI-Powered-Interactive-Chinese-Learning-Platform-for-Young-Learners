package com.example.aichinese.service;

import java.util.Objects;

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
import com.example.aichinese.util.ExceptionLogUtils;
import com.example.aichinese.util.TextUtils;

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
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender");
        this.emailProperties = Objects.requireNonNull(emailProperties, "emailProperties");
        this.smtpUsername = TextUtils.defaultIfBlank(smtpUsername, "");
    }

    @Override
    public void sendEmail(User recipient, String subject, String body) {
        String recipientEmail = recipient == null ? null : TextUtils.trimToNull(recipient.getEmail());
        if (recipientEmail == null) {
            log.info("Email sending skipped because the recipient email address is missing.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setFrom(emailProperties.resolveFrom(smtpUsername));
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception exception) {
            log.warn("Email sending failed for user id {}; falling back to in-app notifications only: {}",
                    recipient.getId(),
                    ExceptionLogUtils.safeSummary(exception));
            log.debug("Email sending stack trace for user id {}.", recipient.getId(), exception);
        }
    }
}
