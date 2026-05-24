package com.example.aichinese.dto.ai;

import java.time.LocalDateTime;

/**
 * Admin-facing AI usage log row returned by
 * {@code GET /api/v1/admin/ai-usage-logs} (paginated, filterable
 * by user / feature / status / date).
 *
 * <p>Drives the admin AI ops dashboard — every AI request the
 * platform issues to its upstream provider writes one
 * {@link com.example.aichinese.entity.AiUsageLog} row, surfaced
 * here for cost monitoring, abuse triage, and provider error
 * analysis. The lighter
 * {@link com.example.aichinese.dto.dashboard.AdminDashboardResponse.AiUsageSummary}
 * is the dashboard-roll-up of the same data source.</p>
 *
 * <h2>Cost / quota fields</h2>
 * <ul>
 *   <li>{@link #featureKey} — which surface generated the
 *       request ({@code "VOCABULARY_HELPER"}, {@code "QUIZ_GEN"},
 *       {@code "CHAT"}, etc.). Used for per-feature cost
 *       breakdown.</li>
 *   <li>{@link #providerName} / {@link #modelName} — provider and
 *       model identifiers (e.g. {@code "OPENAI"} /
 *       {@code "gpt-4o-mini"}) so admins can attribute spend
 *       across providers.</li>
 *   <li>{@link #tokenCount} — total tokens consumed (input +
 *       output, normalised by the provider adapter).</li>
 * </ul>
 *
 * <h2>{@link #status}</h2>
 * <p>String form of
 * {@link com.example.aichinese.entity.enums.AiUsageStatus}
 * ({@code "SUCCESS"}, {@code "RATE_LIMITED"},
 * {@code "PROVIDER_ERROR"}, {@code "FILTERED"}, etc.). Drives the
 * admin's "AI status" pie chart on
 * {@link com.example.aichinese.dto.analytics.AdminAnalyticsResponse}.</p>
 *
 * <h2>{@link #inputSummary} / {@link #outputSummary} / {@link #errorSummary}</h2>
 * <p>Truncated snippets of the prompt / response / error message
 * for diagnostic purposes. The raw full bodies are NOT persisted
 * — only the first ~200 chars — to keep the audit log
 * lightweight and to limit retained-PII surface.</p>
 *
 * <h2>{@link #relatedEntityType} / {@link #relatedEntityId}</h2>
 * <p>Optional pointer to the originating row (e.g.
 * {@code "Lesson:42"} when the student invoked AI from a lesson
 * context). {@code null} for free-standing AI usage.</p>
 *
 * @see AiAvailabilityResponse for the live provider health snapshot
 */
public record AdminAiUsageLogResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        String userRole,
        String featureKey,
        String providerName,
        String modelName,
        String status,
        Integer tokenCount,
        String languagePreference,
        String inputSummary,
        String outputSummary,
        String errorSummary,
        String relatedEntityType,
        Long relatedEntityId,
        LocalDateTime createdAt) {
}
