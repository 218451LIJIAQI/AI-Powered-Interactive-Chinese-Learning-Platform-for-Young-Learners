package com.example.aichinese.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.support.CreateSupportRequest;
import com.example.aichinese.dto.support.SupportReplyRequest;
import com.example.aichinese.dto.support.SupportRequestDetailResponse;
import com.example.aichinese.dto.support.SupportRequestSummaryResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.support.SupportService;

import jakarta.validation.Valid;

/** Help & support for signed-in students and teachers (applicants included): their own requests only. */
@RestController
@RequestMapping("/api/v1/users/me/support")
public class SupportController {

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping
    public ApiResponse<List<SupportRequestSummaryResponse>> list(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Support requests retrieved successfully.", supportService.listOwn(currentUser));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SupportRequestDetailResponse> create(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @ModelAttribute CreateSupportRequest request,
            @RequestParam(value = "screenshot", required = false) MultipartFile screenshot) {
        return ApiResponse.success("Support request sent.", supportService.create(currentUser, request, screenshot));
    }

    @GetMapping("/{id}")
    public ApiResponse<SupportRequestDetailResponse> get(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success("Support request retrieved successfully.", supportService.getOwn(currentUser, id));
    }

    @PostMapping("/{id}/replies")
    public ApiResponse<SupportRequestDetailResponse> reply(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody SupportReplyRequest request) {
        return ApiResponse.success("Reply sent.", supportService.replyAsRequester(currentUser, id, request.body()));
    }

    @PostMapping("/{id}/solve")
    public ApiResponse<SupportRequestDetailResponse> solve(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success("Marked as solved.", supportService.solveAsRequester(currentUser, id));
    }

    @GetMapping("/attachments/{messageId}")
    public ResponseEntity<Resource> attachment(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long messageId) {
        return FileDownloadResponseBuilder.buildAttachmentDownloadResponse(
                supportService.ownAttachment(currentUser, messageId));
    }
}
