package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiService;

import jakarta.validation.Valid;

/**
 * Teacher AI drafts (FRAI05). An approved teacher can ask the quiz
 * generator for an unsaved draft that the teacher reviews, edits and then saves through the normal
 * create / submit-for-review workflow. Nothing is persisted here; usage is logged and rate-limited
 * like learner AI calls. Pending or rejected teachers get 403 from {@code ApprovedTeacherInterceptor}.
 */
@RestController
@RequestMapping("/api/v1/teachers/ai")
public class TeacherAiController {

    private static final String DRAFT_MESSAGE = "AI-assisted draft generated. Review and edit it before saving; nothing has been saved.";

    private final AiService aiService;

    public TeacherAiController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/availability")
    public ApiResponse<AiAvailabilityResponse> getAvailability() {
        return ApiResponse.success("AI availability retrieved successfully.", aiService.getAvailability());
    }

    @PostMapping("/quiz-draft")
    public ApiResponse<AiQuizGeneratorResponse> quizDraft(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiQuizGeneratorRequest request) {
        return ApiResponse.success(DRAFT_MESSAGE, aiService.teacherQuizDraft(currentUser, request));
    }
}