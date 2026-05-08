package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findTop5ByOrderByCreatedAtDesc();
}
