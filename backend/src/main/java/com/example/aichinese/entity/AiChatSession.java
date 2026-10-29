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
@Table(name = "ai_chat_sessions", indexes = {
        @Index(name = "idx_ai_chat_sessions_user_id", columnList = "user_id"),
        @Index(name = "idx_ai_chat_sessions_feature_key", columnList = "feature_key"),
        @Index(name = "idx_ai_chat_sessions_user_feature_created", columnList = "user_id,feature_key,created_at")
})
public class AiChatSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_key", nullable = false, updatable = false, length = 40)
    private AiFeatureKey featureKey;

    @Column(name = "language_preference", length = 20)
    private String languagePreference;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
