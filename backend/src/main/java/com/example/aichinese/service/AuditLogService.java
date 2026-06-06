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
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.ExceptionLogUtils;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.RequestIdHolder;
import com.example.aichinese.util.SensitiveDataRedactor;
import com.example.aichinese.util.TextUtils;

import jakarta.persistence.criteria.Predicate;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final int MAX_AUDIT_DESCRIPTION_LENGTH = 1000;

    private final AuditLogRepository auditLogRepository;
    private final CommunicationMapper communicationMapper;
    private final UserRepository userRepository;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            CommunicationMapper communicationMapper,
            UserRepository userRepository) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository");
        this.communicationMapper = Objects.requireNonNull(communicationMapper, "communicationMapper");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    @Transactional
    public void record(User actor, String actionType, String targetType, Long targetId, String description) {
        record(actor, actionType, targetType, targetId, description, null);
    }

    @Transactional
    public void record(SecurityUserDetails actor, String actionType, String targetType, Long targetId, String description) {
        User actorReference = actor == null || actor.getUserId() == null
                ? null
                : userRepository.getReferenceById(actor.getUserId());
        record(actorReference, actor == null ? null : actor.getUsername(),
                actionType, targetType, targetId, description, null);
    }

    @Transactional
    public void record(User actor, String actionType, String targetType, Long targetId, String description, String ipAddress) {
        record(actor, actor == null ? null : actor.getEmail(),
                actionType, targetType, targetId, description, ipAddress);
    }

    private void record(
            User actor,
            String actorEmail,
            String actionType,
            String targetType,
            Long targetId,
            String description,
            String ipAddress) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorUser(actor);
        auditLog.setActorEmail(actorEmail);
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

    @Transactional(readOnly = true)
    public AuditLogResponse getAuditLog(Long id) {
        AuditLog auditLog = auditLogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Audit log was not found."));
        return communicationMapper.toAuditLogResponse(auditLog);
    }

    private static Pageable newestPageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private static String sanitizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = SensitiveDataRedactor.redactText(description);
        return truncate(normalized, MAX_AUDIT_DESCRIPTION_LENGTH);
    }

    private Specification<AuditLog> auditLogSpecification(String actionType, String targetType, String actor) {
        String normalizedActionType = normalizeFilter(actionType);
        String normalizedTargetType = normalizeFilter(targetType);
        String normalizedActor = normalizeFilter(actor);
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (normalizedActionType != null) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("actionType")), normalizedActionType));
            }
            if (normalizedTargetType != null) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("targetType")), normalizedTargetType));
            }
            if (normalizedActor != null) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("actorEmail")), "%" + normalizedActor + "%"));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String normalizeFilter(String value) {
        String trimmed = TextUtils.trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        int end = Character.isHighSurrogate(value.charAt(maxLength - 1))
                ? maxLength - 1
                : maxLength;
        return value.substring(0, end);
    }
}
