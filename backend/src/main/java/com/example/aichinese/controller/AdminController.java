package com.example.aichinese.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.example.aichinese.dto.dashboard.AdminDashboardResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AdminTeacherManagementService;
import com.example.aichinese.service.AdminUserManagementService;
import com.example.aichinese.service.DashboardService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final DashboardService dashboardService;
    private final AdminTeacherManagementService adminTeacherManagementService;
    private final AdminUserManagementService adminUserManagementService;

    public AdminController(
            DashboardService dashboardService,
            AdminTeacherManagementService adminTeacherManagementService,
            AdminUserManagementService adminUserManagementService) {
        this.dashboardService = dashboardService;
        this.adminTeacherManagementService = adminTeacherManagementService;
        this.adminUserManagementService = adminUserManagementService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> getDashboard(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Admin dashboard retrieved successfully.",
                dashboardService.getAdminDashboard(currentUser));
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
        Resource resource = adminTeacherManagementService.getTeacherDocument(currentUser, id);
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(resource)
                        .orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
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

    @GetMapping("/students")
    public ApiResponse<PageResponse<AdminStudentListItemResponse>> listStudents(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.success(
                "Students retrieved successfully.",
                adminUserManagementService.listStudents(page, size));
    }
}
