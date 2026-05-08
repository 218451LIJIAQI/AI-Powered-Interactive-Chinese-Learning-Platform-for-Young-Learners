package com.example.aichinese.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.aichinese.entity.User;

@Service
public class NoOpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailService.class);

    @Override
    public void sendTeacherApprovalNotice(User recipient, String note) {
        log.info("Email delivery is not configured. Skipping teacher approval email for {}.", recipient.getEmail());
    }

    @Override
    public void sendTeacherRejectionNotice(User recipient, String reason) {
        log.info("Email delivery is not configured. Skipping teacher rejection email for {}.", recipient.getEmail());
    }
}
