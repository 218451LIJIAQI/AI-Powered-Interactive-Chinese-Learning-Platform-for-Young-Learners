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
import com.example.aichinese.dto.learn.AnswerInsightsResponse;
import com.example.aichinese.dto.learn.DailyGoalRequest;
import com.example.aichinese.dto.learn.DailyGoalResponse;
import com.example.aichinese.dto.learn.HintDto;
import com.example.aichinese.dto.learn.ProgressOverviewResponse;
import com.example.aichinese.dto.learn.SkillProgressResponse;
import com.example.aichinese.dto.learn.TodayResponse;
import com.example.aichinese.dto.learn.RewardSummaryResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.learn.AnswerInsightsService;
import com.example.aichinese.service.learn.HintPolicyService;
import com.example.aichinese.service.learn.ProgressOverviewService;
import com.example.aichinese.service.learn.SkillProgressService;
import com.example.aichinese.service.learn.XpService;

import lombok.RequiredArgsConstructor;

/**
 * XP, the daily goal and the streak: the sidebar's reward summary, today's summary, the daily goal setting and the
 * Progress page (with how sure the student's answers were this week); and the student's hint setting with the rule of
 * their level.
 */
@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class StudentXpController {

    private final XpService xpService;
    private final ProgressOverviewService progressOverviewService;
    private final SkillProgressService skillProgressService;
    private final AnswerInsightsService answerInsightsService;
    private final HintPolicyService hintPolicyService;

    @GetMapping("/rewards")
    public ApiResponse<RewardSummaryResponse> rewards(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Rewards retrieved successfully.", xpService.rewardSummary(currentUser));
    }

    @GetMapping("/today")
    public ApiResponse<TodayResponse> today(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Today retrieved successfully.", xpService.today(currentUser));
    }

    @GetMapping("/daily-goal")
    public ApiResponse<DailyGoalResponse> dailyGoal(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Daily goal retrieved successfully.", xpService.dailyGoalOf(currentUser));
    }

    @PutMapping("/daily-goal")
    public ApiResponse<DailyGoalResponse> changeDailyGoal(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody DailyGoalRequest request) {
        return ApiResponse.success("Daily goal saved.", xpService.changeDailyGoal(currentUser, request.dailyGoalXp()));
    }

    @GetMapping("/progress/overview")
    public ApiResponse<ProgressOverviewResponse> progressOverview(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "month", required = false) String month) {
        return ApiResponse.success("Progress retrieved successfully.",
                progressOverviewService.overview(currentUser, LearnParams.optionalMonth(month)));
    }

    @GetMapping("/progress/skills")
    public ApiResponse<SkillProgressResponse> progressSkills(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Skills retrieved successfully.", skillProgressService.skills(currentUser));
    }

    @GetMapping("/progress/answers")
    public ApiResponse<AnswerInsightsResponse> progressAnswers(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Answers retrieved successfully.", answerInsightsService.thisWeek(currentUser));
    }

    @GetMapping("/hint-settings")
    public ApiResponse<HintDto.Settings> hintSettings(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Hint settings retrieved successfully.", hintPolicyService.settings(currentUser));
    }

    @PutMapping("/hint-settings")
    public ApiResponse<HintDto.Settings> changeHintSettings(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody HintDto.SettingsUpdate request) {
        return ApiResponse.success("Hint settings saved.", hintPolicyService.changeMode(currentUser, request.mode()));
    }
}
