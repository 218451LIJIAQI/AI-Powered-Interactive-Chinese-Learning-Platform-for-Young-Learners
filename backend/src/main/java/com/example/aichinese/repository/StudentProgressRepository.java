package com.example.aichinese.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.StudentProgress;

import jakarta.persistence.LockModeType;

public interface StudentProgressRepository extends JpaRepository<StudentProgress, Long> {

    Optional<StudentProgress> findFirstByStudentIdOrderByUpdatedAtDesc(Long studentId);

    Optional<StudentProgress> findByStudentId(Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "student", "level" })
    @Query("select sp from StudentProgress sp where sp.student.id = :studentId")
    Optional<StudentProgress> findLockedByStudentId(@Param("studentId") Long studentId);

    @EntityGraph(attributePaths = { "student", "student.user", "student.currentLevel" })
    List<StudentProgress> findByStudentIdInOrderByUpdatedAtDesc(List<Long> studentIds);
}
