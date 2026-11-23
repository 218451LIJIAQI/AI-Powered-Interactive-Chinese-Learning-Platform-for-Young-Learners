package com.example.aichinese.controller;

import java.util.List;

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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.ai.AiChatRequest;
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
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiPracticeCheckService;
import com.example.aichinese.service.AiResponseReportService;
import com.example.aichinese.service.AiService;
import com.example.aichinese.service.XiaomeiService;
import com.example.aichinese.service.ai.AiChatStreamRunner;
import com.example.aichinese.service.ai.AiHelperStatusService;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

/**
 * The student AI Helper ({@code /api/v1/ai/**}, STUDENT only): the status line, Ask SparkBot, Talk with Xiaomei (what she
 * remembers is in {@link XiaomeiMemoryController}), Quick quiz, Story, pronunciation and handwriting checks, and the
 * student's reports. Nothing here gives XP.
 */
@Validated
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final AiChatStreamRunner chatStreamRunner;
    private final AiHelperStatusService helperStatus;
    private final XiaomeiService xiaomeiService;
    private final AiPracticeCheckService practiceCheckService;
    private final AiResponseReportService aiResponseReportService;

    @GetMapping("/status")
    public ApiResponse<AiHelperStatusResponse> status(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("AI Helper status retrieved successfully.", helperStatus.forStudent(currentUser.getUserId()));
    }

    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = """
                    UTF-8 server-sent events. Each `delta` carries the next piece of answer text, including any spaces.
                    A `complete` event carries the full [AiChatResponse](#/components/schemas/AiChatResponse) as JSON.
                    An `error` event carries a plain-text failure message. A timeout or disconnect can end the stream
                    before a terminal event arrives.
                    """,
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(implementation = String.class),
                    examples = {
                            @ExampleObject(name = "Completed", value = """
                                    event:delta
                                    data:你好

                                    event:complete
                                    data:{"sessionId":7,"chatMessageId":9,"aiResponse":"你好","status":"SUCCESS"}

                                    """),
                            @ExampleObject(name = "Failed", value = """
                                    event:error
                                    data:AI chat stream failed. Please try again.

                                    """)
                    }))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "HTTP errors returned before streaming use the API's JSON error envelope.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(ref = "#/components/schemas/ApiResponseVoid")))
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AiChatRequest request) {
        // Validate account, message and session synchronously so 400/401/404 return JSON before SSE starts.
        AiService.PreparedChat prepared = aiService.prepareChat(currentUser, request);
        return chatStreamRunner.stream(currentUser, prepared);
    }

    @PostMapping("/conversation/reply")
    public ApiResponse<XiaomeiResponses.Reply> conversationReply(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody XiaomeiReplyRequest request) {
        return ApiResponse.success("Xiaomei replied.", xiaomeiService.reply(currentUser, request));
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
            @Size(max = 20, message = "Language preference must not exceed 20 characters.")
            @RequestParam(value = "languagePreference", required = false) String languagePreference) {
        return ApiResponse.success("Practice check processed.", practiceCheckService.check(currentUser,
                new AiPracticeCheckService.CheckRequest(type, file, targetText, lessonId, languagePreference)));
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

    /** Deletes the check at once; {@code POST .../restore} is its Undo for a while (see AiPracticeCheckService). */
    @DeleteMapping("/practice-checks/{id}")
    public ApiResponse<Void> deletePracticeCheck(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        practiceCheckService.delete(currentUser, id);
        return ApiResponse.success("Practice check deleted.", null);
    }

    /** The Undo of Delete: the check is back in Recent checks. */
    @PostMapping("/practice-checks/{id}/restore")
    public ApiResponse<PracticeCheckResponses.Check> restorePracticeCheck(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success("Practice check restored.", practiceCheckService.restore(currentUser, id));
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
}
