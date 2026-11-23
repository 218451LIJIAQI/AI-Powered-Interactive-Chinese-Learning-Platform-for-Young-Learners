package com.example.aichinese.service.ai;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.value.PronunciationFeedback;
import com.example.aichinese.service.AiLogService;
import com.example.aichinese.service.FileStorageService.PracticeUpload;
import com.example.aichinese.util.AiInputSanitizer;

import lombok.RequiredArgsConstructor;

/**
 * The one path every AI request takes, whatever the feature: provider availability, the admin word filter and AI
 * safety check on the student's words ({@link AiSafetyGuard}), the per-user quota, the provider call, and one
 * AI-usage log row for the outcome. Text and image features also validate and screen the generated response.
 *
 * <p>Availability and request text are checked before reserving quota. The usage log stores prompt and reply lengths
 * ({@link AiLogService}).</p>
 */
@Component
@RequiredArgsConstructor
public class AiGateway {

    static final String QUOTA_EXCEEDED_ERROR = "Platform AI request quota exceeded.";
    static final String PROVIDER_FAILED_ERROR = "Provider request failed or timed out.";
    static final String EMPTY_REPLY_ERROR = "Provider response text was empty.";
    static final String INVALID_JSON_ERROR = "Provider response was not valid JSON.";
    static final String INCOMPLETE_JSON_ERROR = "Provider response was missing the fields the feature needs.";
    static final String NO_IMAGE_SUPPORT_ERROR = "Configured provider does not support image input.";
    static final String NO_SPEECH_SUPPORT_ERROR = "Tencent pronunciation assessment is disabled or not configured.";

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    private final AiAvailabilityService availabilityService;
    private final AiSafetyGuard safetyGuard;
    private final AiUsageLimiter usageLimiter;
    private final AiProviderClient providerClient;
    private final TencentPronunciationClient pronunciationClient;
    private final AiLogService aiLogService;

    /**
     * One AI request. {@code userPrompt} holds the student's words with the feature's instructions; {@code history}
     * earlier turns of a conversation; {@code complete} decides whether a JSON reply has what the feature shows
     * (null for a text reply).
     */
    public record AiCall(
            User user,
            String sessionId,
            AiFeatureKey feature,
            String language,
            String userPrompt,
            Predicate<Map<String, Object>> complete,
            List<AiProviderPrompt.AiMessage> history,
            AiProviderPrompt.AiImageInput image) {

        /** A request whose reply is a JSON object that {@code complete} accepts. */
        public static AiCall json(
                User user, AiFeatureKey feature, String language, String userPrompt,
                Predicate<Map<String, Object>> complete) {
            return new AiCall(user, null, feature, language, userPrompt, complete, List.of(), null);
        }

        /** A request whose reply is plain text. */
        public static AiCall text(User user, AiFeatureKey feature, String language, String userPrompt) {
            return new AiCall(user, null, feature, language, userPrompt, null, List.of(), null);
        }

        public AiCall withSession(String browserSessionId) {
            return new AiCall(user, browserSessionId, feature, language, userPrompt, complete, history, image);
        }

        public AiCall withHistory(List<AiProviderPrompt.AiMessage> turns) {
            return new AiCall(user, sessionId, feature, language, userPrompt, complete, turns, image);
        }

        public AiCall withImage(AiProviderPrompt.AiImageInput photo) {
            return new AiCall(user, sessionId, feature, language, userPrompt, complete, history, photo);
        }

        boolean expectsJson() {
            return complete != null;
        }
    }

