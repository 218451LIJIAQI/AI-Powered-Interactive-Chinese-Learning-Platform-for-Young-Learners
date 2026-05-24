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

    /**
     * Co-validating lookup that returns the session only when it is owned
     * by {@code userId}; the service layer maps an empty {@link Optional}
     * to a 404 response so the existence of cross-user session ids is
     * never disclosed to the caller.
     *
     * <p>The {@code user} association stays {@code LAZY}: every current
     * caller already holds the parent {@code User} reference (the request
     * authenticated as that user, or it loaded the {@code AiUsageLog}
     * whose {@code studentUser} is the same row), so eagerly fetching it
     * would only add an avoidable join.</p>
     *
     * @param id     primary key of the AI chat session
     * @param userId primary key of the user expected to own the session
     * @return the session when {@code id} exists <em>and</em> belongs to
     *         {@code userId}; otherwise {@link Optional#empty()}
     */
    Optional<AiChatSession> findByIdAndUserId(Long id, Long userId);
}
