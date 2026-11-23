package com.example.aichinese.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.audit.AuditEntryResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.admin.AuditLogQueryService;
import com.example.aichinese.service.admin.AuditLogQueryService.AuditFilter;
import com.example.aichinese.service.learn.LearnClock;

import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

/** The admin audit log (FRADM08): search, one entry, the actions in use and a CSV export. Entries are never edited here. */
@Validated
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AuditLogQueryService auditLogQueryService;
    private final LearnClock clock;

    /** {@code userId}: only the entries by or about that account (the Users page's "View all"). */
    @GetMapping
    public ApiResponse<PageResponse<AuditEntryResponse>> search(
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Size(max = 80, message = "Action type must not exceed 80 characters.")
            @RequestParam(value = "actionType", required = false) String actionType,
            @Size(max = 200, message = "Actor must not exceed 200 characters.")
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Audit logs retrieved successfully.",
                auditLogQueryService.search(new AuditFilter(keyword, actionType, actor, from, to, userId), page, size));
    }

    @GetMapping("/actions")
    public ApiResponse<List<String>> actionTypes() {
        return ApiResponse.success("Audit actions retrieved successfully.", auditLogQueryService.actionTypes());
    }

    @GetMapping("/{id}")
    public ApiResponse<AuditEntryResponse> get(@PathVariable Long id) {
        return ApiResponse.success("Audit entry retrieved successfully.", auditLogQueryService.get(id));
    }

    /** Every entry matching the same filters (newest first, up to 5000) as a CSV download; audited. */
    @CsvAttachmentResponse
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Size(max = 80, message = "Action type must not exceed 80 characters.")
            @RequestParam(value = "actionType", required = false) String actionType,
            @Size(max = 200, message = "Actor must not exceed 200 characters.")
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "userId", required = false) Long userId) {
        String csv = auditLogQueryService.exportCsv(
                currentUser, new AuditFilter(keyword, actionType, actor, from, to, userId));
        return FileDownloadResponseBuilder.csvAttachment("audit-log-" + clock.today() + ".csv", csv);
    }
}
