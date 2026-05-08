package com.example.aichinese.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.entity.User;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuthService;

@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherController {

    private final AuthService authService;

    public TeacherController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboard(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        User user = currentUser.getUser();
        return ApiResponse.success(
                "Teacher dashboard access granted.",
                Map.of(
                        "userId", currentUser.getUserId(),
                        "email", currentUser.getUsername(),
                        "roles", currentUser.getRoleNames(),
                        "teacherApproved", authService.isTeacherApproved(user)));
    }
}
