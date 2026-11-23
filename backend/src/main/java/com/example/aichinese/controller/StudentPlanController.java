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
import com.example.aichinese.dto.learn.LevelCheckNextRequest;
import com.example.aichinese.dto.learn.LevelCheckNextResponse;
import com.example.aichinese.dto.plan.HomeView;
import com.example.aichinese.dto.plan.PlanRequests;
import com.example.aichinese.dto.plan.PlanViews;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.learn.LevelCheckService;
import com.example.aichinese.service.plan.LearningPlanService;
import com.example.aichinese.service.plan.StudentHomeService;

import lombok.RequiredArgsConstructor;

/**
 * The student's Home and learning plan: setup with its adaptive level check, the plan page, a new goal and a retaken
 * level check.
 */
@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class StudentPlanController {

    private final LearningPlanService planService;
    private final StudentHomeService homeService;
    private final LevelCheckService levelCheckService;

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

    /** "Build my plan" in the setup: the plan waits for {@code POST /plan/confirm} ("Start my path"). */
    @PostMapping("/plan")
    public ApiResponse<PlanViews.Plan> build(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody PlanRequests.BuildRequest request) {
        return ApiResponse.success("Your learning plan is ready.", planService.build(currentUser, request));
    }

    /** "Start my path": the plan the setup built becomes the learner's plan. */
    @PostMapping("/plan/confirm")
    public ApiResponse<PlanViews.Plan> confirm(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Your learning plan has started.", planService.confirm(currentUser));
    }

    /** "Update my goal": changes the plan at once, or, before the setup's plan was started, rebuilds that plan. */
    @PutMapping("/plan/goal")
    public ApiResponse<PlanViews.Plan> updateGoal(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody PlanRequests.UpdateGoalRequest request) {
        return ApiResponse.success("Plan updated.", planService.updateGoal(currentUser, request));
    }

    /** "Pick this first": the optional class to take first, on the setup's plan or the learner's plan. */
    @PutMapping("/plan/class-pick")
    public ApiResponse<PlanViews.Plan> pickClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody PlanRequests.ClassPickRequest request) {
        return ApiResponse.success("Class order saved.", planService.pickClass(currentUser, request.classId()));
    }

    /** The next step of the adaptive level check (stateless: every call sends the answers so far). */
    @PostMapping("/level-check/next")
    public ApiResponse<LevelCheckNextResponse> levelCheckNext(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody LevelCheckNextRequest request) {
        return ApiResponse.success("Level check step retrieved successfully.", levelCheckService.next(currentUser, request));
    }

    @PostMapping("/plan/level-check")
    public ApiResponse<PlanViews.RetakeResult> retake(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody PlanRequests.RetakeRequest request) {
        return ApiResponse.success("Level check saved.", planService.retake(currentUser, request));
    }

    /** "Set up later", also after the plan was built: a plan that was not started is removed. */
    @PostMapping("/plan/skip")
    public ApiResponse<Void> skip(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        planService.skip(currentUser);
        return ApiResponse.success("You can build your plan any time from Home.", null);
    }
}
