package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.AiChatMessage;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    List<AiChatMessage> findBySessionIdOrderByCreatedAtDescIdDesc(Long sessionId, Pageable pageable);

    /** The last user question before this answer in the same session, or null when there is none. */
    @Query("""
            select m.messageText from AiChatMessage m
            where m.session.id = :sessionId and m.senderRole = USER and m.id < :answerId
            order by m.id desc limit 1
            """)
    String findQuestionBefore(@Param("sessionId") Long sessionId, @Param("answerId") Long answerId);

    /** Removes every chat turn owned by a deleted account so free-form learner prompts are not retained. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AiChatMessage m where m.session.id in (select s.id from AiChatSession s where s.user.id = :userId)")
    int deleteBySessionUserId(@Param("userId") Long userId);
}
