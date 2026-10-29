package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.AiChatMessage;
import com.example.aichinese.entity.enums.AiChatSenderRole;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    Page<AiChatMessage> findBySessionIdOrderByCreatedAtDesc(Long sessionId, Pageable pageable);

    /** The latest message of {@code senderRole} in the session before message {@code beforeId}. */
    Optional<AiChatMessage> findFirstBySessionIdAndSenderRoleAndIdLessThanOrderByIdDesc(
            Long sessionId, AiChatSenderRole senderRole, Long beforeId);
}
