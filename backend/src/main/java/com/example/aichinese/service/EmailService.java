package com.example.aichinese.service;

import com.example.aichinese.entity.User;

public interface EmailService {

    void sendTeacherApprovalNotice(User recipient, String note);

    void sendTeacherRejectionNotice(User recipient, String reason);
}
