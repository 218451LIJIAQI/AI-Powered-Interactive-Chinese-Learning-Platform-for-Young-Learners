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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_usage_logs", indexes = {
        @Index(name = "idx_ai_usage_logs_user_id", columnList = "user_id"),
        @Index(name = "idx_ai_usage_logs_feature_key", columnList = "feature_key"),
        @Index(name = "idx_ai_usage_logs_created_at", columnList = "created_at")
})
public class AiUsageLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "feature_key", nullable = false, length = 100)
    private String featureKey;

    @Column(name = "provider_name", length = 100)
    private String providerName;

    @Column(name = "model_name", length = 120)
    private String modelName;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "user_role", length = 50)
    private String userRole;

    @Column(name = "language_preference", length = 20)
    private String languagePreference;

    @Column(name = "input_summary", length = 500)
    private String inputSummary;

    @Column(name = "output_summary", length = 500)
    private String outputSummary;

    @Column(name = "error_summary", length = 255)
    private String errorSummary;

    @Column(name = "related_entity_type", length = 100)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;
}
