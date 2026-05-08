package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.AiChatMessage;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {
}
