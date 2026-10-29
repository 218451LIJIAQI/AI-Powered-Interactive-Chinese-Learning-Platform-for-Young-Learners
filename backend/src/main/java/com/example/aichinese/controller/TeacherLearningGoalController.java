package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.plan.PlanViews;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.plan.LearningPlanService;

/** The read-only learning goal of a student in one of the teacher's classes. */
@RestController
@RequestMapping("/api/v1/teachers/students")
public class TeacherLearningGoalController {

    private final LearningPlanService planService;

    public TeacherLearningGoalController(LearningPlanService planService) {
        this.planService = planService;
    }

    /** {@code data: null} when the student has no plan; 403 when the student is not in the teacher's classes. */
    @GetMapping("/{studentId}/learning-goal")
    public ApiResponse<PlanViews.TeacherGoal> learningGoal(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long studentId) {
        return ApiResponse.success("Learning goal retrieved successfully.", planService.teacherGoal(currentUser, studentId));
    }
}
