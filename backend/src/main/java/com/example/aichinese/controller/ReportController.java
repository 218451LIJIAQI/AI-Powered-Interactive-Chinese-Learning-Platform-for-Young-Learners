package com.example.aichinese.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.communication.CreateReportRequest;
import com.example.aichinese.dto.communication.ReportResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ReportService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping
    public ApiResponse<ReportResponse> createReport(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CreateReportRequest request) {
        return ApiResponse.success(
                "Report submitted successfully.",
                reportService.createReport(currentUser, request));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my")
    public ApiResponse<List<ReportResponse>> getMyReports(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Reports retrieved successfully.",
                reportService.getMyReports(currentUser));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my/{id}")
    public ApiResponse<ReportResponse> getMyReport(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Report retrieved successfully.",
                reportService.getMyReport(currentUser, id));
    }
}
