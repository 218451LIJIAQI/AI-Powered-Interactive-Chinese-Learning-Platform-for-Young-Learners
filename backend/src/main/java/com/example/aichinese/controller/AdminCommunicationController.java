package com.example.aichinese.controller;

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
import com.example.aichinese.dto.ai.AdminAiResponseReportResponse;
import com.example.aichinese.dto.ai.ReviewAiResponseReportRequest;
import com.example.aichinese.dto.communication.AuditLogResponse;
import com.example.aichinese.dto.communication.CreateModerationActionRequest;
import com.example.aichinese.dto.communication.ModerationRecordResponse;
import com.example.aichinese.dto.communication.NotificationResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.AiResponseReportService;
import com.example.aichinese.service.ContentModerationService;
import com.example.aichinese.service.NotificationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Admin-side communication surface: cross-user notification
 * dispatch, content moderation actions (warn / hide / restore), and
 * the moderation audit history view.
 *
 * <h2>Moderation</h2>
 * <p>Actions are recorded as {@link com.example.aichinese.entity.ContentModerationRecord}
 * rows via {@link com.example.aichinese.service.ContentModerationService};
 * each carries the admin note that the affected teacher will see, so
 * a teacher contesting a decision can be shown the exact stated
 * reason. Notes are sanitized + length-capped through
 * {@link com.example.aichinese.util.AiInputSanitizer}.</p>
 *
 * <h2>Notifications</h2>
 * <p>Admin-pushed notifications go through
 * {@link com.example.aichinese.service.NotificationService}'s
 * producer surface so dispatch, audit, and downstream side-effects
 * (in-app + optional email fallback) stay uniform with system-
 * generated notifications.</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminCommunicationController {

    private final NotificationService notificationService;
    private final ContentModerationService contentModerationService;
    private final AuditLogService auditLogService;
    private final AiResponseReportService aiResponseReportService;

    public AdminCommunicationController(
            NotificationService notificationService,
            ContentModerationService contentModerationService,
            AuditLogService auditLogService,
            AiResponseReportService aiResponseReportService) {
        this.notificationService = notificationService;
        this.contentModerationService = contentModerationService;
        this.auditLogService = auditLogService;
        this.aiResponseReportService = aiResponseReportService;
    }

    @GetMapping("/notifications")
    public ApiResponse<PageResponse<NotificationResponse>> getAdminNotifications(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Admin notifications retrieved successfully.",
                notificationService.getCurrentUserNotifications(currentUser, page, size));
    }

    @GetMapping("/moderation-records")
    public ApiResponse<PageResponse<ModerationRecordResponse>> getModerationRecords(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Moderation records retrieved successfully.",
                contentModerationService.getAdminModerationRecords(page, size));
    }

    @GetMapping("/moderation-records/{id}")
    public ApiResponse<ModerationRecordResponse> getModerationRecord(@PathVariable Long id) {
        return ApiResponse.success(
                "Moderation record retrieved successfully.",
                contentModerationService.getAdminModerationRecord(id));
    }

    @GetMapping("/ai-response-reports")
    public ApiResponse<PageResponse<AdminAiResponseReportResponse>> getAiResponseReports(
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "AI response report queue retrieved successfully.",
                aiResponseReportService.getAdminReports(status, page, size));
    }

    @GetMapping("/ai-response-reports/{id}")
    public ApiResponse<AdminAiResponseReportResponse> getAiResponseReport(@PathVariable Long id) {
        return ApiResponse.success(
                "AI response report retrieved successfully.",
                aiResponseReportService.getAdminReport(id));
    }

    @PostMapping("/ai-response-reports/{id}/review")
    public ApiResponse<AdminAiResponseReportResponse> reviewAiResponseReport(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody ReviewAiResponseReportRequest request) {
        return ApiResponse.success(
                "AI response report reviewed successfully.",
                aiResponseReportService.reviewReport(currentUser, id, request));
    }

    @PostMapping("/moderation/hide")
    public ApiResponse<ModerationRecordResponse> hideContent(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CreateModerationActionRequest request) {
        return ApiResponse.success(
                "Moderation action applied successfully.",
                contentModerationService.hide(currentUser, request));
    }

    @PostMapping("/moderation/unpublish")
    public ApiResponse<ModerationRecordResponse> unpublishContent(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CreateModerationActionRequest request) {
        return ApiResponse.success(
                "Moderation action applied successfully.",
                contentModerationService.unpublish(currentUser, request));
    }

    @PostMapping("/moderation/archive")
    public ApiResponse<ModerationRecordResponse> archiveContent(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CreateModerationActionRequest request) {
        return ApiResponse.success(
                "Moderation action applied successfully.",
                contentModerationService.archive(currentUser, request));
    }

    @PostMapping("/moderation/request-revision")
    public ApiResponse<ModerationRecordResponse> requestContentRevision(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CreateModerationActionRequest request) {
        return ApiResponse.success(
                "Moderation action applied successfully.",
                contentModerationService.requestRevision(currentUser, request));
    }

    @PostMapping("/moderation/restore")
    public ApiResponse<ModerationRecordResponse> restoreContent(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CreateModerationActionRequest request) {
        return ApiResponse.success(
                "Moderation action applied successfully.",
                contentModerationService.restore(currentUser, request));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResponse<AuditLogResponse>> getAuditLogs(
            @Size(max = 80, message = "Action type must not exceed 80 characters.")
            @RequestParam(value = "actionType", required = false) String actionType,
            @Size(max = 80, message = "Target type must not exceed 80 characters.")
            @RequestParam(value = "targetType", required = false) String targetType,
            @Size(max = 200, message = "Actor must not exceed 200 characters.")
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Audit logs retrieved successfully.",
                auditLogService.getAuditLogs(actionType, targetType, actor, page, size));
    }

    @GetMapping("/audit-logs/{id}")
    public ApiResponse<AuditLogResponse> getAuditLog(@PathVariable Long id) {
        return ApiResponse.success("Audit log retrieved successfully.", auditLogService.getAuditLog(id));
    }

}
