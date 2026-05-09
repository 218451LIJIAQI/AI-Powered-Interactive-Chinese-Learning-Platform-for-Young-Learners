package com.example.aichinese.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.communication.CreateTeacherFeedbackRequest;
import com.example.aichinese.dto.communication.FeedbackCandidateResponse;
import com.example.aichinese.dto.communication.TeacherFeedbackResponse;
import com.example.aichinese.dto.communication.UpdateTeacherFeedbackRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.TeacherFeedbackService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/teachers/feedback")
public class TeacherFeedbackController {

    private final TeacherFeedbackService teacherFeedbackService;

    public TeacherFeedbackController(TeacherFeedbackService teacherFeedbackService) {
        this.teacherFeedbackService = teacherFeedbackService;
    }

    @PostMapping
    public ApiResponse<TeacherFeedbackResponse> createFeedback(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CreateTeacherFeedbackRequest request) {
        return ApiResponse.success(
                "Teacher feedback created successfully.",
                teacherFeedbackService.createFeedback(currentUser, request));
    }

    @GetMapping
    public ApiResponse<List<TeacherFeedbackResponse>> getFeedback(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher feedback history retrieved successfully.",
                teacherFeedbackService.getTeacherFeedback(currentUser));
    }

    @PutMapping("/{id}")
    public ApiResponse<TeacherFeedbackResponse> updateFeedback(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateTeacherFeedbackRequest request) {
        return ApiResponse.success(
                "Teacher feedback updated successfully.",
                teacherFeedbackService.updateFeedback(currentUser, id, request));
    }

    @GetMapping("/candidates")
    public ApiResponse<List<FeedbackCandidateResponse>> getFeedbackCandidates(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher feedback candidates retrieved successfully.",
                teacherFeedbackService.getTeacherFeedbackCandidates(currentUser));
    }
}
