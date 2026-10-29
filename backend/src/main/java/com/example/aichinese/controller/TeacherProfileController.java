package com.example.aichinese.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
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
import com.example.aichinese.dto.teacher.SubmitVerificationRequest;
import com.example.aichinese.dto.teacher.TeacherProfileResponse;
import com.example.aichinese.dto.teacher.UpdateTeacherProfileRequest;
import com.example.aichinese.entity.enums.TeacherVerificationDocumentType;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StoredFileDownload;
import com.example.aichinese.service.teacher.TeacherVerificationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * The Teaching profile page: public profile and verification. Open to teachers who are not approved yet
 * (ApprovedTeacherInterceptor.OPEN_TEACHER_PATHS).
 */
@Validated
@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherProfileController {

    private final TeacherVerificationService teacherVerificationService;

    public TeacherProfileController(TeacherVerificationService teacherVerificationService) {
        this.teacherVerificationService = teacherVerificationService;
    }

    @GetMapping("/me")
    public ApiResponse<TeacherProfileResponse> getProfile(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher profile retrieved successfully.",
                teacherVerificationService.getProfile(currentUser));
    }

    @PutMapping("/me/profile")
    public ApiResponse<TeacherProfileResponse> updateProfile(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody UpdateTeacherProfileRequest request) {
        return ApiResponse.success(
                "Teacher profile updated successfully.",
                teacherVerificationService.updateProfile(currentUser, request));
    }

    @PostMapping(value = "/verification-documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TeacherProfileResponse> uploadDocument(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam("file") MultipartFile file,
            @Size(max = 40, message = "Document type must not exceed 40 characters.")
            @RequestParam(value = "documentType", required = false) String documentType) {
        return ApiResponse.success(
                "Verification document uploaded successfully.",
                teacherVerificationService.uploadDocument(
                        currentUser, file, TeacherVerificationDocumentType.tryFrom(documentType).orElse(null)));
    }

    @GetMapping("/verification-documents/{id}")
    public ResponseEntity<Resource> getOwnDocument(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        StoredFileDownload download = teacherVerificationService.getOwnDocument(currentUser, id);
        return FileDownloadResponseBuilder.buildAttachmentDownloadResponse(download);
    }

    @PostMapping("/verification/submit")
    public ApiResponse<TeacherProfileResponse> submit(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody SubmitVerificationRequest request) {
        return ApiResponse.success(
                "Submitted for verification.",
                teacherVerificationService.submit(currentUser));
    }
}
