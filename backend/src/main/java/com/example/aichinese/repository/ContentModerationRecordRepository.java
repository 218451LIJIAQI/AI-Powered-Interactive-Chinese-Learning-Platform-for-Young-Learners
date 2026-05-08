package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.ContentModerationRecord;

public interface ContentModerationRecordRepository extends JpaRepository<ContentModerationRecord, Long> {

    List<ContentModerationRecord> findTop10ByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);
}
