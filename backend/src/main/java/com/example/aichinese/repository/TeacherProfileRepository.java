package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.TeacherProfile;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, Long> {
}
