package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.AiGenerationRecord;

public interface AiGenerationRecordRepository extends JpaRepository<AiGenerationRecord, Long> {
}
