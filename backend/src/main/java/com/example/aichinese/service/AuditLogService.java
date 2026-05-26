package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.communication.AuditLogResponse;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.util.ExceptionLogUtils;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.RequestIdHolder;
import com.example.aichinese.util.SensitiveDataRedactor;

import jakarta.persistence.criteria.Predicate;

/**
 * Centralised audit-trail writer used by every state-changing service
 * across the platform (auth, payment, refund, payout, teacher
 * approval, content moderation, system settings).
 *
 * <h2>What gets written</h2>
 * <p>Each call captures the actor, action, target type / id, and a
 * human-readable description. The description is run through
 * {@link SensitiveDataRedactor} to redact tokens, passwords, reset
 * links, API keys, email addresses, and {@code Authorization: Bearer}
 * headers
 * — so an admin browsing the audit history cannot lift secrets from
 * the description even if a developer accidentally passed one in.
 * Descriptions are capped at {@link #MAX_AUDIT_DESCRIPTION_LENGTH}
 * to keep rows bounded.</p>
 *
 * <h2>Correlation</h2>
 * <p>The current {@link RequestIdHolder} value is recorded so every
 * audit row joins back to its triggering HTTP request, simplifying
 * incident reconstruction.</p>
 *
 * <h2>Read side</h2>
 * <p>Listing supports JPA {@link Specification}-driven filtering
 * (actor, action, target, date range) so admins can pivot the audit
 * log along any dimension without hand-written query branches.</p>
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final int MAX_AUDIT_DESCRIPTION_LENGTH = 1000;

    private final AuditLogRepository auditLogRepository;
    private final CommunicationMapper communicationMapper;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            CommunicationMapper communicationMapper) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository");
        this.communicationMapper = Objects.requireNonNull(communicationMapper, "communicationMapper");
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
        auditLog.setDescription(sanitizeDescription(description));
        auditLog.setIpAddress(ipAddress);
        auditLog.setRequestId(RequestIdHolder.get());
        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void safeRecord(User actor, String actionType, String targetType, Long targetId, String description, String ipAddress) {
        try {
            record(actor, actionType, targetType, targetId, description, ipAddress);
        } catch (Exception exception) {
            log.warn("Audit log creation failed for action {} on {}:{}: {}",
                    actionType,
                    targetType,
                    targetId,
                    ExceptionLogUtils.safeSummary(exception));
            log.debug("Audit log creation stack trace for action {} on {}:{}.", actionType, targetType, targetId, exception);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogs(String actionType, String targetType, String actor, int page, int size) {
        // DB-side ordering replaces the previous JVM sort over the full
        // audit_log table. Since audit_log grows on every privileged action,
        // pushing the sort down to SQL avoids a worst-case O(N log N) JVM
        // pass plus an additional copy of the entity list per request.
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<AuditLog> auditPage = auditLogRepository.findAll(
                auditLogSpecification(actionType, targetType, actor),
                newestPageable(safePage, safeSize));
        return new PageResponse<>(
                auditPage.getContent().stream()
                        .map(communicationMapper::toAuditLogResponse)
                        .toList(),
                safePage,
                safeSize,
                auditPage.getTotalElements(),
                PaginationUtils.totalPages(auditPage.getTotalElements(), safeSize));
    }

    /**
     * Look up a single audit log by its identifier. Uses the repository's
     * primary-key index instead of streaming the entire table the way the
     * controller previously did.
     */
    @Transactional(readOnly = true)
    public AuditLogResponse getAuditLog(Long id) {
        AuditLog auditLog = auditLogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Audit log was not found."));
        return communicationMapper.toAuditLogResponse(auditLog);
    }

    private Pageable newestPageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private String sanitizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = SensitiveDataRedactor.redactText(description);
        return normalized.length() <= MAX_AUDIT_DESCRIPTION_LENGTH
                ? normalized
                : normalized.substring(0, MAX_AUDIT_DESCRIPTION_LENGTH);
    }

    private Specification<AuditLog> auditLogSpecification(String actionType, String targetType, String actor) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actionType != null && !actionType.isBlank()) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("actionType")), actionType.trim().toLowerCase(Locale.ROOT)));
            }
            if (targetType != null && !targetType.isBlank()) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("targetType")), targetType.trim().toLowerCase(Locale.ROOT)));
            }
            if (actor != null && !actor.isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("actorEmail")), "%" + actor.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
