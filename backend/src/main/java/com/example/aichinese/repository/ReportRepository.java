package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {
}
