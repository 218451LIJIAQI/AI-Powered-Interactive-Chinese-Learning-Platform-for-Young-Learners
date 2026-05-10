package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiChatResponse;
import com.example.aichinese.dto.ai.AiLearningRecommendationRequest;
import com.example.aichinese.dto.ai.AiLearningRecommendationResponse;
import com.example.aichinese.dto.ai.AiProgressInsightRequest;
import com.example.aichinese.dto.ai.AiProgressInsightResponse;
import com.example.aichinese.dto.ai.AiPronunciationFeedbackResponse;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorResponse;
import com.example.aichinese.dto.ai.AiSentencePracticeRequest;
import com.example.aichinese.dto.ai.AiSentencePracticeResponse;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorResponse;
import com.example.aichinese.dto.ai.AiTeacherAssistantRequest;
import com.example.aichinese.dto.ai.AiTeacherAssistantResponse;
import com.example.aichinese.dto.ai.AiVocabularyHelperRequest;
import com.example.aichinese.dto.ai.AiVocabularyHelperResponse;
import com.example.aichinese.dto.ai.AiWritingFeedbackResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/availability")
    public ApiResponse<AiAvailabilityResponse> getAvailability() {
        return ApiResponse.success("AI availability retrieved successfully.", aiService.getAvailability());
    }

    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiChatRequest request) {
        return ApiResponse.success("AI chat response generated successfully.", aiService.chat(currentUser, request));
    }

    @PostMapping("/vocabulary-helper")
    public ApiResponse<AiVocabularyHelperResponse> vocabularyHelper(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiVocabularyHelperRequest request) {
        return ApiResponse.success(
                "AI vocabulary helper response generated successfully.",
                aiService.vocabularyHelper(currentUser, request));
    }

    @PostMapping("/sentence-practice")
    public ApiResponse<AiSentencePracticeResponse> sentencePractice(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiSentencePracticeRequest request) {
        return ApiResponse.success(
                "AI sentence practice response generated successfully.",
                aiService.sentencePractice(currentUser, request));
    }

    @PostMapping("/quiz-generator")
    public ApiResponse<AiQuizGeneratorResponse> quizGenerator(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiQuizGeneratorRequest request) {
        return ApiResponse.success(
                "AI quiz draft generated successfully.",
                aiService.quizGenerator(currentUser, request));
    }

    @PostMapping("/story-generator")
    public ApiResponse<AiStoryGeneratorResponse> storyGenerator(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiStoryGeneratorRequest request) {
        return ApiResponse.success(
                "AI story draft generated successfully.",
                aiService.storyGenerator(currentUser, request));
    }

    @PostMapping("/learning-recommendation")
    public ApiResponse<AiLearningRecommendationResponse> learningRecommendation(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiLearningRecommendationRequest request) {
        return ApiResponse.success(
                "AI learning recommendation generated successfully.",
                aiService.learningRecommendation(currentUser, request));
    }

    @PostMapping("/progress-insight")
    public ApiResponse<AiProgressInsightResponse> progressInsight(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiProgressInsightRequest request) {
        return ApiResponse.success(
                "AI progress insight generated successfully.",
                aiService.progressInsight(currentUser, request));
    }

    @PostMapping("/teacher-assistant")
    public ApiResponse<AiTeacherAssistantResponse> teacherAssistant(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiTeacherAssistantRequest request) {
        return ApiResponse.success(
                "AI teacher assistant draft generated successfully.",
                aiService.teacherAssistant(currentUser, request));
    }

    @PostMapping("/pronunciation-feedback")
    public ApiResponse<AiPronunciationFeedbackResponse> pronunciationFeedback(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam("file") MultipartFile file,
            @RequestParam("targetText") String targetText,
            @RequestParam(value = "pinyin", required = false) String pinyin,
            @RequestParam(value = "tone", required = false) String tone,
            @RequestParam(value = "languagePreference", required = false) String languagePreference) {
        return ApiResponse.success(
                "AI pronunciation feedback processed successfully.",
                aiService.pronunciationFeedback(currentUser, file, targetText, pinyin, tone, languagePreference));
    }

    @PostMapping("/writing-feedback")
    public ApiResponse<AiWritingFeedbackResponse> writingFeedback(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam("file") MultipartFile file,
            @RequestParam("targetCharacter") String targetCharacter,
            @RequestParam(value = "languagePreference", required = false) String languagePreference) {
        return ApiResponse.success(
                "AI writing feedback processed successfully.",
                aiService.writingFeedback(currentUser, file, targetCharacter, languagePreference));
    }
}
