package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cost / reliability telemetry row for one upstream AI request.
 *
 * <p>Written by {@code AiService} after every call to the AI provider,
 * regardless of outcome. The admin dashboard aggregates these rows to
 * surface daily token spend, error rates, and per-feature popularity
 * histograms.</p>
 *
 * <p>{@link #user} is nullable on purpose: occasional system-initiated
 * calls (warm-up probes, scheduled summarisation jobs) are not bound to
 * a logged-in user but still need to be costed. {@link #userRole} is
 * persisted alongside as a denormalised string so that historical
 * analytics survive role re-assignments and account deletions.</p>
 *
 * <p>{@link #status} is a free-form short string mirrored from
 * {@link com.example.aichinese.entity.enums.AiRequestStatus} (kept loose
 * because the audit log must accept any future status value without a
 * Flyway migration). The constraint
 * {@code chk_ai_usage_logs_status} pins the allowed set; see
 * {@code SchemaEnumContractTests}.</p>
 *
 * <p>{@link #relatedEntityType} and {@link #relatedEntityId} provide a
 * polymorphic back-reference (e.g. to a {@code Lesson}, {@code Quiz}, or
 * {@code AiChatSession}) without forcing a JPA association — useful for
 * post-hoc log slicing and intentionally insulated from cascade deletes
 * of the referenced entity.</p>
 *
 * <p>Telemetry is append-only by design: every domain field is mapped
 * {@code updatable = false}, so even an accidental {@code save()} on a
 * managed instance is a no-op at flush time. The single legitimate
 * write path is {@code AiLogService#createUsageLog}, which inserts the
 * row exactly once after the upstream provider call resolves.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_usage_logs", indexes = {
        @Index(name = "idx_ai_usage_logs_user_id", columnList = "user_id"),
        @Index(name = "idx_ai_usage_logs_feature_key", columnList = "feature_key"),
        @Index(name = "idx_ai_usage_logs_created_at", columnList = "created_at"),
        @Index(name = "idx_ai_usage_logs_user_feature_created", columnList = "user_id,feature_key,created_at")
})
public class AiUsageLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false)
    private User user;

    @Column(name = "feature_key", nullable = false, updatable = false, length = 100)
    private String featureKey;

    @Column(name = "provider_name", updatable = false, length = 100)
    private String providerName;

    @Column(name = "model_name", updatable = false, length = 120)
    private String modelName;

    @Column(name = "status", nullable = false, updatable = false, length = 50)
    private String status;

    @Column(name = "token_count", updatable = false)
    private Integer tokenCount;

    @Column(name = "user_role", updatable = false, length = 50)
    private String userRole;

    @Column(name = "language_preference", updatable = false, length = 20)
    private String languagePreference;

    @Column(name = "input_summary", updatable = false, length = 500)
    private String inputSummary;

    @Column(name = "output_summary", updatable = false, length = 500)
    private String outputSummary;

    @Column(name = "error_summary", updatable = false, length = 255)
    private String errorSummary;

    @Column(name = "related_entity_type", updatable = false, length = 100)
    private String relatedEntityType;

    @Column(name = "related_entity_id", updatable = false)
    private Long relatedEntityId;
}
