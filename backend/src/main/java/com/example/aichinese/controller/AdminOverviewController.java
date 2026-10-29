package com.example.aichinese.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.admin.AdminAnalyticsResponse;
import com.example.aichinese.dto.admin.AdminDashboardResponse;
import com.example.aichinese.service.admin.AdminOverviewService;

/** The admin dashboard and analytics. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminOverviewController {

    private final AdminOverviewService adminOverviewService;

    public AdminOverviewController(AdminOverviewService adminOverviewService) {
        this.adminOverviewService = adminOverviewService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> dashboard() {
        return ApiResponse.success("Admin dashboard retrieved successfully.", adminOverviewService.dashboard());
    }

    /** {@code days}: 7 (default) or 30. */
    @GetMapping("/analytics")
    public ApiResponse<AdminAnalyticsResponse> analytics(@RequestParam(value = "days", defaultValue = "7") int days) {
        return ApiResponse.success("Admin analytics retrieved successfully.", adminOverviewService.analytics(days));
    }
}
