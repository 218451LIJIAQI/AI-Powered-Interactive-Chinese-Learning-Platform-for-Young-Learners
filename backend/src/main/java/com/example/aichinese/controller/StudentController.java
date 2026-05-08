package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.dashboard.StudentDashboardResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.DashboardService;

@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

    private final DashboardService dashboardService;

    public StudentController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<StudentDashboardResponse> getDashboard(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Student dashboard retrieved successfully.",
                dashboardService.getStudentDashboard(currentUser));
    }
}
