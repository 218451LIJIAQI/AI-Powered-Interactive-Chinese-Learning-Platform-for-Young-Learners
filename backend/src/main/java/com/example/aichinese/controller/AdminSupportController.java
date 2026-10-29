package com.example.aichinese.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.support.SupportQueueResponse;
import com.example.aichinese.dto.support.SupportReplyRequest;
import com.example.aichinese.dto.support.SupportRequestDetailResponse;
import com.example.aichinese.entity.enums.SupportRequestStatus;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.support.SupportService;

import jakarta.validation.Valid;

/** The admin Support queue: Open / Answered / Solved tabs, one request, reply and mark as solved. */
@RestController
@RequestMapping("/api/v1/admin/support")
public class AdminSupportController {

    private final SupportService supportService;

    public AdminSupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping
    public ApiResponse<SupportQueueResponse> queue(
            @RequestParam(value = "status", defaultValue = "OPEN") String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        SupportRequestStatus parsed = SupportRequestStatus.tryFrom(status)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid support status filter."));
        return ApiResponse.success("Support queue retrieved successfully.", supportService.queue(parsed, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<SupportRequestDetailResponse> get(@PathVariable Long id) {
        return ApiResponse.success("Support request retrieved successfully.", supportService.get(id));
    }

    @PostMapping("/{id}/replies")
    public ApiResponse<SupportRequestDetailResponse> reply(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody SupportReplyRequest request) {
        return ApiResponse.success("Reply sent.", supportService.reply(currentUser, id, request.body()));
    }

    @PostMapping("/{id}/solve")
    public ApiResponse<SupportRequestDetailResponse> solve(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success("Marked as solved.", supportService.solve(currentUser, id));
    }

    @GetMapping("/attachments/{messageId}")
    public ResponseEntity<Resource> attachment(@PathVariable Long messageId) {
        return FileDownloadResponseBuilder.buildAttachmentDownloadResponse(supportService.attachment(messageId));
    }
}
