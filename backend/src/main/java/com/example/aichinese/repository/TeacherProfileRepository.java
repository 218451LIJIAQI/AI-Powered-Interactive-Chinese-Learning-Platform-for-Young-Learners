package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.TeacherStatus;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, Long> {

    Optional<TeacherProfile> findByUserId(Long userId);

    long countByTeacherStatus(TeacherStatus teacherStatus);
}
