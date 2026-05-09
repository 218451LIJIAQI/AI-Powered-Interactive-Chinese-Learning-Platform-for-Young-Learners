package com.example.aichinese.controller;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.communication.AdminReportActionRequest;
import com.example.aichinese.dto.communication.AuditLogResponse;
import com.example.aichinese.dto.communication.CreateModerationActionRequest;
import com.example.aichinese.dto.communication.ModerationRecordResponse;
import com.example.aichinese.dto.communication.NotificationResponse;
import com.example.aichinese.dto.communication.ReportResponse;
import com.example.aichinese.dto.communication.ReviewResponse;
import com.example.aichinese.dto.communication.SupportTicketDetailResponse;
import com.example.aichinese.dto.communication.SupportTicketMessageRequest;
import com.example.aichinese.dto.communication.SupportTicketResponse;
import com.example.aichinese.dto.communication.TeacherFeedbackResponse;
import com.example.aichinese.dto.communication.UpdateSupportTicketPriorityRequest;
import com.example.aichinese.dto.communication.UpdateSupportTicketStatusRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.ContentModerationService;
import com.example.aichinese.service.NotificationService;
import com.example.aichinese.service.ReportService;
import com.example.aichinese.service.ReviewService;
import com.example.aichinese.service.SupportTicketService;
import com.example.aichinese.service.TeacherFeedbackService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminCommunicationController {

    private final NotificationService notificationService;
    private final ReportService reportService;
    private final ReviewService reviewService;
    private final SupportTicketService supportTicketService;
    private final ContentModerationService contentModerationService;
    private final AuditLogService auditLogService;
    private final TeacherFeedbackService teacherFeedbackService;

    public AdminCommunicationController(
            NotificationService notificationService,
            ReportService reportService,
            ReviewService reviewService,
            SupportTicketService supportTicketService,
            ContentModerationService contentModerationService,
            AuditLogService auditLogService,
            TeacherFeedbackService teacherFeedbackService) {
        this.notificationService = notificationService;
        this.reportService = reportService;
        this.reviewService = reviewService;
        this.supportTicketService = supportTicketService;
        this.contentModerationService = contentModerationService;
        this.auditLogService = auditLogService;
        this.teacherFeedbackService = teacherFeedbackService;
    }

    @GetMapping("/notifications")
    public ApiResponse<List<NotificationResponse>> getAdminNotifications(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Admin notifications retrieved successfully.",
                notificationService.getCurrentUserNotifications(currentUser));
    }

    @GetMapping("/reports")
    public ApiResponse<List<ReportResponse>> getReports() {
        return ApiResponse.success(
                "Reports retrieved successfully.",
                reportService.getAdminReports());
    }

    @GetMapping("/reports/{id}")
    public ApiResponse<ReportResponse> getReport(@PathVariable Long id) {
        return ApiResponse.success(
                "Report retrieved successfully.",
                reportService.getAdminReport(id));
    }

    @PatchMapping("/reports/{id}/review")
    public ApiResponse<ReportResponse> reviewReport(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminReportActionRequest request) {
        return ApiResponse.success(
                "Report reviewed successfully.",
                reportService.reviewReport(currentUser, id, request));
    }

    @PatchMapping("/reports/{id}/dismiss")
    public ApiResponse<ReportResponse> dismissReport(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminReportActionRequest request) {
        return ApiResponse.success(
                "Report dismissed successfully.",
                reportService.dismissReport(currentUser, id, request));
    }

    @PatchMapping("/reports/{id}/mark-action-taken")
    public ApiResponse<ReportResponse> markActionTaken(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminReportActionRequest request) {
        return ApiResponse.success(
                "Report marked as action taken successfully.",
                reportService.markActionTaken(currentUser, id, request));
    }

    @PatchMapping("/reports/{id}/request-revision")
    public ApiResponse<ReportResponse> requestRevision(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminReportActionRequest request) {
        return ApiResponse.success(
                "Teacher revision requested successfully.",
                reportService.requestRevision(currentUser, id, request));
    }

    @GetMapping("/reviews")
    public ApiResponse<List<ReviewResponse>> getReviews() {
        return ApiResponse.success(
                "Reviews retrieved successfully.",
                reviewService.getAdminReviews());
    }

    @PatchMapping("/reviews/{id}/hide")
    public ApiResponse<ReviewResponse> hideReview(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Review hidden successfully.",
                reviewService.hideReview(currentUser, id));
    }

    @PatchMapping("/reviews/{id}/show")
    public ApiResponse<ReviewResponse> showReview(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Review restored successfully.",
                reviewService.showReview(currentUser, id));
    }

    @PatchMapping("/reviews/{id}/flag")
    public ApiResponse<ReviewResponse> flagReview(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Review flagged successfully.",
                reviewService.flagReview(currentUser, id));
    }

    @GetMapping("/support-tickets")
    public ApiResponse<List<SupportTicketResponse>> getSupportTickets() {
        return ApiResponse.success(
                "Support tickets retrieved successfully.",
                supportTicketService.getAdminTickets());
    }

    @GetMapping("/support-tickets/{id}")
    public ApiResponse<SupportTicketDetailResponse> getSupportTicket(@PathVariable Long id) {
        return ApiResponse.success(
                "Support ticket retrieved successfully.",
                supportTicketService.getAdminTicket(id));
    }

    @PostMapping("/support-tickets/{id}/messages")
    public ApiResponse<SupportTicketDetailResponse> addSupportTicketMessage(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody SupportTicketMessageRequest request) {
        return ApiResponse.success(
                "Support ticket reply sent successfully.",
                supportTicketService.addAdminMessage(currentUser, id, request));
    }

    @PatchMapping("/support-tickets/{id}/status")
    public ApiResponse<SupportTicketResponse> updateSupportTicketStatus(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSupportTicketStatusRequest request) {
        return ApiResponse.success(
                "Support ticket status updated successfully.",
                supportTicketService.updateStatus(currentUser, id, request));
    }

    @PatchMapping("/support-tickets/{id}/priority")
    public ApiResponse<SupportTicketResponse> updateSupportTicketPriority(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSupportTicketPriorityRequest request) {
        return ApiResponse.success(
                "Support ticket priority updated successfully.",
                supportTicketService.updatePriority(currentUser, id, request));
    }

    @GetMapping("/moderation-records")
    public ApiResponse<List<ModerationRecordResponse>> getModerationRecords() {
        return ApiResponse.success(
                "Moderation records retrieved successfully.",
                contentModerationService.getAdminModerationRecords());
    }

    @GetMapping("/moderation-records/{id}")
    public ApiResponse<ModerationRecordResponse> getModerationRecord(@PathVariable Long id) {
        return ApiResponse.success(
                "Moderation record retrieved successfully.",
                contentModerationService.getAdminModerationRecord(id));
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
    public ApiResponse<List<AuditLogResponse>> getAuditLogs(
            @RequestParam(value = "actionType", required = false) String actionType,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "actor", required = false) String actor) {
        Stream<AuditLogResponse> stream = auditLogService.getAuditLogs().stream();
        if (actionType != null && !actionType.isBlank()) {
            stream = stream.filter(item -> item.actionType() != null && item.actionType().equalsIgnoreCase(actionType.trim()));
        }
        if (targetType != null && !targetType.isBlank()) {
            stream = stream.filter(item -> item.targetType() != null && item.targetType().equalsIgnoreCase(targetType.trim()));
        }
        if (actor != null && !actor.isBlank()) {
            stream = stream.filter(item -> item.actorEmail() != null && item.actorEmail().toLowerCase().contains(actor.trim().toLowerCase()));
        }
        return ApiResponse.success("Audit logs retrieved successfully.", stream.toList());
    }

    @GetMapping("/audit-logs/{id}")
    public ApiResponse<AuditLogResponse> getAuditLog(@PathVariable Long id) {
        AuditLogResponse auditLog = auditLogService.getAuditLogs().stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Audit log was not found."));
        return ApiResponse.success("Audit log retrieved successfully.", auditLog);
    }

    @GetMapping("/feedback")
    public ApiResponse<List<TeacherFeedbackResponse>> getFeedback() {
        return ApiResponse.success(
                "Teacher feedback records retrieved successfully.",
                teacherFeedbackService.getAdminFeedback());
    }
}
