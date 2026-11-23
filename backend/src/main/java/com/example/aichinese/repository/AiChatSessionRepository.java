package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.AiChatSession;

import jakarta.persistence.LockModeType;

public interface AiChatSessionRepository extends JpaRepository<AiChatSession, Long> {

    Optional<AiChatSession> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AiChatSession> findLockedByIdAndUserId(Long id, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AiChatSession s where s.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
