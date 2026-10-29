package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.AiPracticeCheck;

public interface AiPracticeCheckRepository extends JpaRepository<AiPracticeCheck, Long> {

    @EntityGraph(attributePaths = "lesson")
    Page<AiPracticeCheck> findByStudentUserIdOrderByCreatedAtDescIdDesc(Long studentUserId, Pageable pageable);

    @EntityGraph(attributePaths = "lesson")
    Optional<AiPracticeCheck> findByIdAndStudentUserId(Long id, Long studentUserId);
}
