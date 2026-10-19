package com.example.aichinese.controller;

import java.util.Objects;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorResponse;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

/**
 * Teacher AI drafts (D6, FR-AIGEN-06/09/10). An approved teacher can ask the quiz or story
 * generator for an unsaved draft that the teacher reviews, edits and then saves through the normal
 * create / submit-for-review workflow. Nothing is persisted here; usage is logged and rate-limited
 * like learner AI calls. Pending or rejected teachers get 403 from {@code ApprovedTeacherInterceptor}.
 */
@Validated
@RestController
@RequestMapping("/api/v1/teachers/ai")
public class TeacherAiController {

    private static final String DRAFT_MESSAGE = "AI-assisted draft generated. Review and edit it before saving; nothing has been saved.";

    private final AiService aiService;

    public TeacherAiController(AiService aiService) {
        this.aiService = Objects.requireNonNull(aiService, "aiService");
    }

    @GetMapping("/availability")
    @Operation(summary = "AI availability for teacher drafts")
    public ApiResponse<AiAvailabilityResponse> getAvailability() {
        return ApiResponse.success("AI availability retrieved successfully.", aiService.getAvailability());
    }

    @PostMapping("/quiz-draft")
    @Operation(summary = "Generate an unsaved AI quiz draft", description = "Returns quiz questions for the teacher to edit; nothing is saved.")
    public ApiResponse<AiQuizGeneratorResponse> quizDraft(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiQuizGeneratorRequest request) {
        return ApiResponse.success(DRAFT_MESSAGE, aiService.teacherQuizDraft(currentUser, request));
    }

    @PostMapping("/story-draft")
    @Operation(summary = "Generate an unsaved AI story draft", description = "Returns a short story for the teacher to edit; nothing is saved.")
    public ApiResponse<AiStoryGeneratorResponse> storyDraft(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiStoryGeneratorRequest request) {
        return ApiResponse.success(DRAFT_MESSAGE, aiService.teacherStoryDraft(currentUser, request));
    }
}