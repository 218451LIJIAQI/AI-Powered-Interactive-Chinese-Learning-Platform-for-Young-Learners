package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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
        @Index(name = "idx_ai_chat_sessions_feature_key", columnList = "feature_key"),
        @Index(name = "idx_ai_chat_sessions_user_feature_created", columnList = "user_id,feature_key,created_at"),
        @Index(name = "idx_ai_chat_sessions_related_lesson_id", columnList = "related_lesson_id")
})
public class AiChatSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "feature_key", nullable = false, updatable = false, length = 100)
    private String featureKey;

    @Column(name = "session_title", length = 200)
    private String sessionTitle;

    @Column(name = "language_preference", length = 20)
    private String languagePreference;

    @Column(name = "related_lesson_id", updatable = false)
    private Long relatedLessonId;

    /** Read-only mapping that declares the foreign key to lessons (V5, audit A4 §5.1). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_lesson_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_ai_chat_sessions_related_lesson"))
    private Lesson relatedLesson;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
