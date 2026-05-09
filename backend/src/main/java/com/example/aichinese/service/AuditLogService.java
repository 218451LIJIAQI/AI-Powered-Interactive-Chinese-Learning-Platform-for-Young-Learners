package com.example.aichinese.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.communication.AuditLogResponse;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AuditLogRepository;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final CommunicationMapper communicationMapper;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            CommunicationMapper communicationMapper) {
        this.auditLogRepository = auditLogRepository;
        this.communicationMapper = communicationMapper;
    }

    @Transactional
    public void record(User actor, String actionType, String targetType, Long targetId, String description) {
        record(actor, actionType, targetType, targetId, description, null);
    }

    @Transactional
    public void record(User actor, String actionType, String targetType, Long targetId, String description, String ipAddress) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorUser(actor);
        auditLog.setActorEmail(actor != null ? actor.getEmail() : null);
        auditLog.setActionType(actionType);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setDescription(description);
        auditLog.setIpAddress(ipAddress);
        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void safeRecord(User actor, String actionType, String targetType, Long targetId, String description, String ipAddress) {
        try {
            record(actor, actionType, targetType, targetId, description, ipAddress);
        } catch (Exception exception) {
            log.warn("Audit log creation failed for action {} on {}:{}.", actionType, targetType, targetId, exception);
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditLogResponse> getAuditLogs() {
        return auditLogRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(AuditLog::getCreatedAt).reversed())
                .map(communicationMapper::toAuditLogResponse)
                .toList();
    }
}
