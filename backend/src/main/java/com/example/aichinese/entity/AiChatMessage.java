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
import lombok.Setter;

@Getter
@Setter
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
