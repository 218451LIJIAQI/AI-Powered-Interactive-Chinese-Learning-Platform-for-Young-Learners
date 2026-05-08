package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.StudentProgress;

public interface StudentProgressRepository extends JpaRepository<StudentProgress, Long> {

    Optional<StudentProgress> findFirstByStudentIdOrderByUpdatedAtDesc(Long studentId);
}
