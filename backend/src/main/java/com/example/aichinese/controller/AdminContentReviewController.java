package com.example.aichinese.controller;

import java.util.Objects;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.review.ContentReviewDetailResponse;
import com.example.aichinese.dto.review.ContentReviewItemResponse;
import com.example.aichinese.dto.review.ContentReviewNoteRequest;
import com.example.aichinese.dto.review.ContentReviewRejectRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ContentReviewService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Admin content review queue (D4, FR-ADM-06/07, FR-CONT-11/12): submitted courses, modules,
 * lessons, quizzes and practice activities with approve, reject (reason required), archive and
 * restore decisions.
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/content-review")
public class AdminContentReviewController {

    private final ContentReviewService contentReviewService;

    public AdminContentReviewController(ContentReviewService contentReviewService) {
        this.contentReviewService = Objects.requireNonNull(contentReviewService, "contentReviewService");
    }

    @GetMapping
    public ApiResponse<PageResponse<ContentReviewItemResponse>> listQueue(
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @Size(max = 40, message = "Content type must not exceed 40 characters.")
            @RequestParam(value = "type", required = false) String type,
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Content review queue retrieved successfully.",
                contentReviewService.listQueue(status, type, keyword, page, size));
    }

    @GetMapping("/{type}/{id}")
    public ApiResponse<ContentReviewDetailResponse> getDetail(
            @Size(max = 40, message = "Content type must not exceed 40 characters.")
            @PathVariable String type,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Content review detail retrieved successfully.",
                contentReviewService.getDetail(ContentReviewService.parseType(type), id));
    }

    @PostMapping("/{type}/{id}/approve")
    public ApiResponse<ContentReviewItemResponse> approve(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Size(max = 40, message = "Content type must not exceed 40 characters.")
            @PathVariable String type,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ContentReviewNoteRequest request) {
        return ApiResponse.success(
                "Content approved and published.",
                contentReviewService.approve(currentUser, ContentReviewService.parseType(type), id, noteOf(request)));
    }

    @PostMapping("/{type}/{id}/reject")
    public ApiResponse<ContentReviewItemResponse> reject(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Size(max = 40, message = "Content type must not exceed 40 characters.")
            @PathVariable String type,
            @PathVariable Long id,
            @Valid @RequestBody ContentReviewRejectRequest request) {
        return ApiResponse.success(
                "Content returned to the teacher.",
                contentReviewService.reject(currentUser, ContentReviewService.parseType(type), id, request.reason()));
    }

    @PostMapping("/{type}/{id}/archive")
    public ApiResponse<ContentReviewItemResponse> archive(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Size(max = 40, message = "Content type must not exceed 40 characters.")
            @PathVariable String type,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ContentReviewNoteRequest request) {
        return ApiResponse.success(
                "Content archived.",
                contentReviewService.archive(currentUser, ContentReviewService.parseType(type), id, noteOf(request)));
    }

    @PostMapping("/{type}/{id}/restore")
    public ApiResponse<ContentReviewItemResponse> restore(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Size(max = 40, message = "Content type must not exceed 40 characters.")
            @PathVariable String type,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ContentReviewNoteRequest request) {
        return ApiResponse.success(
                "Content restored and published.",
                contentReviewService.restore(currentUser, ContentReviewService.parseType(type), id, noteOf(request)));
    }

    private static String noteOf(ContentReviewNoteRequest request) {
        return request == null ? null : request.note();
    }
}
