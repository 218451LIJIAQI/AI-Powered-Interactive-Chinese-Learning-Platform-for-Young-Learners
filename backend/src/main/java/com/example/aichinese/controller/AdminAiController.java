package com.example.aichinese.controller;

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
import com.example.aichinese.dto.ai.AdminAiResponseReportResponse;
import com.example.aichinese.dto.ai.AdminAiSettingsResponse;
import com.example.aichinese.dto.ai.AdminAiSettingsUpdateRequest;
import com.example.aichinese.dto.ai.AdminAiUsageLogResponse;
import com.example.aichinese.dto.ai.AdminAiUsageSummaryResponse;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.dto.ai.AiQuizGeneratorResponse;
import com.example.aichinese.dto.ai.QuizDraftRequest;
import com.example.aichinese.dto.ai.ReviewAiResponseReportRequest;
import com.example.aichinese.dto.learn.HintPolicyDto;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiLogService;
import com.example.aichinese.service.AiResponseReportService;
import com.example.aichinese.service.AiService;
import com.example.aichinese.service.ai.AiHelperStatusService;
import com.example.aichinese.service.PlatformSettingsService;
import com.example.aichinese.service.learn.HintPolicyService;
import com.example.aichinese.service.learn.LearnClock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

/**
 * The admin AI area ({@code /api/v1/admin/ai/**}, ADMIN only): reports, usage and settings, and the AI-assisted quiz
 * draft of the lesson editor.
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final AiResponseReportService aiResponseReportService;
    private final AiLogService aiLogService;
    private final PlatformSettingsService platformSettingsService;
    private final HintPolicyService hintPolicyService;
    private final AiService aiService;
    private final AiHelperStatusService helperStatus;
    private final LearnClock clock;

    @GetMapping("/quiz-draft/availability")
    public ApiResponse<AiAvailabilityResponse> quizDraftAvailability() {
        return ApiResponse.success("AI availability retrieved successfully.", helperStatus.forQuizAuthor());
    }

    /**
     * Draft questions for a lesson's quiz, to check and edit before adding them: nothing is saved. A lesson quiz has
     * exactly 5 questions, so the draft writes at most the places left (refused when the quiz is full). Usage is logged
     * as QUIZ_DRAFT and limited like every AI request.
     */
    @PostMapping("/quiz-draft")
    public ApiResponse<AiQuizGeneratorResponse> quizDraft(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody QuizDraftRequest request) {
        return ApiResponse.success(
                "AI-assisted draft generated. Review and edit it before saving; nothing has been saved.",
                aiService.quizDraft(currentUser, request));
    }

    /** The hint rules per level (AI Settings, "Hints for students"). */
    @GetMapping("/hints")
    public ApiResponse<HintPolicyDto.Response> hintPolicy() {
        return ApiResponse.success("Hint rules retrieved successfully.", hintPolicyService.policy());
    }

    @PutMapping("/hints")
    public ApiResponse<HintPolicyDto.Response> updateHintPolicy(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody HintPolicyDto.UpdateRequest request) {
        return ApiResponse.success("Hint rules saved.", hintPolicyService.update(currentUser, request));
    }

    /** Every report, open ones first. */
    @GetMapping("/reports")
    public ApiResponse<PageResponse<AdminAiResponseReportResponse>> listReports(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "AI response report queue retrieved successfully.",
                aiResponseReportService.getAdminReports(page, size));
    }

    /** Opens a report linked from a notification or the dashboard, regardless of its place in the queue. */
    @GetMapping("/reports/{id}")
    public ApiResponse<AdminAiResponseReportResponse> report(@PathVariable Long id) {
        return ApiResponse.success("AI response report retrieved successfully.", aiResponseReportService.getAdminReport(id));
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
    @CsvAttachmentResponse
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
        return FileDownloadResponseBuilder.csvAttachment("ai-usage-log-" + clock.today() + ".csv", csv);
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
