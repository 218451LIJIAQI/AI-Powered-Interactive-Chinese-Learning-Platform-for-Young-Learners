package com.example.aichinese.controller;

import java.util.Objects;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.example.aichinese.dto.admin.activity.AdminActivitySummaryResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.TeacherActivityService;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.Size;

/**
 * Teacher-authored practice activities (D5): an approved teacher manages only their own
 * flashcards, matching games, listening, tracing, drag-and-drop and story activities. Request
 * bodies are the same {@code Admin*Request} DTOs as the admin library; new items are drafts and
 * must be submitted for admin review before students see them.
 */
@Validated
@RestController
@RequestMapping("/api/v1/teachers/activities")
public class TeacherActivityController {

    private final TeacherActivityService teacherActivityService;

    public TeacherActivityController(TeacherActivityService teacherActivityService) {
        this.teacherActivityService = Objects.requireNonNull(teacherActivityService, "teacherActivityService");
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminActivitySummaryResponse>> listOwnActivities(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @Size(max = 60, message = "Type must not exceed 60 characters.")
            @RequestParam(value = "type", required = false) String type,
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Teacher activities retrieved successfully.",
                teacherActivityService.listOwn(currentUser, status, type, keyword, page, size));
    }

    @GetMapping("/{slug}")
    public ApiResponse<PageResponse<?>> listOwnActivitiesOfType(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Size(max = 60, message = "Activity type must not exceed 60 characters.") @PathVariable String slug,
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @Size(max = 40, message = "Sort must not exceed 40 characters.")
            @RequestParam(value = "sort", required = false) String sort) {
        return ApiResponse.success(
                "Teacher activities retrieved successfully.",
                teacherActivityService.listOwnOfType(currentUser, slug, status, page, size, sort));
    }

    @PostMapping("/{slug}")
    public ApiResponse<Object> createActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Size(max = 60, message = "Activity type must not exceed 60 characters.") @PathVariable String slug,
            @RequestBody JsonNode body) {
        return ApiResponse.success(
                "Activity draft created successfully.",
                teacherActivityService.create(currentUser, slug, body));
    }

    @GetMapping("/{slug}/{id}")
    public ApiResponse<Object> getActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Size(max = 60, message = "Activity type must not exceed 60 characters.") @PathVariable String slug,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Activity retrieved successfully.",
                teacherActivityService.get(currentUser, slug, id));
    }

    @PutMapping("/{slug}/{id}")
    public ApiResponse<Object> updateActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Size(max = 60, message = "Activity type must not exceed 60 characters.") @PathVariable String slug,
            @PathVariable Long id,
            @RequestBody JsonNode body) {
        return ApiResponse.success(
                "Activity updated successfully.",
                teacherActivityService.update(currentUser, slug, id, body));
    }

    @DeleteMapping("/{slug}/{id}")
    public ApiResponse<Void> deleteDraftActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Size(max = 60, message = "Activity type must not exceed 60 characters.") @PathVariable String slug,
            @PathVariable Long id) {
        teacherActivityService.deleteDraft(currentUser, slug, id);
        return ApiResponse.success("Activity draft deleted successfully.", null);
    }

    @PostMapping("/{slug}/{id}/submit")
    public ApiResponse<Object> submitActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Size(max = 60, message = "Activity type must not exceed 60 characters.") @PathVariable String slug,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Submitted for admin review.",
                teacherActivityService.submit(currentUser, slug, id));
    }
}