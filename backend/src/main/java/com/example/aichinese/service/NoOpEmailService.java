package com.example.aichinese.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.example.aichinese.entity.User;

/**
 * Default {@link EmailService} that logs and discards. Active when
 * {@code app.email.enabled} is {@code false} or absent - so a fresh
 * non-production deployment without SMTP credentials still boots and serves
 * traffic. Production startup requires SMTP so password-reset emails cannot
 * be silently discarded.
 */
@Service
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailService.class);

    @Override
    public void sendEmail(User recipient, String subject, String body) {
        Long userId = recipient != null ? recipient.getId() : null;
        log.info("SMTP not configured. Email sending skipped for user id {} with subject '{}'.", userId, subject);
    }
}
