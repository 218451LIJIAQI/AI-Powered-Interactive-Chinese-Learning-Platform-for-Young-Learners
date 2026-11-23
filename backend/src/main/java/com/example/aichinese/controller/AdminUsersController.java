package com.example.aichinese.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.admin.AdminDeactivationRequest;
import com.example.aichinese.dto.admin.AdminInviteRequest;
import com.example.aichinese.dto.admin.AdminInviteResponse;
import com.example.aichinese.dto.admin.AdminReactivationRequest;
import com.example.aichinese.dto.admin.AdminUserDetailResponse;
import com.example.aichinese.dto.admin.AdminUserListItemResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.admin.AdminInviteService;
import com.example.aichinese.service.admin.AdminUserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

/** The admin Users section: learners and admins (deactivate, reactivate) and admin invites (send, cancel, undo). */
@Validated
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminUsersController {

    private final AdminUserService adminUserService;
    private final AdminInviteService adminInviteService;

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
    public ApiResponse<AdminUserDetailResponse> deactivate(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminDeactivationRequest request) {
        return ApiResponse.success(
                "User deactivated.",
                adminUserService.deactivate(currentUser, id, request.reason()));
    }

    /** Runs at once and needs no reason, so the body may be left out; a reason given is audited. */
    @PostMapping("/users/{id}/reactivate")
    public ApiResponse<AdminUserDetailResponse> reactivate(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminReactivationRequest request) {
        return ApiResponse.success(
                "User reactivated.",
                adminUserService.reactivate(currentUser, id, request == null ? null : request.reason()));
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

    /** "Cancel invite": the invite's link stops working until the cancel is undone. */
    @DeleteMapping("/admin-invites/{id}")
    public ApiResponse<Void> cancelInvite(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        adminInviteService.cancel(currentUser, id);
        return ApiResponse.success("Invite cancelled. The link no longer works.", null);
    }

    /** The Undo of "Cancel invite": the invite is pending again and the link it sent works again. */
    @PostMapping("/admin-invites/{id}/restore")
    public ApiResponse<AdminInviteResponse> restoreInvite(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success("Invite restored.", adminInviteService.restore(currentUser, id));
    }
}
