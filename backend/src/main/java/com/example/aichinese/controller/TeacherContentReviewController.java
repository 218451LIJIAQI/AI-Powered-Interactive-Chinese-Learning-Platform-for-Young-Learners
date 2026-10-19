package com.example.aichinese.controller;

import java.util.Objects;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.content.CoursePackageResponse;
import com.example.aichinese.dto.content.LearningModuleResponse;
import com.example.aichinese.dto.content.LessonResponse;
import com.example.aichinese.dto.learning.TeacherQuizResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.TeacherLessonService;
import com.example.aichinese.service.TeacherModuleService;
import com.example.aichinese.service.TeacherPackageService;
import com.example.aichinese.service.TeacherQuizService;

/**
 * Teacher "submit for review" actions (D4): DRAFT or REJECTED content moves to SUBMITTED and
 * waits for an admin decision. Approval status is enforced by {@code ApprovedTeacherInterceptor}.
 */
@Validated
@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherContentReviewController {

    private static final String SUBMITTED_MESSAGE = "Submitted for admin review.";

    private final TeacherPackageService teacherPackageService;
    private final TeacherModuleService teacherModuleService;
    private final TeacherLessonService teacherLessonService;
    private final TeacherQuizService teacherQuizService;

    public TeacherContentReviewController(
            TeacherPackageService teacherPackageService,
            TeacherModuleService teacherModuleService,
            TeacherLessonService teacherLessonService,
            TeacherQuizService teacherQuizService) {
        this.teacherPackageService = Objects.requireNonNull(teacherPackageService, "teacherPackageService");
        this.teacherModuleService = Objects.requireNonNull(teacherModuleService, "teacherModuleService");
        this.teacherLessonService = Objects.requireNonNull(teacherLessonService, "teacherLessonService");
        this.teacherQuizService = Objects.requireNonNull(teacherQuizService, "teacherQuizService");
    }

    @PostMapping("/packages/{id}/submit")
    public ApiResponse<CoursePackageResponse> submitPackage(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(SUBMITTED_MESSAGE, teacherPackageService.submitPackage(currentUser, id));
    }

    @PostMapping("/modules/{id}/submit")
    public ApiResponse<LearningModuleResponse> submitModule(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(SUBMITTED_MESSAGE, teacherModuleService.submitModule(currentUser, id));
    }

    @PostMapping("/lessons/{id}/submit")
    public ApiResponse<LessonResponse> submitLesson(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(SUBMITTED_MESSAGE, teacherLessonService.submitLesson(currentUser, id));
    }

    @GetMapping("/quizzes/{quizId}")
    public ApiResponse<TeacherQuizResponse> getQuiz(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long quizId) {
        return ApiResponse.success("Quiz retrieved successfully.", teacherQuizService.getQuiz(currentUser, quizId));
    }

    @PostMapping("/quizzes/{quizId}/submit")
    public ApiResponse<TeacherQuizResponse> submitQuiz(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long quizId) {
        return ApiResponse.success(SUBMITTED_MESSAGE, teacherQuizService.submitQuiz(currentUser, quizId));
    }
}
