package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.plan.HomeView;
import com.example.aichinese.dto.plan.PlanRequests;
import com.example.aichinese.dto.plan.PlanViews;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.plan.LearningPlanService;
import com.example.aichinese.service.plan.StudentHomeService;

/** The student's Home and learning plan: setup, the plan page, a new goal and a retaken level check. */
@RestController
@RequestMapping("/api/v1/students")
public class StudentPlanController {

    private final LearningPlanService planService;
    private final StudentHomeService homeService;

    public StudentPlanController(LearningPlanService planService, StudentHomeService homeService) {
        this.planService = planService;
        this.homeService = homeService;
    }

    @GetMapping("/home")
    public ApiResponse<HomeView> home(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Home retrieved successfully.", homeService.home(currentUser));
    }

    @GetMapping("/plan/options")
    public ApiResponse<PlanViews.Options> options() {
        return ApiResponse.success("Plan options retrieved successfully.", planService.options());
    }

    /** The plan, or {@code data: null} without one. */
    @GetMapping("/plan")
    public ApiResponse<PlanViews.Plan> plan(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Learning plan retrieved successfully.", planService.current(currentUser));
    }

    @GetMapping("/plan/overview")
    public ApiResponse<PlanViews.Overview> overview(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Learning plan retrieved successfully.", planService.overview(currentUser));
    }

    @PostMapping("/plan")
    public ApiResponse<PlanViews.Plan> build(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody PlanRequests.BuildRequest request) {
        return ApiResponse.success("Your learning plan is ready.", planService.build(currentUser, request));
    }

    @PutMapping("/plan/goal")
    public ApiResponse<PlanViews.Plan> updateGoal(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody PlanRequests.UpdateGoalRequest request) {
        return ApiResponse.success("Plan updated.", planService.updateGoal(currentUser, request));
    }

    @PostMapping("/plan/level-check")
    public ApiResponse<PlanViews.RetakeResult> retake(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody PlanRequests.RetakeRequest request) {
        return ApiResponse.success("Level check saved.", planService.retake(currentUser, request));
    }

    @PostMapping("/plan/skip")
    public ApiResponse<PlanViews.Overview> skip(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("You can build your plan any time from Home.", planService.skip(currentUser));
    }
}
