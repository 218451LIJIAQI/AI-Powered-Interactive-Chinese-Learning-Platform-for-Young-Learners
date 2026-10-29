package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.learn.AdminPracticeSetDetail;
import com.example.aichinese.dto.learn.AdminPracticeSetRow;
import com.example.aichinese.dto.learn.PracticeSetRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.learn.PracticeLibraryService;

/** The admin Practice library: practice sets of every type, made by the MandarinSpark team. */
@RestController
@RequestMapping("/api/v1/admin/practice-sets")
public class AdminPracticeLibraryController {

    private final PracticeLibraryService practiceLibraryService;

    public AdminPracticeLibraryController(PracticeLibraryService practiceLibraryService) {
        this.practiceLibraryService = practiceLibraryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminPracticeSetRow>> list(
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "stage", required = false) String stage,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        PracticeLibraryService.Filter filter = new PracticeLibraryService.Filter(
                keyword,
                LearnParams.optionalPracticeType(type),
                LearnParams.optionalStatus(status),
                LearnParams.optionalStage(stage));
        return ApiResponse.success("Practice sets retrieved successfully.", practiceLibraryService.list(filter, page, size));
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

    @PostMapping("/{type}/{setId}/restore")
    public ApiResponse<AdminPracticeSetDetail> restore(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long setId) {
        return ApiResponse.success("Practice set restored.",
                practiceLibraryService.restore(currentUser, LearnParams.practiceType(type), setId));
    }
}
