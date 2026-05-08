package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.TeacherVerificationDocument;

public interface TeacherVerificationDocumentRepository extends JpaRepository<TeacherVerificationDocument, Long> {

    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    List<TeacherVerificationDocument> findByTeacherIdOrderBySubmittedAtDesc(Long teacherId);

    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    List<TeacherVerificationDocument> findByTeacherIdAndCurrentVersionTrueOrderBySubmittedAtDesc(Long teacherId);

    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    Optional<TeacherVerificationDocument> findByIdAndTeacherUserId(Long id, Long userId);

    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    Optional<TeacherVerificationDocument> findById(Long id);

    long countByTeacherId(Long teacherId);

    @EntityGraph(attributePaths = { "uploadedFile", "teacher", "teacher.user" })
    Optional<TeacherVerificationDocument> findTopByTeacherIdOrderBySubmittedAtDesc(Long teacherId);
}
