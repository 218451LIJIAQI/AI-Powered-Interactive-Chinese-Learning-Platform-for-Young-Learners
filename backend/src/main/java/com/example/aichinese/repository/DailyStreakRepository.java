package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.DailyStreak;

import jakarta.persistence.LockModeType;

public interface DailyStreakRepository extends JpaRepository<DailyStreak, Long> {

    Optional<DailyStreak> findFirstByStudentIdOrderByCreatedAtDesc(Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ds from DailyStreak ds where ds.student.id = :studentId")
    Optional<DailyStreak> findLockedByStudentId(@Param("studentId") Long studentId);
}
