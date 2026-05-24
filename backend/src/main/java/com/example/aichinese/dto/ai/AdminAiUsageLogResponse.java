package com.example.aichinese.dto.ai;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers (snapshot tests, log
 * fingerprints, frontend code that pins on field positions).</p>
 *
 * <h2>{@code toString()} redaction</h2>
 * <p>The auto-generated record {@code toString()} is overridden so
 * sensitive content — the user's email ({@link #userEmail} PII),
 * the truncated prompt {@link #inputSummary}, the truncated AI
 * response {@link #outputSummary}, and any provider error
 * fragment {@link #errorSummary} — never appear in plain log
 * files or exception traces. The non-sensitive id, role, feature,
 * provider, model, status, token-count and timestamp fields are
 * kept verbatim so the log line still pinpoints the offending
 * row for forensic follow-up via {@link #id}.</p>
 *
 * @see AiAvailabilityResponse for the live provider health snapshot
 */
@JsonPropertyOrder({
        "id",
        "userId",
        "userName",
        "userEmail",
        "userRole",
        "featureKey",
        "providerName",
        "modelName",
        "status",
        "tokenCount",
        "languagePreference",
        "inputSummary",
        "outputSummary",
        "errorSummary",
        "relatedEntityType",
        "relatedEntityId",
        "createdAt"
})
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

    /**
     * Redacts every sensitive content field so a log line that
     * accidentally captures this DTO never leaks PII or sensitive
     * AI content. The non-sensitive id, role, feature, provider,
     * model, status and token-count fields are kept verbatim so
     * the log line still pinpoints the offending row.
     */
    @Override
    public String toString() {
        return "AdminAiUsageLogResponse["
                + "id=" + id
                + ", userId=" + userId
                + ", userName=" + redactValue(userName)
                + ", userEmail=" + redactValue(userEmail)
                + ", userRole=" + userRole
                + ", featureKey=" + featureKey
                + ", providerName=" + providerName
                + ", modelName=" + modelName
                + ", status=" + status
                + ", tokenCount=" + tokenCount
                + ", languagePreference=" + languagePreference
                + ", inputSummary=" + redactLength(inputSummary)
                + ", outputSummary=" + redactLength(outputSummary)
                + ", errorSummary=" + redactLength(errorSummary)
                + ", relatedEntityType=" + relatedEntityType
                + ", relatedEntityId=" + relatedEntityId
                + ", createdAt=" + createdAt
                + "]";
    }

    private static String redactValue(String value) {
        return value == null ? "null" : "<redacted>";
    }

    private static String redactLength(String value) {
        return value == null ? "null" : "<redacted:" + value.length() + ">";
    }
}
