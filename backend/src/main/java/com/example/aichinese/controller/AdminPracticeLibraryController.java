package com.example.aichinese.controller;

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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.learn.AdminPracticeSetDetail;
import com.example.aichinese.dto.learn.AdminPracticeSetRow;
import com.example.aichinese.dto.learn.PracticeSetRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.learn.PracticeLibraryService;
import com.example.aichinese.service.learn.PracticeSetDirectory;

import lombok.RequiredArgsConstructor;

/** The admin Practice library: practice sets of every type, made by the MandarinSpark team. */
@Validated
@RestController
@RequestMapping("/api/v1/admin/practice-sets")
@RequiredArgsConstructor
public class AdminPracticeLibraryController {

    private final PracticeLibraryService practiceLibraryService;
    private final PracticeSetDirectory directory;

    @GetMapping
    public ApiResponse<PageResponse<AdminPracticeSetRow>> list(
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "q", required = false) String q,
            @Size(max = 40, message = "Practice type filter must not exceed 40 characters.")
            @RequestParam(value = "type", required = false) String type,
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.success("Practice sets retrieved successfully.", directory.adminSets(q,
                LearnParams.optionalPracticeType(type), LearnParams.optionalStatus(status), page, size));
    }

    @GetMapping("/{type}/{setId}")
    public ApiResponse<AdminPracticeSetDetail> detail(@PathVariable String type, @PathVariable Long setId) {
        return ApiResponse.success("Practice set retrieved successfully.",
                practiceLibraryService.detail(LearnParams.practiceType(type), setId));
    }

    @PostMapping("/{type}")
    public ApiResponse<AdminPracticeSetDetail> create(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @Valid @RequestBody PracticeSetRequest request) {
        return ApiResponse.success("Activity saved.",
                practiceLibraryService.create(currentUser, LearnParams.practiceType(type), request));
    }

    @PutMapping("/{type}/{setId}")
    public ApiResponse<AdminPracticeSetDetail> update(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long setId,
            @Valid @RequestBody PracticeSetRequest request) {
        return ApiResponse.success("Activity saved.",
                practiceLibraryService.update(currentUser, LearnParams.practiceType(type), setId, request));
    }

    @PostMapping("/{type}/{setId}/publish")
    public ApiResponse<AdminPracticeSetDetail> publish(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long setId) {
        return ApiResponse.success("Practice set published.",
                practiceLibraryService.publish(currentUser, LearnParams.practiceType(type), setId));
    }

    @PostMapping("/{type}/{setId}/unpublish")
    public ApiResponse<AdminPracticeSetDetail> unpublish(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long setId) {
        return ApiResponse.success("Practice set unpublished.",
                practiceLibraryService.unpublish(currentUser, LearnParams.practiceType(type), setId));
    }

    @PostMapping("/{type}/{setId}/archive")
    public ApiResponse<AdminPracticeSetDetail> archive(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long setId) {
        return ApiResponse.success("Practice set archived.",
                practiceLibraryService.archive(currentUser, LearnParams.practiceType(type), setId));
    }

    /** Restore (from the list): the set comes back as a draft. */
    @PostMapping("/{type}/{setId}/restore")
    public ApiResponse<AdminPracticeSetDetail> restore(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long setId) {
        return ApiResponse.success("Practice set restored.",
                practiceLibraryService.restore(currentUser, LearnParams.practiceType(type), setId));
    }

    /** The Undo of Archive: the set goes back to the status it had before. */
    @PostMapping("/{type}/{setId}/unarchive")
    public ApiResponse<AdminPracticeSetDetail> unarchive(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long setId) {
        return ApiResponse.success("Practice set restored.",
                practiceLibraryService.unarchive(currentUser, LearnParams.practiceType(type), setId));
    }
}
