package com.example.aichinese.repository;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.TeacherEarning;

public interface TeacherEarningRepository extends JpaRepository<TeacherEarning, Long> {

    @Query("select coalesce(sum(te.grossAmount), 0) from TeacherEarning te where te.teacher.id = :teacherId")
    BigDecimal sumGrossAmountByTeacherId(@Param("teacherId") Long teacherId);

    @Query("select coalesce(sum(te.netAmount), 0) from TeacherEarning te where te.teacher.id = :teacherId")
    BigDecimal sumNetAmountByTeacherId(@Param("teacherId") Long teacherId);
}
