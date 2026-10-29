package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.learning.RewardSummaryResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningProgressService;

/**
 * The student's rewards (sidebar); Home and the learning plan live in StudentPlanController, class learning in
 * StudentClassController.
 */
@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

    private final StudentLearningProgressService rewards;

    public StudentController(StudentLearningProgressService rewards) {
        this.rewards = rewards;
    }

    @GetMapping("/rewards")
    public ApiResponse<RewardSummaryResponse> getRewards(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Rewards retrieved successfully.",
                rewards.getRewards(currentUser));
    }
}
