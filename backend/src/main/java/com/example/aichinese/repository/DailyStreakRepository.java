package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.DailyStreak;

public interface DailyStreakRepository extends JpaRepository<DailyStreak, Long> {

    Optional<DailyStreak> findFirstByStudentIdOrderByCreatedAtDesc(Long studentId);
}
