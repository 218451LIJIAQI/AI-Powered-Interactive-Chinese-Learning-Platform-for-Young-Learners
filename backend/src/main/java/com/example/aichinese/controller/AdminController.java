package com.example.aichinese.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
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
import com.example.aichinese.dto.admin.ApproveTeacherRequest;
import com.example.aichinese.dto.admin.RejectTeacherRequest;
import com.example.aichinese.dto.ai.AdminAiUsageLogResponse;
import com.example.aichinese.dto.analytics.AdminAnalyticsResponse;
import com.example.aichinese.dto.content.ContentApprovalRequest;
import com.example.aichinese.dto.content.ContentRejectionRequest;
import com.example.aichinese.dto.content.ContentSubmissionDetailResponse;
import com.example.aichinese.dto.content.ContentSubmissionResponse;
import com.example.aichinese.dto.content.LearningCategoryResponse;
import com.example.aichinese.dto.content.LearningLevelResponse;
import com.example.aichinese.dto.dashboard.AdminDashboardResponse;
import com.example.aichinese.dto.learning.AdminScheduleResponse;
import com.example.aichinese.dto.learning.LiveClassResponse;
import com.example.aichinese.entity.enums.ManagedContentType;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AdminContentReviewService;
import com.example.aichinese.service.AdminTeacherManagementService;
import com.example.aichinese.service.AdminUserManagementService;
import com.example.aichinese.service.AnalyticsService;
import com.example.aichinese.service.AiService;
import com.example.aichinese.service.DashboardService;
import com.example.aichinese.service.LearningCatalogService;
import com.example.aichinese.service.LiveClassService;
import com.example.aichinese.service.ScheduleService;
import com.example.aichinese.service.StoredFileDownload;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final DashboardService dashboardService;
    private final AnalyticsService analyticsService;
    private final AdminTeacherManagementService adminTeacherManagementService;
    private final AdminUserManagementService adminUserManagementService;
    private final AdminContentReviewService adminContentReviewService;
    private final LearningCatalogService learningCatalogService;
    private final AiService aiService;
    private final LiveClassService liveClassService;
    private final ScheduleService scheduleService;

    public AdminController(
            DashboardService dashboardService,
            AnalyticsService analyticsService,
            AdminTeacherManagementService adminTeacherManagementService,
            AdminUserManagementService adminUserManagementService,
            AdminContentReviewService adminContentReviewService,
            LearningCatalogService learningCatalogService,
            AiService aiService,
            LiveClassService liveClassService,
            ScheduleService scheduleService) {
        this.dashboardService = dashboardService;
        this.analyticsService = analyticsService;
        this.adminTeacherManagementService = adminTeacherManagementService;
        this.adminUserManagementService = adminUserManagementService;
        this.adminContentReviewService = adminContentReviewService;
        this.learningCatalogService = learningCatalogService;
        this.aiService = aiService;
        this.liveClassService = liveClassService;
        this.scheduleService = scheduleService;
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

    @GetMapping("/schedule")
    public ApiResponse<AdminScheduleResponse> getAdminSchedule(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Admin schedule retrieved successfully.",
                scheduleService.getAdminSchedule(currentUser));
    }

    @GetMapping("/live-classes")
    public ApiResponse<List<LiveClassResponse>> getAdminLiveClasses(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Admin live classes retrieved successfully.",
                liveClassService.getAdminLiveClasses(currentUser));
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
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
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
    public ApiResponse<List<AdminTeacherListItemResponse>> listPendingTeachers() {
        return ApiResponse.success(
                "Pending teachers retrieved successfully.",
                adminTeacherManagementService.listPendingTeachers());
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
        return buildInlineDownloadResponse(download);
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserListItemResponse>> listUsers(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.success(
                "Users retrieved successfully.",
                adminUserManagementService.listUsers(role, keyword, status, page, size));
    }

    @GetMapping("/ai/usage-logs")
    public ApiResponse<PageResponse<AdminAiUsageLogResponse>> listAiUsageLogs(
            @RequestParam(value = "featureType", required = false) String featureType,
            @RequestParam(value = "status", required = false) String status,
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

    @GetMapping("/levels")
    public ApiResponse<List<LearningLevelResponse>> listLevels() {
        return ApiResponse.success(
                "Learning levels retrieved successfully.",
                learningCatalogService.getAdminLevels());
    }

    @GetMapping("/categories")
    public ApiResponse<List<LearningCategoryResponse>> listCategories() {
        return ApiResponse.success(
                "Learning categories retrieved successfully.",
                learningCatalogService.getAdminCategories());
    }

    @GetMapping("/content/submissions")
    public ApiResponse<PageResponse<ContentSubmissionResponse>> listContentSubmissions(
            @RequestParam(value = "contentType", required = false) String contentType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "teacherId", required = false) Long teacherId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.success(
                "Content submissions retrieved successfully.",
                adminContentReviewService.listSubmissions(contentType, status, teacherId, keyword, page, size));
    }

    @GetMapping("/content/submissions/{contentType}/{id}")
    public ApiResponse<ContentSubmissionDetailResponse> getContentSubmissionDetail(
            @PathVariable String contentType,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Content submission detail retrieved successfully.",
                adminContentReviewService.getSubmissionDetail(ManagedContentType.fromValue(contentType), id));
    }

    @PostMapping("/content/{contentType}/{id}/approve")
    public ApiResponse<ContentSubmissionDetailResponse> approveContent(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String contentType,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ContentApprovalRequest request) {
        return ApiResponse.success(
                "Content approved successfully.",
                adminContentReviewService.approveContent(
                        currentUser,
                        ManagedContentType.fromValue(contentType),
                        id,
                        request != null ? request.note() : null));
    }

    @PostMapping("/content/{contentType}/{id}/reject")
    public ApiResponse<ContentSubmissionDetailResponse> rejectContent(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String contentType,
            @PathVariable Long id,
            @Valid @RequestBody ContentRejectionRequest request) {
        return ApiResponse.success(
                "Content rejected successfully.",
                adminContentReviewService.rejectContent(
                        currentUser,
                        ManagedContentType.fromValue(contentType),
                        id,
                        request.reason()));
    }

    @PostMapping("/content/{contentType}/{id}/publish")
    public ApiResponse<ContentSubmissionDetailResponse> publishContent(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String contentType,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ContentApprovalRequest request) {
        return ApiResponse.success(
                "Content published successfully.",
                adminContentReviewService.publishContent(
                        currentUser,
                        ManagedContentType.fromValue(contentType),
                        id,
                        request != null ? request.note() : null));
    }

    @PatchMapping("/content/{contentType}/{id}/archive")
    public ApiResponse<ContentSubmissionDetailResponse> archiveContent(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
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

    private ResponseEntity<Resource> buildInlineDownloadResponse(StoredFileDownload download) {
        MediaType mediaType = download.contentType().isBlank()
                ? MediaTypeFactory.getMediaType(download.resource()).orElse(MediaType.APPLICATION_OCTET_STREAM)
                : MediaType.parseMediaType(download.contentType());
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(download.originalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }
}
