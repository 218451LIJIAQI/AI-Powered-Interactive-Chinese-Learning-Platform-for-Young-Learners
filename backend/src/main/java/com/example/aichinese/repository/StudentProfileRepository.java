package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.example.aichinese.entity.StudentProfile;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    @EntityGraph(attributePaths = { "currentLevel" })
    Optional<StudentProfile> findByUserId(Long userId);
}
