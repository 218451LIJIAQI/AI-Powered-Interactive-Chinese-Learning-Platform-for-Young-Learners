package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.AiResponseReport;
import com.example.aichinese.entity.enums.AiResponseReportStatus;

public interface AiResponseReportRepository extends JpaRepository<AiResponseReport, Long> {

    @EntityGraph(attributePaths = {
            "studentUser",
            "reviewerUser",
            "aiUsageLog",
            "chatSession",
            "chatMessage"
    })
    @NonNull
    List<AiResponseReport> findAll(@NonNull Sort sort);

    @EntityGraph(attributePaths = {
            "studentUser",
            "reviewerUser",
            "aiUsageLog",
            "chatSession",
            "chatMessage"
    })
    List<AiResponseReport> findByStatusOrderByCreatedAtDesc(AiResponseReportStatus status);

    @EntityGraph(attributePaths = {
            "studentUser",
            "reviewerUser",
            "aiUsageLog",
            "chatSession",
            "chatMessage"
    })
    Page<AiResponseReport> findByStatusOrderByCreatedAtDesc(AiResponseReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {
            "studentUser",
            "reviewerUser",
            "aiUsageLog",
            "chatSession",
            "chatMessage"
    })
    List<AiResponseReport> findByStudentUserIdOrderByCreatedAtDesc(Long studentUserId);

    @EntityGraph(attributePaths = {
            "studentUser",
            "reviewerUser",
            "aiUsageLog",
            "chatSession",
            "chatMessage"
    })
    Page<AiResponseReport> findByStudentUserIdOrderByCreatedAtDesc(Long studentUserId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {
            "studentUser",
            "reviewerUser",
            "aiUsageLog",
            "chatSession",
            "chatMessage"
    })
    @NonNull
    Page<AiResponseReport> findAll(@NonNull Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {
            "studentUser",
            "reviewerUser",
            "aiUsageLog",
            "chatSession",
            "chatMessage"
    })
    @NonNull
    Optional<AiResponseReport> findById(@NonNull Long id);
}
