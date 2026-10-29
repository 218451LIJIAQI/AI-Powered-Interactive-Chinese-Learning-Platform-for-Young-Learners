package com.example.aichinese.service;

import com.example.aichinese.entity.User;

public interface EmailService {

    void sendEmail(User recipient, String subject, String body);
}
