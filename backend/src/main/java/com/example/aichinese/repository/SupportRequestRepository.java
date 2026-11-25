package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.SupportRequest;
import com.example.aichinese.entity.enums.SupportRequestStatus;

import jakarta.persistence.LockModeType;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {

    /**
     * What deleting a verified account removes: its own requests and the ones sent from its address through the public
     * Help page (no account; MySQL utf8mb4_0900_ai_ci matches any letter case while still using the email index).
     */
    @Query("""
            select r.id from SupportRequest r
            where r.requester.id = :userId or (r.requester is null and r.requesterEmail = lower(:email))
            order by r.id asc
            """)
    List<Long> findIdsSentBy(@Param("userId") Long userId, @Param("email") String email);

    /** What deleting an unverified account removes: only requests created directly by the signed-in account. */
    @Query("select r.id from SupportRequest r where r.requester.id = :userId order by r.id asc")
    List<Long> findIdsByRequesterId(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select r.id from SupportRequest r where r.id = :id")
    Optional<Long> findIdWithReadLock(@Param("id") Long id);

    @EntityGraph(attributePaths = { "requester" })
    Optional<SupportRequest> findDetailedById(Long id);

    /** The admin queue of one status: the request left longest without a change first. */
    @EntityGraph(attributePaths = { "requester" })
    List<SupportRequest> findByStatusOrderByUpdatedAtAscIdAsc(SupportRequestStatus status, Pageable pageable);

    /** Solved requests, most recently solved first. */
    @EntityGraph(attributePaths = { "requester" })
    List<SupportRequest> findByStatusOrderBySolvedAtDescIdDesc(SupportRequestStatus status, Pageable pageable);

    long countByStatus(SupportRequestStatus status);

    interface StatusCount {
        SupportRequestStatus getStatus();
        long getCount();
    }

    @Query("select r.status as status, count(r) as count from SupportRequest r group by r.status")
    List<StatusCount> groupByStatus();

    @Query("select coalesce(max(r.number), 0) from SupportRequest r")
    int findHighestNumber();

    /** Bounded slice of answered requests past their auto-close deadline, in ascending id order for ordered locking. */
    @Query("""
            select r.id from SupportRequest r
            where r.status = com.example.aichinese.entity.enums.SupportRequestStatus.ANSWERED
              and r.closesAt <= :now
            order by r.id asc
            """)
    List<Long> findExpiredIds(@Param("now") LocalDateTime now, Pageable pageable);

    /** Acquires primary-key write locks in ascending id order for account deletion and bulk auto-close. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r.id from SupportRequest r where r.id in :ids order by r.id asc")
    List<Long> lockIdsInOrder(@Param("ids") Collection<Long> ids);

    /** Re-verifies which already-locked requests are still answered and past their auto-close deadline. */
    @Query("""
            select r.id from SupportRequest r
            where r.id in :ids
              and r.status = com.example.aichinese.entity.enums.SupportRequestStatus.ANSWERED
              and r.closesAt <= :now
            order by r.id asc
            """)
    List<Long> findExpiredIdsIn(@Param("ids") Collection<Long> ids, @Param("now") LocalDateTime now);

    /**
     * Marks already-locked expired requests solved via primary key only (avoiding secondary-index scans across the
     * backlog). Relies on MySQL left-to-right {@code SET} evaluation: {@code r.solvedAt = r.closesAt} must stay before
     * {@code r.closesAt = null}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update SupportRequest r
            set r.status = com.example.aichinese.entity.enums.SupportRequestStatus.SOLVED,
                r.solvedAt = r.closesAt,
                r.closedBy = com.example.aichinese.entity.enums.SupportClosedBy.AUTO,
                r.closesAt = null,
                r.updatedAt = :now
            where r.id in :ids
            """)
    int closeLockedByIds(@Param("ids") Collection<Long> ids, @Param("now") LocalDateTime now);
}
