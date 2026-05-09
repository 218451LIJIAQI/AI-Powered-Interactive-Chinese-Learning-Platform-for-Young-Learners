package com.example.aichinese.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.TeacherEarning;

public interface TeacherEarningRepository extends JpaRepository<TeacherEarning, Long> {

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "orderItem", "orderItem.order" })
    List<TeacherEarning> findByTeacherUserIdOrderByCreatedAtDesc(Long userId);

    Optional<TeacherEarning> findByOrderItemId(Long orderItemId);

    @Query("select coalesce(sum(te.grossAmount), 0) from TeacherEarning te where te.teacher.id = :teacherId")
    BigDecimal sumGrossAmountByTeacherId(@Param("teacherId") Long teacherId);

    @Query("select coalesce(sum(te.netAmount), 0) from TeacherEarning te where te.teacher.id = :teacherId")
    BigDecimal sumNetAmountByTeacherId(@Param("teacherId") Long teacherId);

    @Query("select coalesce(sum(te.grossAmount), 0) from TeacherEarning te where te.teacher.id = :teacherId and te.earningStatus <> :earningStatus")
    BigDecimal sumGrossAmountByTeacherIdAndEarningStatusNot(@Param("teacherId") Long teacherId, @Param("earningStatus") String earningStatus);

    @Query("select coalesce(sum(te.netAmount), 0) from TeacherEarning te where te.teacher.id = :teacherId and te.earningStatus <> :earningStatus")
    BigDecimal sumNetAmountByTeacherIdAndEarningStatusNot(@Param("teacherId") Long teacherId, @Param("earningStatus") String earningStatus);

    @Query("select coalesce(sum(te.netAmount), 0) from TeacherEarning te where te.teacher.id = :teacherId and te.earningStatus = 'AVAILABLE'")
    BigDecimal sumAvailableNetAmountByTeacherId(@Param("teacherId") Long teacherId);
}
