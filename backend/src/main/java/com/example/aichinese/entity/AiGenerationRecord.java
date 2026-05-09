package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_generation_records", indexes = {
        @Index(name = "idx_ai_generation_records_user_id", columnList = "user_id"),
        @Index(name = "idx_ai_generation_records_feature_key", columnList = "feature_key")
})
public class AiGenerationRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "feature_key", nullable = false, length = 100)
    private String featureKey;

    @Lob
    @Column(name = "prompt_text")
    private String promptText;

    @Lob
    @Column(name = "response_text")
    private String responseText;

    @Column(name = "language_preference", length = 20)
    private String languagePreference;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "provider_name", length = 100)
    private String providerName;

    @Column(name = "model_name", length = 120)
    private String modelName;

    @Column(name = "related_entity_type", length = 100)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @Column(name = "saved_as_official_content", nullable = false)
    private boolean savedAsOfficialContent;

    @Column(name = "reviewed_by_teacher", nullable = false)
    private boolean reviewedByTeacher;
}
