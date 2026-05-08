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
@Table(name = "ai_chat_messages", indexes = {
        @Index(name = "idx_ai_chat_messages_session_id", columnList = "session_id")
})
public class AiChatMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AiChatSession session;

    @Column(name = "sender_role", nullable = false, length = 30)
    private String senderRole;

    @Lob
    @Column(name = "message_text", nullable = false)
    private String messageText;
}
