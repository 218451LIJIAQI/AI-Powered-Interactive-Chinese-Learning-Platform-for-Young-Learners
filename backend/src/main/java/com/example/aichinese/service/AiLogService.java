package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.ai.AdminAiUsageLogResponse;
import com.example.aichinese.dto.ai.AdminAiUsageSummaryResponse;
import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.EnumLookups;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.util.CsvWriter;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

import lombok.RequiredArgsConstructor;

/**
 * The AI-usage log: one row per AI request (who, role, when, feature, result, provider, model, tokens and the length
 * of the prompt and reply, never their text), with the admin totals, the filtered log and its CSV export.
 */
@Service
@RequiredArgsConstructor
public class AiLogService {

    /** Most rows one CSV export contains (newest first). */
    public static final int MAX_EXPORT_ROWS = 10_000;

    private static final int ERROR_SUMMARY_LIMIT = 255;

    private final AiUsageLogRepository aiUsageLogRepository;
    private final AuditLogService auditLogService;
    private final CurrentAccount currentAccount;
    private final LearnClock clock;

    /** One AI-usage log row; {@code input} and {@code output} are only measured, never stored. */
    public record UsageEntry(
            User user,
            String sessionId,
            AiFeatureKey feature,
            String language,
            AiRequestStatus status,
            String providerName,
            String modelName,
            Integer tokenCount,
            String input,
            String output,
            String errorSummary) {
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AiUsageLog logUsage(UsageEntry entry) {
        User user = currentAccount.lock(entry.user().getId(), entry.user().getSessionVersion(), entry.sessionId());
        AiUsageLog usageLog = new AiUsageLog();
        usageLog.setUser(user);
        usageLog.setUserRole(user.requirePrimaryRole().name());
        usageLog.setFeatureKey(entry.feature());
        usageLog.setLanguagePreference(entry.language());
        usageLog.setStatus(entry.status());
        usageLog.setProviderName(entry.providerName());
        usageLog.setModelName(entry.modelName());
        usageLog.setTokenCount(entry.tokenCount());
        usageLog.setInputSummary(contentMetadata(entry.input()));
        usageLog.setOutputSummary(contentMetadata(entry.output()));
        usageLog.setErrorSummary(TextUtils.safeTruncate(TextUtils.trimToNull(entry.errorSummary()), ERROR_SUMMARY_LIMIT));
        return aiUsageLogRepository.save(usageLog);
    }

    /**
     * All-time totals by result and the requests of every feature that calls the AI provider (zero included), for the
     * admin Usage tab. Hints are built by rules and never logged, so they have no row.
     */
    @Transactional(readOnly = true)
    public AdminAiUsageSummaryResponse getUsageSummary() {
        Map<AiFeatureKey, Long> byFeature = new EnumMap<>(AiFeatureKey.class);
        for (AiFeatureKey feature : AiFeatureKey.values()) {
            if (feature != AiFeatureKey.HINT) {
                byFeature.put(feature, 0L);
            }
        }
        long successful = 0;
        long unavailable = 0;
        long failed = 0;
        for (AiUsageLogRepository.FeatureStatusCount row : aiUsageLogRepository.countByFeatureAndStatus()) {
            byFeature.merge(row.feature(), row.count(), Long::sum);
            switch (row.status()) {
                case SUCCESS -> successful += row.count();
                case UNAVAILABLE -> unavailable += row.count();
                case FAILED -> failed += row.count();
            }
        }
        return new AdminAiUsageSummaryResponse(
                successful + unavailable + failed,
                successful,
                unavailable,
                failed,
                byFeature.entrySet().stream()
                        .map(entry -> new AdminAiUsageSummaryResponse.FeatureCount(entry.getKey(), entry.getValue()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAiUsageLogResponse> getUsageLogs(UsageFilter filter, int page, int size) {
        Page<AdminAiUsageLogResponse> result = PaginationUtils.readPage(PaginationUtils.newestFirst(page, size),
                request -> aiUsageLogRepository.findAdminPage(
                        filter.feature(), filter.status(), filter.roleName(), request));
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    /**
     * The filtered log as CSV (at most {@link #MAX_EXPORT_ROWS} rows, newest first). Names are the only personal
     * data in it; the export is recorded in the audit log.
     */
    @Transactional
    public String exportUsageLogsCsv(SecurityUserDetails admin, UsageFilter filter) {
        PageRequest limit = PageRequest.of(0, MAX_EXPORT_ROWS, PaginationUtils.NEWEST_FIRST);
        List<AdminAiUsageLogResponse> rows = aiUsageLogRepository.findExportRows(
                filter.feature(), filter.status(), filter.roleName(), limit);
        CsvWriter csv = new CsvWriter().row(List.of("Time", "User", "Role", "Feature", "Result", "Detail"));
        for (AdminAiUsageLogResponse row : rows) {
            csv.row(Arrays.asList(clock.platformOffsetDateTimeOf(row.createdAt()), row.userName(), row.userRole(),
                    row.featureKey().name(), row.status(), row.errorSummary()));
        }
        auditLogService.record(admin, "AI_USAGE_LOG_EXPORTED", "AI_USAGE_LOG", null,
                "Admin exported " + rows.size() + " AI usage log rows as CSV.");
        return csv.toString();
    }

    /**
     * The admin log filters: feature, result (SUCCESS / FAILED / UNAVAILABLE) and role (STUDENT, or ADMIN for the
     * lesson quiz drafts); blank values mean "all".
     */
    public record UsageFilter(AiFeatureKey feature, AiRequestStatus status, RoleName role) {

        public static UsageFilter parse(String feature, String status, String role) {
            return new UsageFilter(
                    parseEnum(feature, AiFeatureKey.class, "Invalid AI feature filter."),
                    parseEnum(status, AiRequestStatus.class, "Invalid AI result filter."),
                    parseEnum(role, RoleName.class, "Invalid user role filter."));
        }

        private String roleName() {
            return role == null ? null : role.name();
        }

        private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, String message) {
            String trimmed = TextUtils.trimToNull(value);
            if (trimmed == null || "ALL".equalsIgnoreCase(trimmed)) {
                return null;
            }
            return EnumLookups.tryFrom(type, trimmed)
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, message));
        }
    }

    /** Only the length of student content and AI output is stored, never the text. */
    private static String contentMetadata(String value) {
        String trimmed = TextUtils.trimToNull(value);
        return trimmed == null ? null : "content_length=" + trimmed.length();
    }
}
