package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.ContentModerationRecord;

public interface ContentModerationRecordRepository extends JpaRepository<ContentModerationRecord, Long> {

    @EntityGraph(attributePaths = { "adminUser", "relatedTeacher", "relatedTeacher.user" })
    List<ContentModerationRecord> findTop10ByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    @Override
    @EntityGraph(attributePaths = { "adminUser", "relatedTeacher", "relatedTeacher.user" })
    @NonNull
    List<ContentModerationRecord> findAll();

    /**
     * Sort-aware variant used by the admin moderation overview so DB-side
     * ordering replaces the in-memory sort. EntityGraph kept identical so the
     * response mapper never falls back to lazy lookups.
     */
    @Override
    @EntityGraph(attributePaths = { "adminUser", "relatedTeacher", "relatedTeacher.user" })
    @NonNull
    List<ContentModerationRecord> findAll(@NonNull Sort sort);

    @Override
    @EntityGraph(attributePaths = { "adminUser", "relatedTeacher", "relatedTeacher.user" })
    @NonNull
    Page<ContentModerationRecord> findAll(@NonNull Pageable pageable);
}
