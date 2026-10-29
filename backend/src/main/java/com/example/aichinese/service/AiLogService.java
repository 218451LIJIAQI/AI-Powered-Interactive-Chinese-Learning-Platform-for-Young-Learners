package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.ai.AdminAiUsageLogResponse;
import com.example.aichinese.dto.ai.AdminAiUsageSummaryResponse;
import com.example.aichinese.entity.AiChatMessage;
import com.example.aichinese.entity.AiChatSession;
import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiChatSenderRole;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AiChatMessageRepository;
import com.example.aichinese.repository.AiChatSessionRepository;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.account.AccountRoles;
import com.example.aichinese.util.CsvWriter;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

/**
 * Ask SparkBot chat history and the AI-usage log: one row per AI request (who, role, when, feature, result, provider,
 * model, tokens and the length of the prompt and reply, never their text), with the admin totals, the filtered log
 * and its CSV export.
 */
@Service
public class AiLogService {

    /** Most rows one CSV export contains (newest first). */
    public static final int MAX_EXPORT_ROWS = 10_000;

    private static final int ERROR_SUMMARY_LIMIT = 255;
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiChatSessionRepository aiChatSessionRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AuditLogService auditLogService;

    public AiLogService(
            AiUsageLogRepository aiUsageLogRepository,
            AiChatSessionRepository aiChatSessionRepository,
            AiChatMessageRepository aiChatMessageRepository,
            AuditLogService auditLogService) {
        this.aiUsageLogRepository = aiUsageLogRepository;
        this.aiChatSessionRepository = aiChatSessionRepository;
        this.aiChatMessageRepository = aiChatMessageRepository;
        this.auditLogService = auditLogService;
    }

    /** One AI-usage log row; {@code input} and {@code output} are only measured, never stored. */
    public record UsageEntry(
            User user,
            AiFeatureKey feature,
            String language,
            AiRequestStatus status,
            String providerName,
            String modelName,
            Integer tokenCount,
            String input,
            String output,
            String errorSummary,
            String relatedEntityType,
            Long relatedEntityId) {
    }

    @Transactional
    public AiChatSession createChatSession(User user, String languagePreference) {
        AiChatSession session = new AiChatSession();
        session.setUser(user);
        session.setFeatureKey(AiFeatureKey.CHAT_TUTOR);
        session.setLanguagePreference(languagePreference);
        return aiChatSessionRepository.save(session);
    }

    @Transactional
    public AiChatMessage appendChatMessage(AiChatSession session, AiChatSenderRole senderRole, String messageText) {
        AiChatMessage message = new AiChatMessage();
        message.setSession(session);
        message.setSenderRole(senderRole);
        message.setMessageText(messageText);
        return aiChatMessageRepository.save(message);
    }

    @Transactional
    public AiUsageLog logUsage(UsageEntry entry) {
        AiUsageLog usageLog = new AiUsageLog();
        usageLog.setUser(entry.user());
        usageLog.setUserRole(AccountRoles.primary(entry.user()).name());
        usageLog.setFeatureKey(entry.feature());
        usageLog.setLanguagePreference(entry.language());
        usageLog.setStatus(entry.status().name());
        usageLog.setProviderName(entry.providerName());
        usageLog.setModelName(entry.modelName());
        usageLog.setTokenCount(entry.tokenCount());
        usageLog.setInputSummary(contentMetadata(entry.input()));
        usageLog.setOutputSummary(contentMetadata(entry.output()));
        usageLog.setErrorSummary(TextUtils.safeTruncate(TextUtils.trimToNull(entry.errorSummary()), ERROR_SUMMARY_LIMIT));
        usageLog.setRelatedEntityType(entry.relatedEntityType());
        usageLog.setRelatedEntityId(entry.relatedEntityId());
        return aiUsageLogRepository.save(usageLog);
    }

