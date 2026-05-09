package com.example.aichinese.service;

import org.springframework.stereotype.Service;

import com.example.aichinese.entity.User;

@Service
public class CommerceAuditService {

    private final AuditLogService auditLogService;

    public CommerceAuditService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public void record(User actor, String actionType, String targetType, Long targetId, String description) {
        auditLogService.record(actor, actionType, targetType, targetId, description);
    }
}
