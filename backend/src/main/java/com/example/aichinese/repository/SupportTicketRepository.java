package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.SupportTicket;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    @Override
    @EntityGraph(attributePaths = { "createdByUser", "createdByUser.roles" })
    List<SupportTicket> findAll();

    @EntityGraph(attributePaths = { "createdByUser", "createdByUser.roles" })
    List<SupportTicket> findByCreatedByUserIdOrderByUpdatedAtDesc(Long createdByUserId);

    @EntityGraph(attributePaths = { "createdByUser", "createdByUser.roles" })
    Optional<SupportTicket> findByIdAndCreatedByUserId(Long id, Long createdByUserId);
}
