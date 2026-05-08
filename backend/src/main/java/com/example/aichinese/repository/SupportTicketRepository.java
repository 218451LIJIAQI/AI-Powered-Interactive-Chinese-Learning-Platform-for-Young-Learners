package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.SupportTicket;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
}
