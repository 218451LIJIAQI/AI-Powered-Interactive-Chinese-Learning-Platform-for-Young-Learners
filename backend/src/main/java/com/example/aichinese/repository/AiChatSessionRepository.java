package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.AiChatSession;

public interface AiChatSessionRepository extends JpaRepository<AiChatSession, Long> {
}