    /** Provider failures are returned; quota exhaustion (429) and an ended account/session (401) are rejected. */
    public AiExecutionResult execute(AiCall call) {
        AiAvailabilityService.Availability availability = availabilityService.getAvailability();
        Usage usage = new Usage(call.user(), call.sessionId(), call.feature(), call.language(), availability.provider(),
                availability.model(), call.userPrompt());
        if (!availability.available()) {
            return unavailable(usage, availability.message());
        }
        if (call.image() != null && !availability.imageAnalysisAvailable()) {
            return unavailable(usage, NO_IMAGE_SUPPORT_ERROR);
        }
        // Admin word filter / AI safety check: unsafe student words never reach the provider or the quota.
        if (safetyGuard.blocksRequest(call.userPrompt())
                || call.history().stream().anyMatch(turn -> safetyGuard.blocksRequest(turn.content()))) {
            return failed(usage, null, AiSafetyGuard.BLOCKED_INPUT_MESSAGE, AiSafetyGuard.BLOCKED_INPUT_LOG);
        }

        reserveQuota(usage);

        AiProviderPrompt prompt = AiPromptBuilder.buildPrompt(
                call.feature(), call.language(), call.userPrompt(), call.expectsJson(), call.history(), call.image());
        AiProviderResult result;
        try {
            result = providerClient.generateText(prompt);
        } catch (RuntimeException exception) {
            log.warn("AI provider execution failed for feature {} with exception type {}.",
                    call.feature(), exception.getClass().getSimpleName());
            return failed(usage, null, AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE, PROVIDER_FAILED_ERROR);
        }
        // A closed stream interrupts its worker. Screening and logging the outcome may still need a database connection.
        return withInterruptSetAside(() -> finish(call, usage, result));
    }

    /** Checks and screens the reply the provider returned, and logs the outcome. */
    private AiExecutionResult finish(AiCall call, Usage usage, AiProviderResult result) {
        if (!result.success()) {
            return failed(usage, result.tokenCount(), AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE, result.errorSummary());
        }
        // Screen the sanitised text (control tokens and invisible characters removed) without truncating tail content,
        // so words reassembled by AiInputSanitizer cannot slip past safety screening.
        String rawText = result.text();
        String sanitizedReply = AiInputSanitizer.sanitizeUnbounded(rawText);
        if (sanitizedReply == null) {
            return failed(usage, result.tokenCount(), AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE, EMPTY_REPLY_ERROR);
        }

        Map<String, Object> json = Map.of();
        if (call.expectsJson()) {
            json = AiJson.parse(rawText);
            if (json.isEmpty()) {
                return failed(usage, result.tokenCount(), AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE, INVALID_JSON_ERROR);
            }
            if (!call.complete().test(json)) {
                return failed(usage, result.tokenCount(), AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE, INCOMPLETE_JSON_ERROR);
            }
        }

        // Screen each decoded JSON leaf value individually for JSON replies (avoiding cross-leaf boundary false
        // positives while catching JSON unicode escapes like \u200b), or the sanitised text directly for plain replies.
        boolean blocked = call.expectsJson()
                ? AiJson.anyTextMatches(json, safetyGuard::blocksReply)
                : safetyGuard.blocksReply(sanitizedReply);
        if (blocked) {
            return failed(usage, result.tokenCount(), AiSafetyGuard.BLOCKED_OUTPUT_MESSAGE, AiSafetyGuard.BLOCKED_OUTPUT_LOG);
        }

        logUsage(usage.entry(AiRequestStatus.SUCCESS, result.tokenCount(), sanitizedReply, null));
        return AiExecutionResult.success(sanitizedReply, json);
    }

    public record PronunciationOutcome(AiRequestStatus status, Integer score, PronunciationFeedback feedback, String message) {
    }

