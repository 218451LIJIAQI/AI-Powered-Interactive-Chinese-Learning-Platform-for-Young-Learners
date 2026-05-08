package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.dashboard.TeacherDashboardResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.DashboardService;

@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherController {

    private final DashboardService dashboardService;

    public TeacherController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<TeacherDashboardResponse> getDashboard(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher dashboard retrieved successfully.",
                dashboardService.getTeacherDashboard(currentUser));
    }
}
