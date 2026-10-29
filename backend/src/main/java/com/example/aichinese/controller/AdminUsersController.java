package com.example.aichinese.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
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
import com.example.aichinese.dto.admin.AdminDeactivationResponse;
import com.example.aichinese.dto.admin.AdminInviteRequest;
import com.example.aichinese.dto.admin.AdminInviteResponse;
import com.example.aichinese.dto.admin.AdminTeacherApplicationResponse;
import com.example.aichinese.dto.admin.AdminTeacherApplicationSummaryResponse;
import com.example.aichinese.dto.admin.AdminUserDetailResponse;
import com.example.aichinese.dto.admin.AdminUserListItemResponse;
import com.example.aichinese.dto.admin.AdminUserStatusRequest;
import com.example.aichinese.dto.admin.ApproveTeacherRequest;
import com.example.aichinese.dto.review.RejectionReasonRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.admin.AdminInviteService;
import com.example.aichinese.service.admin.AdminUserService;
import com.example.aichinese.service.admin.TeacherApplicationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** The admin Users section: all users (deactivate, reactivate, invite admin) and the teacher applications tab. */
@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminUsersController {

    private static final int APPLICATION_QUEUE_LIMIT = 100;

    private final AdminUserService adminUserService;
    private final AdminInviteService adminInviteService;
    private final TeacherApplicationService teacherApplicationService;

    public AdminUsersController(
            AdminUserService adminUserService,
            AdminInviteService adminInviteService,
            TeacherApplicationService teacherApplicationService) {
        this.adminUserService = adminUserService;
        this.adminInviteService = adminInviteService;
        this.teacherApplicationService = teacherApplicationService;
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserListItemResponse>> listUsers(
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Size(max = 40, message = "Role must not exceed 40 characters.")
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.success(
                "Users retrieved successfully.",
                adminUserService.list(keyword, role, active, page, size));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<AdminUserDetailResponse> getUser(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success("User retrieved successfully.", adminUserService.get(currentUser, id));
    }

    @PostMapping("/users/{id}/deactivate")
    public ApiResponse<AdminDeactivationResponse> deactivate(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminUserStatusRequest request) {
        return ApiResponse.success(
                "User deactivated.",
                adminUserService.deactivate(currentUser, id, request.reason()));
    }

    @PostMapping("/users/{id}/reactivate")
    public ApiResponse<AdminUserDetailResponse> reactivate(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminUserStatusRequest request) {
        return ApiResponse.success(
                "User reactivated.",
                adminUserService.reactivate(currentUser, id, request.reason()));
    }

    @GetMapping("/admin-invites")
    public ApiResponse<List<AdminInviteResponse>> pendingInvites() {
        return ApiResponse.success("Pending admin invites retrieved successfully.", adminInviteService.pending());
    }

    @PostMapping("/admin-invites")
    public ApiResponse<AdminInviteResponse> invite(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AdminInviteRequest request) {
        return ApiResponse.success("Invite sent.", adminInviteService.invite(currentUser, request.email()));
    }

    @GetMapping("/teacher-applications")
    public ApiResponse<List<AdminTeacherApplicationSummaryResponse>> teacherApplications() {
        return ApiResponse.success(
                "Teacher applications retrieved successfully.",
                teacherApplicationService.waiting(APPLICATION_QUEUE_LIMIT));
    }

    @GetMapping("/teacher-applications/{teacherId}")
    public ApiResponse<AdminTeacherApplicationResponse> teacherApplication(@PathVariable Long teacherId) {
        return ApiResponse.success(
                "Teacher application retrieved successfully.",
                teacherApplicationService.get(teacherId));
    }

    @PostMapping("/teachers/{id}/approve")
    public ApiResponse<AdminTeacherApplicationResponse> approveTeacher(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ApproveTeacherRequest request) {
        return ApiResponse.success(
                "Teacher approved successfully.",
                teacherApplicationService.approve(currentUser, id, request == null ? null : request.note()));
    }

    /** "Request changes": the teacher sees the reason and can resubmit (an approved teacher is locked again). */
    @PostMapping("/teachers/{id}/reject")
    public ApiResponse<AdminTeacherApplicationResponse> requestTeacherChanges(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody RejectionReasonRequest request) {
        return ApiResponse.success(
                "Changes requested.",
                teacherApplicationService.requestChanges(currentUser, id, request.reason()));
    }

    @GetMapping("/teacher-documents/{id}")
    public ResponseEntity<Resource> teacherDocument(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return FileDownloadResponseBuilder.buildAttachmentDownloadResponse(
                teacherApplicationService.getDocument(currentUser, id));
    }
}
