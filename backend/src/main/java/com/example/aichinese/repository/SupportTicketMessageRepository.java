package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.SupportTicketMessage;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, Long> {
}
