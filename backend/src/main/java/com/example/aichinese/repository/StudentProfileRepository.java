package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.StudentProfile;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
}