    /** All-time totals by result and the requests of every feature (zero included), for the admin Usage tab. */
    @Transactional(readOnly = true)
    public AdminAiUsageSummaryResponse getUsageSummary() {
        Map<AiFeatureKey, Long> byFeature = new EnumMap<>(AiFeatureKey.class);
        for (AiFeatureKey feature : AiFeatureKey.values()) {
            byFeature.put(feature, 0L);
        }
        long successful = 0;
        long unavailable = 0;
        long failed = 0;
        for (AiUsageLogRepository.FeatureStatusCount row : aiUsageLogRepository.countByFeatureAndStatus()) {
            byFeature.merge(row.getFeatureKey(), row.getCount(), Long::sum);
            switch (AiRequestStatus.valueOf(row.getStatus())) {
                case SUCCESS -> successful += row.getCount();
                case UNAVAILABLE -> unavailable += row.getCount();
                case FAILED -> failed += row.getCount();
            }
        }
        return new AdminAiUsageSummaryResponse(
                successful + unavailable + failed,
                successful,
                unavailable,
                failed,
                byFeature.entrySet().stream()
                        .map(entry -> new AdminAiUsageSummaryResponse.FeatureCount(entry.getKey().name(), entry.getValue()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAiUsageLogResponse> getUsageLogs(UsageFilter filter, int page, int size) {
        return PageResponse.from(
                aiUsageLogRepository.findAll(filter.specification(), PaginationUtils.newestPageable(page, size)),
                AiLogService::toAdminUsageLogResponse);
    }

    /**
     * The filtered log as CSV (at most {@link #MAX_EXPORT_ROWS} rows, newest first). Names are the only personal
     * data in it; the export is recorded in the audit log.
     */
    @Transactional
    public String exportUsageLogsCsv(SecurityUserDetails admin, UsageFilter filter) {
        List<AiUsageLog> rows = aiUsageLogRepository.findAll(
                filter.specification(),
                PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .getContent();
        CsvWriter csv = new CsvWriter().row(List.of("Time", "User", "Role", "Feature", "Result", "Detail"));
        for (AiUsageLog row : rows) {
            csv.row(Arrays.asList(
                    row.getCreatedAt() == null ? null : CSV_TIME.format(row.getCreatedAt()),
                    row.getUser() == null ? null : row.getUser().getDisplayName(),
                    row.getUserRole(),
                    row.getFeatureKey() == null ? null : row.getFeatureKey().name(),
                    row.getStatus(),
                    row.getErrorSummary()));
        }
        auditLogService.record(admin, "AI_USAGE_LOG_EXPORTED", "AI_USAGE_LOG", null,
                "Admin exported " + rows.size() + " AI usage log rows as CSV.");
        return csv.toString();
    }

    /**
     * The admin log filters: feature, result (SUCCESS / FAILED / UNAVAILABLE) and role (STUDENT / TEACHER); blank
     * values mean "all".
     */
    public record UsageFilter(String feature, String status, String role) {

        public static UsageFilter parse(String feature, String status, String role) {
            return new UsageFilter(
                    parseEnum(feature, AiFeatureKey.class, "Invalid AI feature filter."),
                    parseEnum(status, AiRequestStatus.class, "Invalid AI result filter."),
                    parseEnum(role, RoleName.class, "Invalid user role filter."));
        }

        Specification<AiUsageLog> specification() {
            return (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                if (feature != null) {
                    predicates.add(cb.equal(root.get("featureKey"), AiFeatureKey.valueOf(feature)));
                }
                if (status != null) {
                    predicates.add(cb.equal(root.get("status"), status));
                }
                if (role != null) {
                    predicates.add(cb.equal(root.get("userRole"), role));
                }
                return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
            };
        }

        private static <E extends Enum<E>> String parseEnum(String value, Class<E> type, String message) {
            String trimmed = TextUtils.trimToNull(value);
            if (trimmed == null || "ALL".equalsIgnoreCase(trimmed)) {
                return null;
            }
            String upper = trimmed.toUpperCase(Locale.ROOT);
            for (E constant : type.getEnumConstants()) {
                if (constant.name().equals(upper)) {
                    return upper;
                }
            }
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
    }

    private static AdminAiUsageLogResponse toAdminUsageLogResponse(AiUsageLog logRecord) {
        User user = logRecord.getUser();
        return new AdminAiUsageLogResponse(
                logRecord.getId(),
                user != null ? user.getDisplayName() : null,
                logRecord.getUserRole(),
                logRecord.getFeatureKey().name(),
                logRecord.getStatus(),
                logRecord.getErrorSummary(),
                logRecord.getCreatedAt());
    }

    /** Only the length of student content and AI output is stored, never the text. */
    private static String contentMetadata(String value) {
        String trimmed = TextUtils.trimToNull(value);
        return trimmed == null ? null : "content_length=" + trimmed.length();
    }
}
