package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.communication.StudentTeacherFeedbackResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.TeacherFeedbackService;

@Validated
@RestController
@RequestMapping("/api/v1/students/feedback")
public class StudentFeedbackController {

    private final TeacherFeedbackService teacherFeedbackService;

    public StudentFeedbackController(TeacherFeedbackService teacherFeedbackService) {
        this.teacherFeedbackService = teacherFeedbackService;
    }

    @GetMapping
    public ApiResponse<PageResponse<StudentTeacherFeedbackResponse>> getFeedback(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Teacher feedback retrieved successfully.",
                teacherFeedbackService.getStudentFeedback(currentUser, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<StudentTeacherFeedbackResponse> getFeedbackById(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Teacher feedback retrieved successfully.",
                teacherFeedbackService.getStudentFeedbackById(currentUser, id));
    }
}
