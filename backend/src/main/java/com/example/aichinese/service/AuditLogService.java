package com.example.aichinese.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AuditLogRepository;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(User actor, String actionType, String targetType, Long targetId, String description) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorUser(actor);
        auditLog.setActionType(actionType);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setDescription(description);
        auditLogRepository.save(auditLog);
    }
}
