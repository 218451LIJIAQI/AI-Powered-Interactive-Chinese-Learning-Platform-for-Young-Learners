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
     * What deleting the account removes: its own requests and the ones sent from its address through the public Help
     * page (no account; the address in any letter case).
     */
    @Query("""
            select r.id from SupportRequest r
            where r.requester.id = :userId or (r.requester is null and lower(r.requesterEmail) = lower(:email))
            order by r.id asc
            """)
    List<Long> findIdsSentBy(@Param("userId") Long userId, @Param("email") String email);

    /** Locks only these rows in deterministic id order so concurrent writes cannot deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SupportRequest r where r.id in :ids order by r.id asc")
    List<SupportRequest> findLockedByIdIn(@Param("ids") Collection<Long> ids);

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

    /** Answered requests past their auto-close deadline, returned in ascending id order for ordered locking. */
    @Query("""
            select r.id from SupportRequest r
            where r.status = com.example.aichinese.entity.enums.SupportRequestStatus.ANSWERED
              and r.closesAt <= :now
            order by r.id asc
            """)
    List<Long> findExpiredIds(@Param("now") LocalDateTime now);

    /** Acquires primary-key write locks in ascending id order before bulk auto-close so updates match deletion's lock order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r.id from SupportRequest r where r.id in :ids order by r.id asc")
    List<Long> lockIdsInOrder(@Param("ids") Collection<Long> ids);

    /**
     * Answered requests nobody replied to within 7 days of the last support reply become solved.
     * Relies on MySQL left-to-right {@code SET} evaluation: {@code r.solvedAt = r.closesAt} must stay before
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
              and r.status = com.example.aichinese.entity.enums.SupportRequestStatus.ANSWERED
              and r.closesAt <= :now
            """)
    int closeExpiredByIds(@Param("ids") Collection<Long> ids, @Param("now") LocalDateTime now);
}
