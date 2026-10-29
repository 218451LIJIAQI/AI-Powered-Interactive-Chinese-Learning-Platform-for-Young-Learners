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
import com.example.aichinese.dto.learn.AdminLearningPathResponse;
import com.example.aichinese.dto.learn.LevelCheckQuestionData;
import com.example.aichinese.entity.enums.PublishStatus;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.learn.LearningPathAdminService;

/** The platform's own Learn path (units) and the level check question bank, for admins. */
@RestController
@RequestMapping("/api/v1/admin/learning-path")
public class AdminLearningPathController {

    private final LearningPathAdminService learningPathAdminService;

    public AdminLearningPathController(LearningPathAdminService learningPathAdminService) {
        this.learningPathAdminService = learningPathAdminService;
    }

    @GetMapping
    public ApiResponse<AdminLearningPathResponse> overview() {
        return ApiResponse.success("Learning path retrieved successfully.", learningPathAdminService.overview());
    }

    @GetMapping("/units/{unitId}")
    public ApiResponse<AdminLearningPathResponse.UnitDetail> unit(@PathVariable Long unitId) {
        return ApiResponse.success("Unit retrieved successfully.", learningPathAdminService.unit(unitId));
    }

    @PostMapping("/units/{unitId}/publish")
    public ApiResponse<AdminLearningPathResponse.UnitDetail> publishUnit(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long unitId) {
        return ApiResponse.success("Unit published.",
                learningPathAdminService.setUnitStatus(currentUser, unitId, PublishStatus.PUBLISHED));
    }

    @PostMapping("/units/{unitId}/unpublish")
    public ApiResponse<AdminLearningPathResponse.UnitDetail> unpublishUnit(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long unitId) {
        return ApiResponse.success("Unit unpublished.",
                learningPathAdminService.setUnitStatus(currentUser, unitId, PublishStatus.DRAFT));
    }

    @GetMapping("/level-check-questions")
    public ApiResponse<PageResponse<LevelCheckQuestionData.Row>> questions(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success("Level check questions retrieved successfully.",
                learningPathAdminService.questions(page, size));
    }

    @GetMapping("/level-check-questions/{questionId}")
    public ApiResponse<LevelCheckQuestionData.Detail> question(@PathVariable Long questionId) {
        return ApiResponse.success("Level check question retrieved successfully.",
                learningPathAdminService.question(questionId));
    }

    @PostMapping("/level-check-questions")
    public ApiResponse<LevelCheckQuestionData.Detail> createQuestion(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody LevelCheckQuestionData.Request request) {
        return ApiResponse.success("Question saved.", learningPathAdminService.createQuestion(currentUser, request));
    }

    @PutMapping("/level-check-questions/{questionId}")
    public ApiResponse<LevelCheckQuestionData.Detail> updateQuestion(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long questionId,
            @Valid @RequestBody LevelCheckQuestionData.Request request) {
        return ApiResponse.success("Question saved.",
                learningPathAdminService.updateQuestion(currentUser, questionId, request));
    }
}
