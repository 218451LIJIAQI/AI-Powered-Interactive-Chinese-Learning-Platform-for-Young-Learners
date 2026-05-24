package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.AiFeedbackRecord;

/**
 * Persistence operations for {@link AiFeedbackRecord} — the thumbs-up /
 * thumbs-down + free-text feedback a learner can attach to a SparkBot
 * AI reply.
 *
 * <p>This repository deliberately exposes only the inherited
 * {@link JpaRepository} surface: feedback is write-once (the UI flow
 * does not support edits once submitted), and every query path runs
 * through {@code findById} or {@code save}. Adding bespoke lookup
 * methods here would over-engineer a write-mostly audit trail.</p>
 */
public interface AiFeedbackRecordRepository extends JpaRepository<AiFeedbackRecord, Long> {
}
