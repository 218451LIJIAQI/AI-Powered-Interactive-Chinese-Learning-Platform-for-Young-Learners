package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.learn.DailyGoalRequest;
import com.example.aichinese.dto.learn.DailyGoalResponse;
import com.example.aichinese.dto.learn.ProgressOverviewResponse;
import com.example.aichinese.dto.learn.TodayResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.service.learn.ProgressOverviewService;
import com.example.aichinese.service.learn.XpService;

/** XP, the daily goal and the streak: today's summary, the daily goal setting and the Progress page. */
@RestController
@RequestMapping("/api/v1/students")
public class StudentXpController {

    private final XpService xpService;
    private final ProgressOverviewService progressOverviewService;
    private final StudentLearningAccessService accessService;

    public StudentXpController(
            XpService xpService,
            ProgressOverviewService progressOverviewService,
            StudentLearningAccessService accessService) {
        this.xpService = xpService;
        this.progressOverviewService = progressOverviewService;
        this.accessService = accessService;
    }

    @GetMapping("/today")
    public ApiResponse<TodayResponse> today(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Today retrieved successfully.",
                xpService.today(accessService.requireStudent(currentUser)));
    }

    @GetMapping("/daily-goal")
    public ApiResponse<DailyGoalResponse> dailyGoal(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Daily goal retrieved successfully.",
                xpService.dailyGoalOf(accessService.requireStudent(currentUser)));
    }

    @PutMapping("/daily-goal")
    public ApiResponse<DailyGoalResponse> changeDailyGoal(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody DailyGoalRequest request) {
        return ApiResponse.success("Daily goal saved.",
                xpService.changeDailyGoal(accessService.requireStudent(currentUser), request.dailyGoalXp()));
    }

    @GetMapping("/progress/overview")
    public ApiResponse<ProgressOverviewResponse> progressOverview(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "month", required = false) String month) {
        return ApiResponse.success("Progress retrieved successfully.", progressOverviewService.overview(
                accessService.requireStudent(currentUser), LearnParams.optionalMonth(month)));
    }
}
