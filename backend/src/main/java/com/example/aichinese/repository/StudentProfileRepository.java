package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.aichinese.entity.StudentProfile;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long>, JpaSpecificationExecutor<StudentProfile> {

    @EntityGraph(attributePaths = { "currentLevel" })
    Optional<StudentProfile> findByUserId(Long userId);

    @EntityGraph(attributePaths = { "user", "currentLevel" })
    Page<StudentProfile> findAll(org.springframework.data.jpa.domain.Specification<StudentProfile> specification, Pageable pageable);

    @EntityGraph(attributePaths = { "user", "currentLevel" })
    java.util.List<StudentProfile> findAll(Sort sort);
}
