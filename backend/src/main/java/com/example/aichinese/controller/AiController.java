package com.example.aichinese.controller;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiChatResponse;
import com.example.aichinese.dto.ai.AiPronunciationFeedbackResponse;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorResponse;
import com.example.aichinese.dto.ai.AiSentencePracticeRequest;
import com.example.aichinese.dto.ai.AiSentencePracticeResponse;
import com.example.aichinese.dto.ai.StudentAiResponseReportResponse;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorResponse;
import com.example.aichinese.dto.ai.AiVocabularyHelperRequest;
import com.example.aichinese.dto.ai.AiVocabularyHelperResponse;
import com.example.aichinese.dto.ai.AiWritingFeedbackResponse;
import com.example.aichinese.dto.ai.CreateAiResponseReportRequest;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiResponseReportService;
import com.example.aichinese.service.AiService;
import com.example.aichinese.util.RequestIdHolder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Validated
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private static final String SSE_DELTA_EVENT = "delta";
    private static final String SSE_COMPLETE_EVENT = "complete";
    private static final String SSE_ERROR_EVENT = "error";
    private static final String STREAM_FAILED_MESSAGE = "AI chat stream failed. Please try again.";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final long CHAT_STREAM_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(45);
    private static final int STREAM_CHUNK_SIZE = 24;

    private final AiService aiService;
    private final AiResponseReportService aiResponseReportService;

    public AiController(
            AiService aiService,
            AiResponseReportService aiResponseReportService) {
        this.aiService = Objects.requireNonNull(aiService, "aiService");
        this.aiResponseReportService = Objects.requireNonNull(aiResponseReportService, "aiResponseReportService");
    }

    @GetMapping("/availability")
    @Operation(summary = "Get AI provider availability", description = "Returns current text, audio, and image capability flags for the authenticated student.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "AI availability returned."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication is required."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Only students can use learner AI endpoints.")
    })
    public ResponseEntity<ApiResponse<AiAvailabilityResponse>> getAvailability() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePrivate())
                .body(ApiResponse.success("AI availability retrieved successfully.", aiService.getAvailability()));
    }

    @PostMapping("/chat")
    @Operation(summary = "Send an AI chat message", description = "Processes one SparkBot chat turn and returns the persisted student/assistant exchange.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chat turn processed."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid chat request."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication is required."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Only students can use learner AI endpoints."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "AI usage quota exceeded.")
    })
    public ApiResponse<AiChatResponse> chat(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiChatRequest request) {
        return ApiResponse.success("AI chat request processed.", aiService.chat(currentUser, request));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream an AI chat response", description = "Returns Server-Sent Events for the SparkBot answer, ending with the complete persisted chat response.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "SSE chat stream opened."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid chat request."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication is required."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Only students can use learner AI endpoints."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "AI usage quota exceeded.")
    })
    public SseEmitter chatStream(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(CHAT_STREAM_TIMEOUT_MS);
        String requestId = RequestIdHolder.get();
        Thread worker = Thread.ofVirtual().unstarted(() -> {
            bindRequestContext(requestId);
            try {
                AiChatResponse response = aiService.chat(currentUser, request);
                sendChunkedText(emitter, response.aiResponse());
                emitter.send(SseEmitter.event()
                        .name(SSE_COMPLETE_EVENT)
                        .data(response, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception exception) {
                log.warn("AI chat stream failed for user id {} with exception type {}.",
                        currentUser == null ? null : currentUser.getUserId(),
                        exception.getClass().getSimpleName());
                completeWithSafeError(emitter, safeStreamErrorMessage(exception));
            } finally {
                clearRequestContext();
            }
        });
        emitter.onCompletion(worker::interrupt);
        emitter.onError(ignored -> worker.interrupt());
        emitter.onTimeout(() -> {
            worker.interrupt();
            emitter.complete();
        });
        worker.start();
        return emitter;
    }

    @PostMapping("/reports")
    @Operation(summary = "Report an AI response", description = "Submits a learner report for admin review when SparkBot content looks wrong or unsafe.")
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
    @Operation(summary = "Generate vocabulary help", description = "Explains a Chinese word with pinyin, translations, examples, and encouragement.")
    public ApiResponse<AiVocabularyHelperResponse> vocabularyHelper(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiVocabularyHelperRequest request) {
        return ApiResponse.success(
                "AI vocabulary helper request processed.",
                aiService.vocabularyHelper(currentUser, request));
    }

    @PostMapping("/sentence-practice")
    @Operation(summary = "Review a practice sentence", description = "Checks a learner sentence and returns beginner-friendly improvement feedback.")
    public ApiResponse<AiSentencePracticeResponse> sentencePractice(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiSentencePracticeRequest request) {
        return ApiResponse.success(
                "AI sentence practice request processed.",
                aiService.sentencePractice(currentUser, request));
    }

    @PostMapping("/quiz-generator")
    @Operation(summary = "Generate a self-practice quiz", description = "Creates a short learner-only quiz draft from a topic.")
    public ApiResponse<AiQuizGeneratorResponse> quizGenerator(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiQuizGeneratorRequest request) {
        return ApiResponse.success(
                "AI quiz draft request processed.",
                aiService.quizGenerator(currentUser, request));
    }

    @PostMapping("/story-generator")
    @Operation(summary = "Generate a practice story", description = "Creates a short Mandarin story draft for self-practice.")
    public ApiResponse<AiStoryGeneratorResponse> storyGenerator(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiStoryGeneratorRequest request) {
        return ApiResponse.success(
                "AI story draft request processed.",
                aiService.storyGenerator(currentUser, request));
    }

    @PostMapping("/pronunciation-feedback")
    @Operation(summary = "Upload pronunciation audio", description = "Stores a pronunciation recording as a practice submission for teacher review (returns submissionId); AI audio feedback is not supported by the configured model.")
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
            @RequestParam(value = "languagePreference", required = false) String languagePreference,
            @RequestParam(value = "lessonId", required = false) Long lessonId) {
        return ApiResponse.success(
                "AI pronunciation feedback request processed.",
                aiService.pronunciationFeedback(currentUser, file, targetText, pinyin, tone, languagePreference, lessonId));
    }

    @PostMapping("/writing-feedback")
    @Operation(summary = "Upload writing image", description = "Stores a handwriting image as a practice submission for teacher review (returns submissionId) and, when the configured provider accepts images, returns limited AI-assisted feedback (not a score).")
    public ApiResponse<AiWritingFeedbackResponse> writingFeedback(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam("file") MultipartFile file,
            @NotBlank(message = "Target character is required.")
            @Size(max = 100, message = "Target character must not exceed 100 characters.")
            @RequestParam("targetCharacter") String targetCharacter,
            @Size(max = 20, message = "Language preference must not exceed 20 characters.")
            @RequestParam(value = "languagePreference", required = false) String languagePreference,
            @RequestParam(value = "lessonId", required = false) Long lessonId) {
        return ApiResponse.success(
                "AI writing feedback request processed.",
                aiService.writingFeedback(currentUser, file, targetCharacter, languagePreference, lessonId));
    }

    private static void sendChunkedText(SseEmitter emitter, String text) throws IOException {
        String safeText = text == null ? "" : text;
        for (int start = 0; start < safeText.length();) {
            int end = start;
            int emittedCodePoints = 0;
            while (end < safeText.length() && emittedCodePoints < STREAM_CHUNK_SIZE) {
                end += Character.charCount(safeText.codePointAt(end));
                emittedCodePoints++;
            }
            emitter.send(SseEmitter.event()
                    .name(SSE_DELTA_EVENT)
                    .data(safeText.substring(start, end)));
            start = end;
        }
    }

    private static void bindRequestContext(String requestId) {
        if (requestId == null) {
            return;
        }
        RequestIdHolder.set(requestId);
        MDC.put(MDC_REQUEST_ID, requestId);
    }

    private static void clearRequestContext() {
        MDC.remove(MDC_REQUEST_ID);
        RequestIdHolder.clear();
    }

    /** A reached AI quota keeps its friendly 429 text (minute or daily limit); any other failure stays generic. */
    private static String safeStreamErrorMessage(Exception exception) {
        if (exception instanceof ResponseStatusException statusException
                && statusException.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return FriendlyErrorMessageResolver.resolve(statusException.getStatusCode(), statusException.getReason());
        }
        return STREAM_FAILED_MESSAGE;
    }

    private static void completeWithSafeError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name(SSE_ERROR_EVENT)
                    .data(message));
        } catch (IOException | IllegalStateException ignored) {
            // Client may have disconnected; the caller already logged a safe failure summary.
        }
        emitter.complete();
    }
}
