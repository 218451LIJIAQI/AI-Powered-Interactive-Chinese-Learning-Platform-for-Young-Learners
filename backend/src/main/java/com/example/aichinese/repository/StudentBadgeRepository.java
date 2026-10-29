package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.StudentBadge;

public interface StudentBadgeRepository extends JpaRepository<StudentBadge, Long> {


    boolean existsByStudentIdAndBadgeId(Long studentId, Long badgeId);


    @EntityGraph(attributePaths = { "badge" })
    List<StudentBadge> findByStudentId(Long studentId);
}
