package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.SupportRequest;
import com.example.aichinese.entity.enums.SupportRequestStatus;
import com.example.aichinese.repository.projection.StatusCountProjection;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {

    List<SupportRequest> findByRequesterIdOrderByCreatedAtDescIdDesc(Long requesterId);

    @EntityGraph(attributePaths = { "requester" })
    Optional<SupportRequest> findByIdAndRequesterId(Long id, Long requesterId);

    @EntityGraph(attributePaths = { "requester" })
    Optional<SupportRequest> findDetailedById(Long id);

    /** The admin queue of one status: the oldest waiting request first, so nobody waits longest. */
    @EntityGraph(attributePaths = { "requester" })
    Page<SupportRequest> findByStatusOrderByUpdatedAtAscIdAsc(SupportRequestStatus status, Pageable pageable);

    /** Solved requests, most recently solved first. */
    @EntityGraph(attributePaths = { "requester" })
    Page<SupportRequest> findByStatusOrderBySolvedAtDescIdDesc(SupportRequestStatus status, Pageable pageable);

    long countByStatus(SupportRequestStatus status);

    /** Answered requests of the requester that have not reached their closing time. */
    @Query("""
            select count(r) from SupportRequest r
            where r.requester.id = :requesterId
              and r.status = com.example.aichinese.entity.enums.SupportRequestStatus.ANSWERED
              and (r.closesAt is null or r.closesAt > :now)
            """)
    long countAnsweredStillOpen(@Param("requesterId") Long requesterId, @Param("now") LocalDateTime now);

    @Query("select r.status as status, count(r) as count from SupportRequest r group by r.status")
    List<StatusCountProjection> countGroupedByStatus();

    @Query("select coalesce(max(r.number), 0) from SupportRequest r")
    int findHighestNumber();

    /** Answered requests nobody replied to within 7 days of the last support reply become solved. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update SupportRequest r
            set r.status = com.example.aichinese.entity.enums.SupportRequestStatus.SOLVED,
                r.solvedAt = r.closesAt,
                r.closedBy = com.example.aichinese.entity.enums.SupportClosedBy.AUTO,
                r.closesAt = null,
                r.updatedAt = :now
            where r.status = com.example.aichinese.entity.enums.SupportRequestStatus.ANSWERED
              and r.closesAt <= :now
            """)
    int closeExpired(@Param("now") LocalDateTime now);

    List<SupportRequest> findByRequesterId(Long requesterId);
}
