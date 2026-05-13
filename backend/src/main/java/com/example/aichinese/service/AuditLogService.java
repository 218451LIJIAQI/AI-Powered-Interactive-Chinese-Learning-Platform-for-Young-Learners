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
        // DB-side ordering replaces the previous JVM sort over the full
        // audit_log table. Since audit_log grows on every privileged action,
        // pushing the sort down to SQL avoids a worst-case O(N log N) JVM
        // pass plus an additional copy of the entity list per request.
        return auditLogRepository.findAll(
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(communicationMapper::toAuditLogResponse)
                .toList();
    }

    /**
     * Look up a single audit log by its identifier. Uses the repository's
     * primary-key index instead of streaming the entire table the way the
     * controller previously did.
     */
    @Transactional(readOnly = true)
    public AuditLogResponse getAuditLog(Long id) {
        AuditLog auditLog = auditLogRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Audit log was not found."));
        return communicationMapper.toAuditLogResponse(auditLog);
    }
}
