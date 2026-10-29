package com.example.aichinese.controller;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiChatResponse;
import com.example.aichinese.dto.ai.AiHelperStatusResponse;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorResponse;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorResponse;
import com.example.aichinese.dto.ai.CreateAiResponseReportRequest;
import com.example.aichinese.dto.ai.PracticeCheckResponses;
import com.example.aichinese.dto.ai.StudentAiResponseReportResponse;
import com.example.aichinese.dto.ai.XiaomeiConversationRequest;
import com.example.aichinese.dto.ai.XiaomeiReplyRequest;
import com.example.aichinese.dto.ai.XiaomeiResponses;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiPracticeCheckService;
import com.example.aichinese.service.AiResponseReportService;
import com.example.aichinese.service.AiService;
import com.example.aichinese.service.XiaomeiService;
import com.example.aichinese.util.RequestIdHolder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The student AI Helper ({@code /api/v1/ai/**}, STUDENT only): the status line, Ask SparkBot, Talk with Xiaomei,
 * Quick quiz, Story, pronunciation and handwriting checks, and the student's reports. Nothing here gives XP.
 */
@Validated
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private static final String SSE_DELTA_EVENT = "delta";
    private static final String SSE_COMPLETE_EVENT = "complete";
    private static final String SSE_ERROR_EVENT = "error";
    private static final String STREAM_FAILED_MESSAGE = "AI chat stream failed. Please try again.";
    private static final long CHAT_STREAM_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(45);
    private static final int STREAM_CHUNK_SIZE = 24;

    private final AiService aiService;
    private final XiaomeiService xiaomeiService;
    private final AiPracticeCheckService practiceCheckService;
    private final AiResponseReportService aiResponseReportService;

    public AiController(
            AiService aiService,
            XiaomeiService xiaomeiService,
            AiPracticeCheckService practiceCheckService,
            AiResponseReportService aiResponseReportService) {
        this.aiService = aiService;
        this.xiaomeiService = xiaomeiService;
        this.practiceCheckService = practiceCheckService;
        this.aiResponseReportService = aiResponseReportService;
    }

    /** Availability, today's request count against the daily limit, and the AI notice text. */
    @GetMapping("/status")
    public ApiResponse<AiHelperStatusResponse> status(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("AI Helper status retrieved successfully.", aiService.status(currentUser));
    }

    /**
     * Ask SparkBot: the answer arrives as {@code delta} events, then one {@code complete} event carrying the whole
     * turn, or one {@code error} event with a safe message.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(CHAT_STREAM_TIMEOUT_MS);
        String requestId = RequestIdHolder.get();
        Thread worker = Thread.ofVirtual().unstarted(() -> {
            if (requestId != null) {
                RequestIdHolder.bind(requestId);
            }
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
                RequestIdHolder.unbind();
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

    @PostMapping("/conversation/reply")
    public ApiResponse<XiaomeiResponses.Reply> conversationReply(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody XiaomeiReplyRequest request) {
        return ApiResponse.success("Xiaomei replied.", xiaomeiService.reply(currentUser, request));
    }

    @PostMapping("/conversation/suggestions")
    public ApiResponse<XiaomeiResponses.Suggestions> conversationSuggestions(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody XiaomeiConversationRequest request) {
        return ApiResponse.success("Reply ideas generated.", xiaomeiService.suggestions(currentUser, request));
    }

    @PostMapping("/conversation/summary")
    public ApiResponse<XiaomeiResponses.Summary> conversationSummary(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody XiaomeiConversationRequest request) {
        return ApiResponse.success("Conversation summary generated.", xiaomeiService.summary(currentUser, request));
    }

    @PostMapping("/quiz-generator")
    public ApiResponse<AiQuizGeneratorResponse> quizGenerator(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiQuizGeneratorRequest request) {
        return ApiResponse.success("AI quiz draft request processed.", aiService.quizGenerator(currentUser, request));
    }

    @PostMapping("/story-generator")
    public ApiResponse<AiStoryGeneratorResponse> storyGenerator(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiStoryGeneratorRequest request) {
        return ApiResponse.success("AI story draft request processed.", aiService.storyGenerator(currentUser, request));
    }

    /** Scores a recording (WEBM, WAV or MP3) or a handwriting photo (PNG or JPEG); the file itself is not kept. */
    @PostMapping(value = "/practice-checks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PracticeCheckResponses.Result> createPracticeCheck(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @NotBlank(message = "Check type is required.")
            @Size(max = 20, message = "Check type must not exceed 20 characters.")
            @RequestParam("type") String type,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @NotBlank(message = "Target text is required.")
            @Size(max = 100, message = "Target text must not exceed 100 characters.")
            @RequestParam("targetText") String targetText,
            @RequestParam(value = "lessonId", required = false) Long lessonId,
            @Min(value = 1, message = "Recording length must be at least 1 second.")
            @Max(value = 600, message = "Recording length must not exceed 600 seconds.")
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds,
            @Size(max = 20, message = "Language preference must not exceed 20 characters.")
            @RequestParam(value = "languagePreference", required = false) String languagePreference) {
        return ApiResponse.success("Practice check processed.", practiceCheckService.check(currentUser,
                new AiPracticeCheckService.CheckRequest(type, file, targetText, lessonId, durationSeconds, languagePreference)));
    }

    @GetMapping("/practice-checks")
    public ApiResponse<PageResponse<PracticeCheckResponses.Check>> listPracticeChecks(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.success("Practice checks retrieved successfully.",
                practiceCheckService.list(currentUser, page, size));
    }

    @GetMapping("/practice-checks/lessons")
    public ApiResponse<List<PracticeCheckResponses.Lesson>> practiceCheckLessons(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Lessons retrieved successfully.", practiceCheckService.linkableLessons(currentUser));
    }

    @DeleteMapping("/practice-checks/{id}")
    public ApiResponse<Void> deletePracticeCheck(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        practiceCheckService.delete(currentUser, id);
        return ApiResponse.success("Practice check deleted.", null);
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

    private static void sendChunkedText(SseEmitter emitter, String text) throws IOException {
        for (int start = 0; start < text.length();) {
            int end = start;
            int emittedCodePoints = 0;
            while (end < text.length() && emittedCodePoints < STREAM_CHUNK_SIZE) {
                end += Character.charCount(text.codePointAt(end));
                emittedCodePoints++;
            }
            emitter.send(SseEmitter.event()
                    .name(SSE_DELTA_EVENT)
                    .data(text.substring(start, end)));
            start = end;
        }
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
