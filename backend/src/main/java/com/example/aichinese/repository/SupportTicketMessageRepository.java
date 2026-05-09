package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.SupportTicketMessage;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, Long> {

    @EntityGraph(attributePaths = { "senderUser", "senderUser.roles", "ticket" })
    List<SupportTicketMessage> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
