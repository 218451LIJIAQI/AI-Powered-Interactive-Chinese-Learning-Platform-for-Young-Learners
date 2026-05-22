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
@Table(name = "ai_chat_sessions", indexes = {
        @Index(name = "idx_ai_chat_sessions_user_id", columnList = "user_id"),
        @Index(name = "idx_ai_chat_sessions_feature_key", columnList = "feature_key")
})
public class AiChatSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "feature_key", nullable = false, length = 100)
    private String featureKey;

    @Column(name = "session_title", length = 200)
    private String sessionTitle;

    @Column(name = "language_preference", length = 20)
    private String languagePreference;

    @Column(name = "related_lesson_id")
    private Long relatedLessonId;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
