package com.example.aichinese.controller;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.navigation.NavCountService;

/** Sidebar badge counts of the signed-in user (mistakes waiting, review queue, open reports, support replies). */
@RestController
@RequestMapping("/api/v1/users/me")
public class NavigationController {

    private final NavCountService navCountService;

    public NavigationController(NavCountService navCountService) {
        this.navCountService = navCountService;
    }

    @GetMapping("/nav-counts")
    public ApiResponse<Map<String, Long>> navCounts(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Navigation counts retrieved successfully.", navCountService.countsFor(currentUser));
    }
}
