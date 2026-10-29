package com.example.aichinese.controller;

import java.time.LocalDate;

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

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.ai.AdminAiReportCountsResponse;
import com.example.aichinese.dto.ai.AdminAiResponseReportResponse;
import com.example.aichinese.dto.ai.AdminAiSettingsResponse;
import com.example.aichinese.dto.ai.AdminAiSettingsUpdateRequest;
import com.example.aichinese.dto.ai.AdminAiUsageLogResponse;
import com.example.aichinese.dto.ai.AdminAiUsageSummaryResponse;
import com.example.aichinese.dto.ai.ReviewAiResponseReportRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiLogService;
import com.example.aichinese.service.AiResponseReportService;
import com.example.aichinese.service.PlatformSettingsService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** The admin AI area ({@code /api/v1/admin/ai/**}, ADMIN only): reports, usage and settings. */
@Validated
@RestController
@RequestMapping("/api/v1/admin/ai")
public class AdminAiController {

    private final AiResponseReportService aiResponseReportService;
    private final AiLogService aiLogService;
    private final PlatformSettingsService platformSettingsService;

    public AdminAiController(
            AiResponseReportService aiResponseReportService,
            AiLogService aiLogService,
            PlatformSettingsService platformSettingsService) {
        this.aiResponseReportService = aiResponseReportService;
        this.aiLogService = aiLogService;
        this.platformSettingsService = platformSettingsService;
    }

    /** Reports, open first; {@code status} = OPEN, CLOSED, RESOLVED, DISMISSED or ALL (default). */
    @GetMapping("/reports")
    public ApiResponse<PageResponse<AdminAiResponseReportResponse>> listReports(
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "AI response report queue retrieved successfully.",
                aiResponseReportService.getAdminReports(status, page, size));
    }

    @GetMapping("/reports/counts")
    public ApiResponse<AdminAiReportCountsResponse> reportCounts() {
        return ApiResponse.success("AI report counts retrieved successfully.", aiResponseReportService.getReportCounts());
    }

    /** Resolves (RESOLVED) or dismisses (DISMISSED) an open report with a note to the student. */
    @PostMapping("/reports/{id}/review")
    public ApiResponse<AdminAiResponseReportResponse> reviewReport(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody ReviewAiResponseReportRequest request) {
        return ApiResponse.success(
                "AI response report reviewed successfully.",
                aiResponseReportService.reviewReport(currentUser, id, request));
    }

    @GetMapping("/usage/summary")
    public ApiResponse<AdminAiUsageSummaryResponse> usageSummary() {
        return ApiResponse.success("AI usage summary retrieved successfully.", aiLogService.getUsageSummary());
    }

    @GetMapping("/usage-logs")
    public ApiResponse<PageResponse<AdminAiUsageLogResponse>> listUsageLogs(
            @Size(max = 80, message = "Feature type must not exceed 80 characters.")
            @RequestParam(value = "featureType", required = false) String featureType,
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @Size(max = 40, message = "User role must not exceed 40 characters.")
            @RequestParam(value = "userRole", required = false) String userRole,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "AI usage logs retrieved successfully.",
                aiLogService.getUsageLogs(
                        AiLogService.UsageFilter.parse(featureType, status, userRole), page, size));
    }

    /** The filtered log as a CSV file (newest first, at most AiLogService.MAX_EXPORT_ROWS rows); audited. */
    @GetMapping(value = "/usage-logs/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportUsageLogs(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Size(max = 80, message = "Feature type must not exceed 80 characters.")
            @RequestParam(value = "featureType", required = false) String featureType,
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @Size(max = 40, message = "User role must not exceed 40 characters.")
            @RequestParam(value = "userRole", required = false) String userRole) {
        String csv = aiLogService.exportUsageLogsCsv(currentUser, AiLogService.UsageFilter.parse(featureType, status, userRole));
        return FileDownloadResponseBuilder.csvAttachment("ai-usage-log-" + LocalDate.now() + ".csv", csv);
    }

    @GetMapping("/settings")
    public ApiResponse<AdminAiSettingsResponse> settings() {
        return ApiResponse.success("AI settings retrieved successfully.", platformSettingsService.getAiSettings());
    }

    @PutMapping("/settings")
    public ApiResponse<AdminAiSettingsResponse> updateSettings(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AdminAiSettingsUpdateRequest request) {
        return ApiResponse.success("AI settings updated successfully.",
                platformSettingsService.updateAiSettings(currentUser, request));
    }
}
