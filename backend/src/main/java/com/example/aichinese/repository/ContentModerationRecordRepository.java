package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.ContentModerationRecord;

public interface ContentModerationRecordRepository extends JpaRepository<ContentModerationRecord, Long> {

    @Override
    @EntityGraph(attributePaths = { "adminUser", "relatedTeacher", "relatedTeacher.user" })
    @NonNull
    List<ContentModerationRecord> findAll();

    @Override
    @EntityGraph(attributePaths = { "adminUser", "relatedTeacher", "relatedTeacher.user" })
    @NonNull
    Page<ContentModerationRecord> findAll(@NonNull Pageable pageable);
}
