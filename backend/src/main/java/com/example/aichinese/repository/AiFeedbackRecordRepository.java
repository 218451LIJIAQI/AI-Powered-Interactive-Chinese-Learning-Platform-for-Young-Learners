package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.AiFeedbackRecord;

public interface AiFeedbackRecordRepository extends JpaRepository<AiFeedbackRecord, Long> {
}
