package com.example.aichinese.controller;

import java.time.LocalDate;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.admin.AdminStudentListItemResponse;
import com.example.aichinese.dto.admin.AdminTeacherDetailResponse;
import com.example.aichinese.dto.admin.AdminTeacherListItemResponse;
import com.example.aichinese.dto.admin.AdminUserListItemResponse;
import com.example.aichinese.dto.admin.AdminUserStatusUpdateRequest;
import com.example.aichinese.dto.admin.ApproveTeacherRequest;
import com.example.aichinese.dto.admin.RejectTeacherRequest;
import com.example.aichinese.dto.ai.AdminAiUsageLogResponse;
import com.example.aichinese.dto.analytics.AdminAnalyticsResponse;
import com.example.aichinese.dto.content.ContentApprovalRequest;
import com.example.aichinese.dto.content.ContentSubmissionDetailResponse;
import com.example.aichinese.dto.content.ContentSubmissionResponse;
import com.example.aichinese.dto.dashboard.AdminDashboardResponse;
import com.example.aichinese.dto.learning.LiveClassResponse;
import com.example.aichinese.entity.enums.ManagedContentType;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AdminContentReviewService;
import com.example.aichinese.service.AdminTeacherManagementService;
import com.example.aichinese.service.AdminUserManagementService;
import com.example.aichinese.service.AnalyticsService;
import com.example.aichinese.service.AiService;
import com.example.aichinese.service.DashboardService;
import com.example.aichinese.service.LiveClassService;
import com.example.aichinese.service.StoredFileDownload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Admin persona REST surface — dashboard, content review queue,
 * teacher management, user management, audit log, platform settings,
 * commission rate, AI usage / report review, and analytics.
 *
 * <h2>Authorization</h2>
 * <p>Class-level {@code hasRole("ADMIN")} is enforced in
 * {@link com.example.aichinese.config.SecurityConfig}; this surface
 * has no row-level filter because admins legitimately see everything.
 * Every state-changing call writes an audit row via
 * {@link com.example.aichinese.service.AuditLogService} so post-hoc
 * forensics can reconstruct who did what.</p>
 *
 * <h2>Input validation</h2>
 * <p>Free-text query parameters (search, status, sort) are
 * {@code @Size}-capped at the controller boundary; JSON bodies use
 * {@code @Valid}.</p>
 *
 * <h2>Sibling surfaces</h2>
 * <p>Commerce-tier admin actions (refunds, payouts) live on
 * {@link AdminCommerceController}; communication-tier (notifications,
 * moderation) on {@link AdminCommunicationController}; activity
 * library curation on {@link AdminActivityController}. This split
 * keeps each file under the soft 500-line authoring cap.</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final DashboardService dashboardService;
    private final AnalyticsService analyticsService;
    private final AdminTeacherManagementService adminTeacherManagementService;
    private final AdminUserManagementService adminUserManagementService;
    private final AdminContentReviewService adminContentReviewService;
    private final AiService aiService;
    private final LiveClassService liveClassService;

    public AdminController(
            DashboardService dashboardService,
            AnalyticsService analyticsService,
            AdminTeacherManagementService adminTeacherManagementService,
            AdminUserManagementService adminUserManagementService,
            AdminContentReviewService adminContentReviewService,
            AiService aiService,
            LiveClassService liveClassService) {
        this.dashboardService = dashboardService;
        this.analyticsService = analyticsService;
        this.adminTeacherManagementService = adminTeacherManagementService;
        this.adminUserManagementService = adminUserManagementService;
        this.adminContentReviewService = adminContentReviewService;
        this.aiService = aiService;
        this.liveClassService = liveClassService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> getDashboard(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Admin dashboard retrieved successfully.",
                dashboardService.getAdminDashboard(currentUser));
    }

    @GetMapping("/analytics")
    public ApiResponse<AdminAnalyticsResponse> getAnalytics(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Admin analytics retrieved successfully.",
                analyticsService.getAdminAnalytics(currentUser));
    }

    @GetMapping("/live-classes")
    public ApiResponse<PageResponse<LiveClassResponse>> getAdminLiveClasses(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Admin live classes retrieved successfully.",
                liveClassService.getAdminLiveClasses(currentUser, page, size));
    }

    @GetMapping("/live-classes/{id}")
    public ApiResponse<LiveClassResponse> getAdminLiveClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Admin live class retrieved successfully.",
                liveClassService.getAdminLiveClass(currentUser, id));
    }

    @GetMapping("/teachers")
    public ApiResponse<PageResponse<AdminTeacherListItemResponse>> listTeachers(
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @Size(max = 40, message = "Sort must not exceed 40 characters.")
            @RequestParam(value = "sort", required = false) String sort) {
        return ApiResponse.success(
                "Teachers retrieved successfully.",
                adminTeacherManagementService.listTeachers(status, keyword, page, size, sort));
    }

    @GetMapping("/teachers/{id}")
    public ApiResponse<AdminTeacherDetailResponse> getTeacherDetail(@PathVariable Long id) {
        return ApiResponse.success(
                "Teacher detail retrieved successfully.",
                adminTeacherManagementService.getTeacherDetail(id));
    }

    @GetMapping("/teachers/pending")
    public ApiResponse<PageResponse<AdminTeacherListItemResponse>> listPendingTeachers(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Pending teachers retrieved successfully.",
                adminTeacherManagementService.listPendingTeachers(page, size));
    }

    @PostMapping("/teachers/{id}/approve")
    public ApiResponse<AdminTeacherDetailResponse> approveTeacher(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ApproveTeacherRequest request) {
        return ApiResponse.success(
                "Teacher approved successfully.",
                adminTeacherManagementService.approveTeacher(currentUser, id, request != null ? request.note() : null));
    }

    @PostMapping("/teachers/{id}/reject")
    public ApiResponse<AdminTeacherDetailResponse> rejectTeacher(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody RejectTeacherRequest request) {
        return ApiResponse.success(
                "Teacher rejected successfully.",
                adminTeacherManagementService.rejectTeacher(currentUser, id, request.reason()));
    }

    @GetMapping("/teacher-documents/{id}")
    public ResponseEntity<Resource> getTeacherDocument(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        StoredFileDownload download = adminTeacherManagementService.getTeacherDocument(currentUser, id);
        return FileDownloadResponseBuilder.buildAttachmentDownloadResponse(download);
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserListItemResponse>> listUsers(
            @Size(max = 40, message = "Role must not exceed 40 characters.")
            @RequestParam(value = "role", required = false) String role,
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.success(
                "Users retrieved successfully.",
                adminUserManagementService.listUsers(role, keyword, status, page, size));
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<AdminUserListItemResponse> updateUserStatus(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminUserStatusUpdateRequest request) {
        return ApiResponse.success(
                "User status updated successfully.",
                adminUserManagementService.updateUserStatus(currentUser, id, request));
    }

    @GetMapping("/ai/usage-logs")
    public ApiResponse<PageResponse<AdminAiUsageLogResponse>> listAiUsageLogs(
            @Size(max = 80, message = "Feature type must not exceed 80 characters.")
            @RequestParam(value = "featureType", required = false) String featureType,
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @Size(max = 40, message = "User role must not exceed 40 characters.")
            @RequestParam(value = "userRole", required = false) String userRole,
            @RequestParam(value = "dateFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "AI usage logs retrieved successfully.",
                aiService.getUsageLogs(featureType, status, userRole, dateFrom, dateTo, page, size));
    }

    @GetMapping("/students")
    public ApiResponse<PageResponse<AdminStudentListItemResponse>> listStudents(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.success(
                "Students retrieved successfully.",
                adminUserManagementService.listStudents(page, size));
    }

    @GetMapping("/content/submissions")
    public ApiResponse<PageResponse<ContentSubmissionResponse>> listContentSubmissions(
            @Size(max = 40, message = "Content type must not exceed 40 characters.")
            @RequestParam(value = "contentType", required = false) String contentType,
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "teacherId", required = false) Long teacherId,
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.success(
                "Content submissions retrieved successfully.",
                adminContentReviewService.listSubmissions(contentType, status, teacherId, keyword, page, size));
    }

    @GetMapping("/content/submissions/{contentType}/{id}")
    public ApiResponse<ContentSubmissionDetailResponse> getContentSubmissionDetail(
            @Size(max = 40, message = "Content type must not exceed 40 characters.")
            @PathVariable String contentType,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Content submission detail retrieved successfully.",
                adminContentReviewService.getSubmissionDetail(ManagedContentType.fromValue(contentType), id));
    }

    @PatchMapping("/content/{contentType}/{id}/archive")
    public ApiResponse<ContentSubmissionDetailResponse> archiveContent(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Size(max = 40, message = "Content type must not exceed 40 characters.")
            @PathVariable String contentType,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ContentApprovalRequest request) {
        return ApiResponse.success(
                "Content archived successfully.",
                adminContentReviewService.archiveContent(
                        currentUser,
                        ManagedContentType.fromValue(contentType),
                        id,
                        request != null ? request.note() : null));
    }
}
