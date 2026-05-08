package com.example.aichinese.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.security.SecurityUserDetails;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboard(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Admin dashboard access granted.",
                Map.of(
                        "userId", currentUser.getUserId(),
                        "email", currentUser.getUsername(),
                        "roles", currentUser.getRoleNames()));
    }
}
