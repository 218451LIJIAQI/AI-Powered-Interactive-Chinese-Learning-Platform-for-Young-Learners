package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.AiChatMessage;
import com.example.aichinese.entity.enums.AiChatSenderRole;

/**
 * Persistence operations for {@link AiChatMessage} — individual user
 * and assistant turns belonging to an {@code AiChatSession}.
 *
 * <p>Includes a native-SQL window helper
 * ({@link #findPromptSnapshotsForAssistantMessageIds(Collection)}) that
 * batch-resolves "the most recent USER prompt preceding each assistant
 * reply" for the SparkBot review workflow. This replaces the prior
 * pattern of N follow-up SELECTs (one per assistant message to find its
 * provoking prompt) with a single grouped query.</p>
 *
 * <p>{@link #findByIdAndSessionUserId(Long, Long)} couples message-id
 * with session-owner user-id so cross-user direct-object reference
 * attempts on chat messages cannot succeed (mirrors the IDOR-prevention
 * convention used by {@code AiChatSessionRepository}).</p>
 */
public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    /**
     * Native projection pairing an assistant message id with the
     * {@code message_text} of the most recent USER turn that preceded
     * it in the same session.
     */
    interface AssistantPromptSnapshotProjection {
        Long getAssistantMessageId();

        String getPromptSnapshot();
    }

    List<AiChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    Page<AiChatMessage> findBySessionIdOrderByCreatedAtDesc(Long sessionId, Pageable pageable);

    Optional<AiChatMessage> findFirstBySessionIdAndSenderRoleOrderByIdDesc(Long sessionId, AiChatSenderRole senderRole);

    @Query(value = """
            select assistant.id as assistantMessageId,
                   prompt.message_text as promptSnapshot
            from ai_chat_messages assistant
            left join ai_chat_messages prompt on prompt.id = (
                select prior.id
                from ai_chat_messages prior
                where prior.session_id = assistant.session_id
                  and prior.sender_role = 'USER'
                  and prior.id < assistant.id
                order by prior.id desc
                limit 1
            )
            where assistant.id in (:assistantMessageIds)
            """, nativeQuery = true)
    List<AssistantPromptSnapshotProjection> findPromptSnapshotsForAssistantMessageIds(
            @Param("assistantMessageIds") Collection<Long> assistantMessageIds);

    @EntityGraph(attributePaths = { "session", "session.user" })
    @Query("select message from AiChatMessage message join message.session session where message.id = :id and session.user.id = :userId")
    Optional<AiChatMessage> findByIdAndSessionUserId(@Param("id") Long id, @Param("userId") Long userId);
}