    /** Assesses speech using Tencent's measurements while validating {@code sessionId} on quota and usage writes. */
    public PronunciationOutcome assessPronunciation(
            User user, String sessionId, String language, String target, PracticeUpload audio) {
        Usage usage = new Usage(user, sessionId, AiFeatureKey.PRACTICE_CHECK, language,
                TencentPronunciationClient.PROVIDER, null, target);
        if (!availabilityService.getAvailability().speechAnalysisAvailable()) {
            logUsage(usage.entry(AiRequestStatus.UNAVAILABLE, null, null, NO_SPEECH_SUPPORT_ERROR));
            return new PronunciationOutcome(AiRequestStatus.UNAVAILABLE, null, null,
                    AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE);
        }
        if (safetyGuard.blocksRequest(target)) {
            return pronunciationFailed(usage, AiSafetyGuard.BLOCKED_INPUT_MESSAGE, AiSafetyGuard.BLOCKED_INPUT_LOG);
        }
        // A target the assessment would refuse uses none of the day's requests, like any request text.
        if (!TencentPronunciationClient.acceptsTarget(target)) {
            TencentPronunciationClient.Failure invalid = TencentPronunciationClient.Failure.INVALID_TARGET;
            return pronunciationFailed(usage, invalid.message, invalid.summary);
        }
        reserveQuota(usage);
        TencentPronunciationClient.Assessment assessment;
        try {
            assessment = pronunciationClient.assess(audio, target);
        } catch (RuntimeException exception) {
            log.warn("Pronunciation assessment failed with exception type {}.", exception.getClass().getSimpleName());
            return pronunciationFailed(usage, AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE, PROVIDER_FAILED_ERROR);
        }
        if (assessment.failure() != null) {
            return pronunciationFailed(usage, assessment.failure().message, assessment.failure().summary);
        }
        logUsage(usage.entry(AiRequestStatus.SUCCESS, null, null, null));
        return new PronunciationOutcome(AiRequestStatus.SUCCESS, assessment.score(), assessment.feedback(), null);
    }

    private PronunciationOutcome pronunciationFailed(Usage usage, String message, String error) {
        logUsage(usage.entry(AiRequestStatus.FAILED, null, null, error));
        return new PronunciationOutcome(AiRequestStatus.FAILED, null, null, message);
    }

    private void reserveQuota(Usage usage) {
        try {
            usageLimiter.checkProviderCallAllowed(usage.user(), usage.sessionId());
        } catch (ResponseStatusException quotaExceeded) {
            if (quotaExceeded.getStatusCode() == TOO_MANY_REQUESTS) {
                logUsage(usage.entry(AiRequestStatus.FAILED, null, null, QUOTA_EXCEEDED_ERROR));
            }
            throw quotaExceeded;
        }
    }

    private AiExecutionResult unavailable(Usage usage, String reason) {
        logUsage(usage.entry(AiRequestStatus.UNAVAILABLE, null, null, reason));
        return AiExecutionResult.unavailable(AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE);
    }

    /** Logs a FAILED request and returns it: {@code userMessage} for the student, {@code errorSummary} for the log. */
    private AiExecutionResult failed(Usage usage, Integer tokenCount, String userMessage, String errorSummary) {
        logUsage(usage.entry(AiRequestStatus.FAILED, tokenCount, null, errorSummary));
        return AiExecutionResult.failed(userMessage);
    }

    private void logUsage(AiLogService.UsageEntry entry) {
        withInterruptSetAside(() -> {
            aiLogService.logUsage(entry);
            return null;
        });
    }

    /**
     * Database work with the caller's interrupt status set aside. The Ask SparkBot stream interrupts its virtual worker
     * when it closes, which also cuts the provider call short; database I/O on an interrupted virtual thread would close
     * the pooled connection and fail, losing the log row. The status is restored for the caller, which then keeps
     * nothing of the turn.
     */
    private static <T> T withInterruptSetAside(Supplier<T> work) {
        boolean interrupted = Thread.interrupted();
        try {
            return work.get();
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** The parts of an AI-usage log row that stay the same for one request. */
    private record Usage(
            User user,
            String sessionId,
            AiFeatureKey feature,
            String language,
            String providerName,
            String modelName,
            String input) {

        AiLogService.UsageEntry entry(AiRequestStatus status, Integer tokenCount, String output, String errorSummary) {
            return new AiLogService.UsageEntry(user, sessionId, feature, language, status, providerName, modelName,
                    tokenCount, input, output, errorSummary);
        }
    }
}
