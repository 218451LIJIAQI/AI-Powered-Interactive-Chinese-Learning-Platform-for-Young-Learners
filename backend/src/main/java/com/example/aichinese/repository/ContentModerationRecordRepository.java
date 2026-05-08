package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.ContentModerationRecord;

public interface ContentModerationRecordRepository extends JpaRepository<ContentModerationRecord, Long> {
}
