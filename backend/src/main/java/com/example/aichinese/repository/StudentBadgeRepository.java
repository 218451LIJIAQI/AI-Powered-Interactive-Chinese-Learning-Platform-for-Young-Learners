package com.example.aichinese.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.StudentBadge;

public interface StudentBadgeRepository extends JpaRepository<StudentBadge, Long> {

    boolean existsByStudentIdAndBadgeName(Long studentId, String badgeName);

    @Query("select sb.badge.name from StudentBadge sb where sb.student.id = :studentId")
    Set<String> findBadgeNamesByStudentId(@Param("studentId") Long studentId);

    @EntityGraph(attributePaths = { "badge" })
    List<StudentBadge> findByStudentId(Long studentId);
}
