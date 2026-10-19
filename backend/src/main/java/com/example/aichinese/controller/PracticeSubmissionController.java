package com.example.aichinese.controller;

import java.util.Objects;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.practice.PracticeSubmissionFeedbackRequest;
import com.example.aichinese.dto.practice.PracticeSubmissionResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.PracticeSubmissionService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Practice submissions (D7 / D7a): students list their own pronunciation recordings and
 * handwriting images with AI and teacher feedback; approved teachers review submissions of
 * students they teach; admins can view them for moderation. Role gates come from the URL prefix
 * (SecurityConfig) and the teacher approval interceptor; ownership is checked in the service.
 */
@Validated
@RestController
public class PracticeSubmissionController {

    private final PracticeSubmissionService practiceSubmissionService;

    public PracticeSubmissionController(PracticeSubmissionService practiceSubmissionService) {
        this.practiceSubmissionService = Objects.requireNonNull(practiceSubmissionService, "practiceSubmissionService");
    }

    // ----- student -----

    @GetMapping("/api/v1/students/practice-submissions")
    @Operation(summary = "List my practice submissions", description = "Own pronunciation/handwriting uploads with AI-assisted feedback (not a score) and teacher feedback.")
    public ApiResponse<PageResponse<PracticeSubmissionResponse>> getStudentSubmissions(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Size(max = 30, message = "Type must not exceed 30 characters.")
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Practice submissions retrieved successfully.",
                practiceSubmissionService.getStudentSubmissions(currentUser, type, page, size));
    }

    @GetMapping("/api/v1/students/practice-submissions/{id}")
    public ApiResponse<PracticeSubmissionResponse> getStudentSubmission(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Practice submission retrieved successfully.",
                practiceSubmissionService.getStudentSubmission(currentUser, id));
    }

    @GetMapping("/api/v1/students/practice-submissions/{id}/file")
    public ResponseEntity<Resource> streamStudentFile(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return FileDownloadResponseBuilder.buildInlineDownloadResponse(
                practiceSubmissionService.loadStudentFile(currentUser, id));
    }

    // ----- approved teacher -----

    @GetMapping("/api/v1/teachers/practice-submissions")
    @Operation(summary = "List practice submissions to review", description = "Submissions of students on the teacher's roster or linked to the teacher's lessons; reviewStatus defaults to SUBMITTED.")
    public ApiResponse<PageResponse<PracticeSubmissionResponse>> getTeacherSubmissions(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Size(max = 20, message = "Review status must not exceed 20 characters.")
            @RequestParam(value = "reviewStatus", required = false) String reviewStatus,
            @Size(max = 30, message = "Type must not exceed 30 characters.")
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Practice submissions retrieved successfully.",
                practiceSubmissionService.getTeacherSubmissions(currentUser, reviewStatus, type, page, size));
    }

    @GetMapping("/api/v1/teachers/practice-submissions/{id}")
    public ApiResponse<PracticeSubmissionResponse> getTeacherSubmission(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Practice submission retrieved successfully.",
                practiceSubmissionService.getTeacherSubmission(currentUser, id));
    }

    @GetMapping("/api/v1/teachers/practice-submissions/{id}/file")
    public ResponseEntity<Resource> streamTeacherFile(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return FileDownloadResponseBuilder.buildInlineDownloadResponse(
                practiceSubmissionService.loadTeacherFile(currentUser, id));
    }

    @PostMapping("/api/v1/teachers/practice-submissions/{id}/feedback")
    @Operation(summary = "Review a practice submission", description = "Writes teacher feedback linked to the submission, marks it REVIEWED and notifies the student.")
    public ApiResponse<PracticeSubmissionResponse> reviewSubmission(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody PracticeSubmissionFeedbackRequest request) {
        return ApiResponse.success(
                "Practice submission reviewed successfully.",
                practiceSubmissionService.reviewSubmission(currentUser, id, request));
    }

    // ----- admin (moderation, read-only) -----

    @GetMapping("/api/v1/admin/practice-submissions")
    public ApiResponse<PageResponse<PracticeSubmissionResponse>> getAdminSubmissions(
            @Size(max = 20, message = "Review status must not exceed 20 characters.")
            @RequestParam(value = "reviewStatus", required = false) String reviewStatus,
            @Size(max = 30, message = "Type must not exceed 30 characters.")
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "studentId", required = false) Long studentId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Practice submissions retrieved successfully.",
                practiceSubmissionService.getAdminSubmissions(reviewStatus, type, studentId, page, size));
    }

    @GetMapping("/api/v1/admin/practice-submissions/{id}")
    public ApiResponse<PracticeSubmissionResponse> getAdminSubmission(@PathVariable Long id) {
        return ApiResponse.success(
                "Practice submission retrieved successfully.",
                practiceSubmissionService.getAdminSubmission(id));
    }

    @GetMapping("/api/v1/admin/practice-submissions/{id}/file")
    public ResponseEntity<Resource> streamAdminFile(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return FileDownloadResponseBuilder.buildInlineDownloadResponse(
                practiceSubmissionService.loadAdminFile(currentUser, id));
    }
}
