package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.AiFeatureKey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_key", nullable = false, updatable = false, length = 40)
    private AiFeatureKey featureKey;

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
