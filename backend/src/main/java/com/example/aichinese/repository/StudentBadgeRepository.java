package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.StudentBadge;

public interface StudentBadgeRepository extends JpaRepository<StudentBadge, Long> {

    long countByStudentId(Long studentId);

    boolean existsByStudentIdAndBadgeId(Long studentId, Long badgeId);

    @EntityGraph(attributePaths = { "badge" })
    List<StudentBadge> findByStudentIdOrderByAwardedAtDesc(Long studentId);

    @EntityGraph(attributePaths = { "badge" })
    Page<StudentBadge> findByStudentIdOrderByAwardedAtDesc(Long studentId, Pageable pageable);
}
