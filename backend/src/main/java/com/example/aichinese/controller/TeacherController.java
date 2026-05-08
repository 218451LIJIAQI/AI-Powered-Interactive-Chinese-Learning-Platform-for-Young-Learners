package com.example.aichinese.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.dashboard.TeacherDashboardResponse;
import com.example.aichinese.dto.teacher.TeacherProfileResponse;
import com.example.aichinese.dto.teacher.TeacherVerificationStatusResponse;
import com.example.aichinese.dto.teacher.UpdateTeacherProfileRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.DashboardService;
import com.example.aichinese.service.TeacherManagementService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherController {

    private final DashboardService dashboardService;
    private final TeacherManagementService teacherManagementService;

    public TeacherController(
            DashboardService dashboardService,
            TeacherManagementService teacherManagementService) {
        this.dashboardService = dashboardService;
        this.teacherManagementService = teacherManagementService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<TeacherDashboardResponse> getDashboard(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher dashboard retrieved successfully.",
                dashboardService.getTeacherDashboard(currentUser));
    }

    @GetMapping("/me")
    public ApiResponse<TeacherProfileResponse> getCurrentTeacherProfile(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher profile retrieved successfully.",
                teacherManagementService.getCurrentTeacherProfile(currentUser));
    }

    @PutMapping("/me/profile")
    public ApiResponse<TeacherProfileResponse> updateCurrentTeacherProfile(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody UpdateTeacherProfileRequest request) {
        return ApiResponse.success(
                "Teacher profile updated successfully.",
                teacherManagementService.updateCurrentTeacherProfile(currentUser, request));
    }

    @GetMapping("/verification-status")
    public ApiResponse<TeacherVerificationStatusResponse> getVerificationStatus(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher verification status retrieved successfully.",
                teacherManagementService.getTeacherVerificationStatus(currentUser));
    }

    @PostMapping(value = "/verification-documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TeacherVerificationStatusResponse> uploadVerificationDocument(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "description", required = false) String description) {
        return ApiResponse.success(
                "Verification document uploaded successfully.",
                teacherManagementService.uploadTeacherVerificationDocument(currentUser, file, documentType, description));
    }

    @PostMapping("/resubmit-verification")
    public ApiResponse<TeacherVerificationStatusResponse> resubmitVerification(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher verification resubmitted successfully.",
                teacherManagementService.resubmitVerification(currentUser));
    }

    @GetMapping("/verification-documents/{id}")
    public ResponseEntity<Resource> getTeacherOwnedDocument(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        Resource resource = teacherManagementService.getTeacherOwnedDocument(currentUser, id);
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(resource)
                        .orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
