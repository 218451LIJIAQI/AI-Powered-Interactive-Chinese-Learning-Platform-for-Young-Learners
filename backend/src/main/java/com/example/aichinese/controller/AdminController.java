package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.dashboard.AdminDashboardResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.DashboardService;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final DashboardService dashboardService;

    public AdminController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> getDashboard(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Admin dashboard retrieved successfully.",
                dashboardService.getAdminDashboard(currentUser));
    }
}
