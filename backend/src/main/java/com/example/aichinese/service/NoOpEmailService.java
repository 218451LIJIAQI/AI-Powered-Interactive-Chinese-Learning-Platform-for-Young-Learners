package com.example.aichinese.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import com.example.aichinese.entity.User;

@Service
@ConditionalOnExpression(
        "'${app.email.enabled:false}' != 'true' || '${spring.mail.host:}'.trim().length() == 0")
public class NoOpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailService.class);
    private volatile int skippedEmailCount;

    @Override
    public void sendEmail(User recipient, String subject, String body) {
        String email = recipient != null ? recipient.getEmail() : "unknown-recipient";
        skippedEmailCount++;
        log.info("SMTP not configured. Email sending skipped for {} with subject '{}'.", email, subject);
    }

    public int getSkippedEmailCount() {
        return skippedEmailCount;
    }
}
