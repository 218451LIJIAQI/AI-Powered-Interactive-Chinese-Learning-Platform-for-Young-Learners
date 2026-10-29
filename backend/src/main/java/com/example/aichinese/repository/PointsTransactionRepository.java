package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.PointsTransaction;

public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {

    Page<PointsTransaction> findByStudentIdOrderByCreatedAtDesc(Long studentId, Pageable pageable);

    boolean existsByStudentIdAndSourceTypeAndSourceId(Long studentId, String sourceType, Long sourceId);

    @Query("""
            select coalesce(sum(p.pointsDelta), 0) from PointsTransaction p
            where p.student.id = :studentId and p.createdAt >= :from and p.createdAt < :to
            """)
    long sumBetween(
            @Param("studentId") Long studentId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Source ids of one source type in a range (the daily-goal rows use the epoch day as their id). */
    @Query("""
            select p.sourceId from PointsTransaction p
            where p.student.id = :studentId and p.sourceType = :sourceType
              and p.sourceId >= :fromId and p.sourceId <= :toId
            """)
    List<Long> findSourceIdsBetween(
            @Param("studentId") Long studentId,
            @Param("sourceType") String sourceType,
            @Param("fromId") Long fromId,
            @Param("toId") Long toId);

    /** XP per source type, for "Where your XP comes from". */
    @Query("""
            select p.sourceType, sum(p.pointsDelta) from PointsTransaction p
            where p.student.id = :studentId group by p.sourceType
            """)
    List<Object[]> sumBySourceType(@Param("studentId") Long studentId);
}
