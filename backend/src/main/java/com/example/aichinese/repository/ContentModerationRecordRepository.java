package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.ContentModerationRecord;

public interface ContentModerationRecordRepository extends JpaRepository<ContentModerationRecord, Long> {

    @EntityGraph(attributePaths = { "adminUser", "relatedTeacher", "relatedTeacher.user" })
    List<ContentModerationRecord> findTop10ByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    @EntityGraph(attributePaths = { "adminUser", "relatedTeacher", "relatedTeacher.user" })
    List<ContentModerationRecord> findByRelatedTeacherUserIdOrderByCreatedAtDesc(Long teacherUserId);

    @Override
    @EntityGraph(attributePaths = { "adminUser", "relatedTeacher", "relatedTeacher.user" })
    List<ContentModerationRecord> findAll();
}
