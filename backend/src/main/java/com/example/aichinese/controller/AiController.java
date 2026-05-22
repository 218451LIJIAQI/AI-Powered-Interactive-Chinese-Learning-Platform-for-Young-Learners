package com.example.aichinese.controller;

import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiChatResponse;
import com.example.aichinese.dto.ai.AiLearningRecommendationRequest;
import com.example.aichinese.dto.ai.AiLearningRecommendationResponse;
import com.example.aichinese.dto.ai.AiPronunciationFeedbackResponse;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorResponse;
import com.example.aichinese.dto.ai.StudentAiResponseReportResponse;
import com.example.aichinese.dto.ai.AiSentencePracticeRequest;
import com.example.aichinese.dto.ai.AiSentencePracticeResponse;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorResponse;
import com.example.aichinese.dto.ai.AiVocabularyHelperRequest;
import com.example.aichinese.dto.ai.AiVocabularyHelperResponse;
import com.example.aichinese.dto.ai.AiWritingFeedbackResponse;
import com.example.aichinese.dto.ai.CreateAiResponseReportRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiResponseReportService;
import com.example.aichinese.service.AiService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Validated
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiService aiService;
    private final AiResponseReportService aiResponseReportService;

    public AiController(
            AiService aiService,
            AiResponseReportService aiResponseReportService) {
        this.aiService = aiService;
        this.aiResponseReportService = aiResponseReportService;
    }

    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<AiAvailabilityResponse>> getAvailability() {
        // GET is the semantically-correct verb for an idempotent "what is the
        // current AI provider state?" probe. Cache-Control: private, max-age=30
        // lets the browser reuse the response across the several AI pages a
        // student typically opens in a single session without holding the
        // answer stale across backend config reloads. `private` ensures the
        // body is never cached by shared proxies since the response is
        // served behind JWT auth.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePrivate())
                .body(ApiResponse.success("AI availability retrieved successfully.", aiService.getAvailability()));
    }

    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiChatRequest request) {
        return ApiResponse.success("AI chat response generated successfully.", aiService.chat(currentUser, request));
    }

    @PostMapping("/reports")
    public ApiResponse<StudentAiResponseReportResponse> reportAiResponse(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CreateAiResponseReportRequest request) {
        return ApiResponse.success(
                "AI response report submitted for review successfully.",
                aiResponseReportService.createReport(currentUser, request));
    }

    @GetMapping("/reports")
    public ApiResponse<PageResponse<StudentAiResponseReportResponse>> getMyAiResponseReports(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "AI response reports retrieved successfully.",
                aiResponseReportService.getCurrentUserReports(currentUser, page, size));
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

    @PostMapping("/pronunciation-feedback")
    public ApiResponse<AiPronunciationFeedbackResponse> pronunciationFeedback(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam("file") MultipartFile file,
            @NotBlank(message = "Target text is required.")
            @Size(max = 200, message = "Target text must not exceed 200 characters.")
            @RequestParam("targetText") String targetText,
            @Size(max = 255, message = "Pinyin must not exceed 255 characters.")
            @RequestParam(value = "pinyin", required = false) String pinyin,
            @Size(max = 20, message = "Tone must not exceed 20 characters.")
            @RequestParam(value = "tone", required = false) String tone,
            @Size(max = 20, message = "Language preference must not exceed 20 characters.")
            @RequestParam(value = "languagePreference", required = false) String languagePreference) {
        return ApiResponse.success(
                "AI pronunciation feedback processed successfully.",
                aiService.pronunciationFeedback(currentUser, file, targetText, pinyin, tone, languagePreference));
    }

    @PostMapping("/writing-feedback")
    public ApiResponse<AiWritingFeedbackResponse> writingFeedback(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam("file") MultipartFile file,
            @NotBlank(message = "Target character is required.")
            @Size(max = 100, message = "Target character must not exceed 100 characters.")
            @RequestParam("targetCharacter") String targetCharacter,
            @Size(max = 20, message = "Language preference must not exceed 20 characters.")
            @RequestParam(value = "languagePreference", required = false) String languagePreference) {
        return ApiResponse.success(
                "AI writing feedback processed successfully.",
                aiService.writingFeedback(currentUser, file, targetCharacter, languagePreference));
    }
}
