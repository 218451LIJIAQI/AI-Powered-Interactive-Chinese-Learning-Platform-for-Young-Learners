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
     *
     * <p>{@link #getPromptSnapshot()} comes from a {@code LEFT JOIN} and
     * is legitimately {@code null} when the assistant message is the very
     * first row of its session (e.g. a system-seeded greeting) and no
     * preceding user turn exists — callers must therefore null-check it
     * rather than dereferencing blindly.</p>
     */
    interface AssistantPromptSnapshotProjection {

        /** @return assistant message primary key; never {@code null} */
        Long getAssistantMessageId();

        /**
         * @return raw {@code message_text} of the most recent preceding
         *         user turn in the same session, or {@code null} when
         *         no such turn exists
         */
        @Nullable
        String getPromptSnapshot();
    }

    /**
     * Returns every message of {@code sessionId} in chronological order,
     * suitable for transcript replay. The composite index
     * {@code idx_ai_chat_messages_session_created} fully covers the
     * filter-and-order so this stays O(log n + k).
     */
    List<AiChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    /**
     * Reverse-chronological paged variant for the "recent first" UI.
     * The supplied {@link Pageable} should not declare its own sort
     * because the derived method already pins the order.
     */
    Page<AiChatMessage> findBySessionIdOrderByCreatedAtDesc(Long sessionId, Pageable pageable);

    /**
     * Locates the latest message of a given role in a session. Sorts by
     * {@code id desc} (rather than {@code created_at desc}) because ids
     * are strictly monotonic auto-increments and therefore avoid the
     * second-resolution tie-breaking ambiguity of {@code created_at};
     * the index {@code idx_ai_chat_messages_session_role_id} fully covers
     * the lookup.
     */
    Optional<AiChatMessage> findFirstBySessionIdAndSenderRoleOrderByIdDesc(
            Long sessionId,
            AiChatSenderRole senderRole);

    /**
     * Public batch-resolver of prompt snapshots that gracefully handles
     * the empty-input edge case: the underlying native SQL would otherwise
     * emit {@code IN ()}, which MySQL rejects with a syntax error
     * (Hibernate only rewrites empty-{@code IN} for JPQL queries, not
     * native ones). This wrapper short-circuits to an empty list and also
     * supplies the persisted enum-name for {@link AiChatSenderRole#USER}
     * so the SQL stays decoupled from the in-Java enum constant name.
     *
     * @param assistantMessageIds target assistant message ids; may be
     *                            {@code null} or empty (both yield an
     *                            empty result)
     * @return one projection per matched assistant id; never {@code null}
     */
    default List<AssistantPromptSnapshotProjection> findPromptSnapshotsForAssistantMessageIds(
            @Nullable Collection<Long> assistantMessageIds) {
        if (assistantMessageIds == null || assistantMessageIds.isEmpty()) {
            return List.of();
        }
        return findPromptSnapshotsForAssistantMessageIdsInternal(
                assistantMessageIds, AiChatSenderRole.USER.name());
    }

    /**
     * Native-SQL backing query for
     * {@link #findPromptSnapshotsForAssistantMessageIds(Collection)}. Not
     * intended to be invoked directly — the public default-method
     * wrapper provides the empty-collection guard and supplies the
     * {@code userRole} parameter. The correlated subquery is fully covered
     * by {@code idx_ai_chat_messages_session_role_id}
     * ({@code session_id, sender_role, id}) and runs in
     * O(distinct sessions · log n).
     */
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

    /**
     * Co-validating lookup: returns the message only when its parent
     * session belongs to {@code userId}. Prevents IDOR (insecure direct
     * object reference) escalation where a logged-in student guesses a
     * neighbour's message id. The {@link EntityGraph} eagerly fetches
     * {@code session} and {@code session.user} so callers can read
     * ownership metadata without triggering an extra round trip.
     */
    @EntityGraph(attributePaths = { "session", "session.user" })
    @Query("select message from AiChatMessage message join message.session session "
            + "where message.id = :id and session.user.id = :userId")
    Optional<AiChatMessage> findByIdAndSessionUserId(
            @Param("id") Long id,
            @Param("userId") Long userId);
}
