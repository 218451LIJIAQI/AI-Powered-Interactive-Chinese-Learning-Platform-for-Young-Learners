package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.AiChatSenderRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single message exchanged inside an {@link AiChatSession} transcript.
 *
 * <p>The platform persists every turn of an AI tutoring conversation so
 * that students can revisit context, moderators can audit AI safety, and
 * the {@code AiResponseReport} flow can quote specific replies. Each row
 * therefore belongs to exactly one parent session ({@code session_id} is
 * NOT NULL) and records who said it ({@link #senderRole}) plus the raw
 * payload ({@link #messageText}).</p>
 *
 * <p>Composite indexes enable two access patterns at scale:</p>
 * <ul>
 *   <li>{@code (session_id, created_at)} — full transcript chronological
 *       replay by session.</li>
 *   <li>{@code (session_id, sender_role, id)} — fast lookup of the
 *       previous user/assistant message inside a session, used by the
 *       continuation prompt builder in {@code AiService}.</li>
 * </ul>
 *
 * <p>The {@code message_text} column is {@code LONGTEXT} so model output
 * up to 4 GiB can be stored without truncation; in practice it is bounded
 * by {@code app.ai.max-output-tokens} on the producer side.</p>
 *
 * <p>The transcript is append-only by design: every domain field on this
 * entity is mapped {@code updatable = false}, so any attempt to mutate
 * a persisted message via {@code entity.setX(…)} + {@code save()} is
 * silently dropped at flush time. The only fields Hibernate writes on
 * UPDATE are the inherited {@code updated_at} audit timestamp — in
 * practice the row is inserted once and never touched again.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_chat_messages", indexes = {
        @Index(name = "idx_ai_chat_messages_session_id", columnList = "session_id"),
        @Index(name = "idx_ai_chat_messages_session_role_id", columnList = "session_id,sender_role,id"),
        @Index(name = "idx_ai_chat_messages_session_created", columnList = "session_id,created_at")
})
public class AiChatMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private AiChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, updatable = false, length = 30)
    private AiChatSenderRole senderRole;

    @Lob
    @Column(name = "message_text", nullable = false, updatable = false, columnDefinition = "LONGTEXT")
    private String messageText;
}
