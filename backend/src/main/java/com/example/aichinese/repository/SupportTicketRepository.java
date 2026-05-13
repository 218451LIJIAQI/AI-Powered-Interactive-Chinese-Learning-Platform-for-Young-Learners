package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.SupportTicket;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    @Override
    @EntityGraph(attributePaths = { "createdByUser", "createdByUser.roles" })
    @NonNull
    List<SupportTicket> findAll();

    /**
     * Sort-aware overload used by the admin support inbox so that ordering by
     * {@code updatedAt DESC} happens at the database layer (where the column
     * lives) rather than in JVM memory after a full table scan.
     */
    @Override
    @EntityGraph(attributePaths = { "createdByUser", "createdByUser.roles" })
    @NonNull
    List<SupportTicket> findAll(@NonNull Sort sort);

    @EntityGraph(attributePaths = { "createdByUser", "createdByUser.roles" })
    List<SupportTicket> findByCreatedByUserIdOrderByUpdatedAtDesc(Long createdByUserId);

    @EntityGraph(attributePaths = { "createdByUser", "createdByUser.roles" })
    Optional<SupportTicket> findByIdAndCreatedByUserId(Long id, Long createdByUserId);

    /**
     * One row per distinct {@code ticketStatus} for the admin analytics
     * dashboard. Database-side aggregation replaces the previous JVM
     * group-by over the full ticket table.
     */
    @org.springframework.data.jpa.repository.Query(
            "select t.ticketStatus as status, count(t) as count from SupportTicket t group by t.ticketStatus")
    List<com.example.aichinese.repository.projection.StatusCountProjection> groupByTicketStatus();

    /**
     * Bulk count of "active" tickets ({@code OPEN} or {@code IN_PROGRESS}) for
     * the admin overview card. Replaces the previous {@code findAll() +
     * stream().filter(...)} two-status check.
     */
    long countByTicketStatusIn(
            java.util.Collection<com.example.aichinese.entity.enums.SupportTicketStatus> statuses);
}
