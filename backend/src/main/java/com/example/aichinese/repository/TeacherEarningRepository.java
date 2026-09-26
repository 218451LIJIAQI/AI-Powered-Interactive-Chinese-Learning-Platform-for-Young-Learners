package com.example.aichinese.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.TeacherEarning;
import com.example.aichinese.entity.enums.TeacherEarningStatus;
import com.example.aichinese.repository.projection.MonthlyEarningProjection;

public interface TeacherEarningRepository extends JpaRepository<TeacherEarning, Long> {

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "orderItem", "orderItem.order" })
    List<TeacherEarning> findByTeacherUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "orderItem", "orderItem.order" })
    Page<TeacherEarning> findByTeacherUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<TeacherEarning> findByOrderItemId(Long orderItemId);

    @Query("""
            select year(te.createdAt) as year, month(te.createdAt) as month, sum(te.netAmount) as amount
            from TeacherEarning te
            where te.teacher.id = :teacherId
            group by year(te.createdAt), month(te.createdAt)
            order by year(te.createdAt) desc, month(te.createdAt) desc
            """)
    List<MonthlyEarningProjection> findMonthlyNetAmountByTeacherId(
            @Param("teacherId") Long teacherId,
            Pageable pageable);

    @Query("select coalesce(sum(te.grossAmount), 0) from TeacherEarning te where te.teacher.id = :teacherId")
    BigDecimal sumGrossAmountByTeacherId(@Param("teacherId") Long teacherId);

    @Query("select coalesce(sum(te.netAmount), 0) from TeacherEarning te where te.teacher.id = :teacherId")
    BigDecimal sumNetAmountByTeacherId(@Param("teacherId") Long teacherId);

    @Query("""
            select coalesce(sum(te.grossAmount), 0)
            from TeacherEarning te
            where te.teacher.id = :teacherId
              and te.earningStatus <> :earningStatus
            """)
    BigDecimal sumGrossAmountByTeacherIdAndEarningStatusNot(
            @Param("teacherId") Long teacherId,
            @Param("earningStatus") TeacherEarningStatus earningStatus);

    @Query("""
            select coalesce(sum(te.netAmount), 0)
            from TeacherEarning te
            where te.teacher.id = :teacherId
              and te.earningStatus <> :earningStatus
            """)
    BigDecimal sumNetAmountByTeacherIdAndEarningStatusNot(
            @Param("teacherId") Long teacherId,
            @Param("earningStatus") TeacherEarningStatus earningStatus);

    @Query("""
            select coalesce(sum(te.netAmount), 0)
            from TeacherEarning te
            where te.teacher.id = :teacherId
              and te.earningStatus = com.example.aichinese.entity.enums.TeacherEarningStatus.AVAILABLE
            """)
    BigDecimal sumAvailableNetAmountByTeacherId(@Param("teacherId") Long teacherId);
}
