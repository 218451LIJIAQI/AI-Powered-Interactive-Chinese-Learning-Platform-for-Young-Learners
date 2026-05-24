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
 * Parent container for an AI tutoring conversation belonging to a single
 * {@link User}. Every {@link AiChatMessage} is owned by exactly one
 * session via the {@code session_id} foreign key.
 *
 * <p>{@link #featureKey} stores the canonical {@code AiFeatureKey} value
 * the conversation was started under (e.g. {@code CHAT_TUTOR},
 * {@code STORY_GENERATOR}). It is held as a {@code String} rather than
 * an enum so that legacy or experimental feature names recovered from
 * imported data do not break Hibernate validation; the application
 * layer parses the value through {@code AiFeatureKey#tryFrom}.</p>
 *
 * <p>{@link #relatedLessonId} is a denormalized {@code @Column}
 * (intentionally <em>not</em> a {@code @ManyToOne}) so that deleting a
 * lesson does not orphan the chat transcript; the foreign key is
 * resolved at presentation time only when the lesson still exists.</p>
 *
 * <p>{@link #active} flips to {@code false} when the user explicitly
 * archives the session; soft-deletion preserves the audit trail required
 * by the AI moderation flow.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_chat_sessions", indexes = {
        @Index(name = "idx_ai_chat_sessions_user_id", columnList = "user_id"),
        @Index(name = "idx_ai_chat_sessions_feature_key", columnList = "feature_key"),
        @Index(name = "idx_ai_chat_sessions_user_feature_created", columnList = "user_id,feature_key,created_at")
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
