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
import org.springframework.lang.Nullable;

import com.example.aichinese.entity.AiChatMessage;
import com.example.aichinese.entity.enums.AiChatSenderRole;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    interface AssistantPromptSnapshotProjection {

        Long getAssistantMessageId();

        @Nullable
        String getPromptSnapshot();
    }

    List<AiChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    Page<AiChatMessage> findBySessionIdOrderByCreatedAtDesc(Long sessionId, Pageable pageable);

    Optional<AiChatMessage> findFirstBySessionIdAndSenderRoleOrderByIdDesc(
            Long sessionId,
            AiChatSenderRole senderRole);

    default List<AssistantPromptSnapshotProjection> findPromptSnapshotsForAssistantMessageIds(
            @Nullable Collection<Long> assistantMessageIds) {
        if (assistantMessageIds == null || assistantMessageIds.isEmpty()) {
            return List.of();
        }
        return findPromptSnapshotsForAssistantMessageIdsInternal(
                assistantMessageIds, AiChatSenderRole.USER.name());
    }

    @Query(value = """
            select assistant.id as assistantMessageId,
                   prompt.message_text as promptSnapshot
            from ai_chat_messages assistant
            left join ai_chat_messages prompt on prompt.id = (
                select prior.id
                from ai_chat_messages prior
                where prior.session_id = assistant.session_id
                  and prior.sender_role = :userRole
                  and prior.id < assistant.id
                order by prior.id desc
                limit 1
            )
            where assistant.id in (:assistantMessageIds)
            """, nativeQuery = true)
    List<AssistantPromptSnapshotProjection> findPromptSnapshotsForAssistantMessageIdsInternal(
            @Param("assistantMessageIds") Collection<Long> assistantMessageIds,
            @Param("userRole") String userRole);

    @EntityGraph(attributePaths = { "session", "session.user" })
    @Query("select message from AiChatMessage message join message.session session "
            + "where message.id = :id and session.user.id = :userId")
    Optional<AiChatMessage> findByIdAndSessionUserId(
            @Param("id") Long id,
            @Param("userId") Long userId);
}
