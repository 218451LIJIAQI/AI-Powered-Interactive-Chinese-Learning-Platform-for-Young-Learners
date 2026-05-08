package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.StudentProgress;

public interface StudentProgressRepository extends JpaRepository<StudentProgress, Long> {
}
