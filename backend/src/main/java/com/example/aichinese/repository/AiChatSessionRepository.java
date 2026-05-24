package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.AiChatSession;

/**
 * Persistence operations for {@link AiChatSession} — the conversational
 * scope that groups a learner's exchanged messages with the SparkBot AI
 * tutor.
 *
 * <p>{@link #findByIdAndUserId(Long, Long)} couples session-id with the
 * owning user id so the SparkBot endpoints cannot be tricked into
 * returning another learner's session via direct-object reference (the
 * service layer treats a missing {@link Optional} as 404 to avoid
 * leaking the existence of cross-user session ids).</p>
 */
public interface AiChatSessionRepository extends JpaRepository<AiChatSession, Long> {

    Optional<AiChatSession> findByIdAndUserId(Long id, Long userId);
}
