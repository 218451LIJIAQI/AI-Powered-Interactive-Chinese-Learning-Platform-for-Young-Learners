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

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    interface AssistantPromptSnapshotProjection {
        Long getAssistantMessageId();

        String getPromptSnapshot();
    }

    List<AiChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    Page<AiChatMessage> findBySessionIdOrderByCreatedAtDesc(Long sessionId, Pageable pageable);

    Optional<AiChatMessage> findFirstBySessionIdAndSenderRoleIgnoreCaseOrderByIdDesc(Long sessionId, String senderRole);

    Optional<AiChatMessage> findFirstBySessionIdAndSenderRoleIgnoreCaseAndIdLessThanOrderByIdDesc(
            Long sessionId,
            String senderRole,
            Long id);

    @Query(value = """
            select assistant.id as assistantMessageId,
                   prompt.message_text as promptSnapshot
            from ai_chat_messages assistant
            left join ai_chat_messages prompt on prompt.id = (
                select prior.id
                from ai_chat_messages prior
                where prior.session_id = assistant.session_id
                  and lower(prior.sender_role) = 'user'
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
