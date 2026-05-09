package com.example.aichinese.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.communication.ModerationRecordResponse;
import com.example.aichinese.dto.communication.ReportResponse;
import com.example.aichinese.dto.communication.ReviewResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ContentModerationService;
import com.example.aichinese.service.ReportService;
import com.example.aichinese.service.ReviewService;

@Validated
@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherCommunicationController {

    private final ReviewService reviewService;
    private final ReportService reportService;
    private final ContentModerationService contentModerationService;

    public TeacherCommunicationController(
            ReviewService reviewService,
            ReportService reportService,
            ContentModerationService contentModerationService) {
        this.reviewService = reviewService;
        this.reportService = reportService;
        this.contentModerationService = contentModerationService;
    }

    @GetMapping("/reviews")
    public ApiResponse<List<ReviewResponse>> getTeacherReviews(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher reviews retrieved successfully.",
                reviewService.getTeacherReviews(currentUser));
    }

    @GetMapping("/reports")
    public ApiResponse<List<ReportResponse>> getTeacherReports(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher reports retrieved successfully.",
                reportService.getTeacherReports(currentUser));
    }

    @GetMapping("/reports/{id}")
    public ApiResponse<ReportResponse> getTeacherReport(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Teacher report retrieved successfully.",
                reportService.getTeacherReport(currentUser, id));
    }

    @GetMapping("/moderation-records")
    public ApiResponse<List<ModerationRecordResponse>> getTeacherModerationRecords(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher moderation records retrieved successfully.",
                contentModerationService.getTeacherModerationRecords(currentUser));
    }
}
