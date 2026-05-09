package com.example.aichinese.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.example.aichinese.config.EmailProperties;
import com.example.aichinese.entity.User;

@Service
@ConditionalOnExpression(
        "'${app.email.enabled:false}' == 'true' && '${spring.mail.host:}'.trim().length() > 0")
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
            log.warn("Email sending failed for {}. Falling back to in-app notifications only.", recipient.getEmail(), exception);
        }
    }
}
