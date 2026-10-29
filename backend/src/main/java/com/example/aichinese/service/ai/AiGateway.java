package com.example.aichinese.service.ai;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.service.AiLogService;
import com.example.aichinese.util.AiInputSanitizer;

/**
 * The one path every AI request takes, whatever the feature: provider availability, the admin AI safety check on the
 * student's words, the per-user quota, the provider call (with speech-to-text first for a recording), the JSON shape
 * the feature needs, the safety check on the reply, and one AI-usage log row for the outcome.
 *
 * <p>A request that is unavailable or blocked never reaches the provider and does not use the quota. Only the length
 * of the prompt and of the reply is logged, never the text ({@link AiLogService}).</p>
 */
@Component
public class AiGateway {

    public static final String NOTHING_HEARD_MESSAGE =
            "SparkBot couldn't hear any speech in this recording. Record it again a little closer to the microphone.";

    static final String QUOTA_EXCEEDED_ERROR = "Platform AI request quota exceeded.";
    static final String PROVIDER_FAILED_ERROR = "Provider request failed or timed out.";
    static final String INVALID_JSON_ERROR = "Provider response was not valid JSON.";
    static final String INCOMPLETE_JSON_ERROR = "Provider response was missing the fields the feature needs.";
    static final String NO_IMAGE_SUPPORT_ERROR = "Configured provider does not support image input.";
    static final String NO_SPEECH_SUPPORT_ERROR = "Configured provider does not support speech-to-text.";

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);
    private static final int MAX_TRANSCRIPT_CHARS = 400;

    private final AiAvailabilityService availabilityService;
    private final AiSafetyGuard safetyGuard;
    private final AiUsageLimiter usageLimiter;
    private final AiPromptBuilder promptBuilder;
    private final AiProviderRouter providerRouter;
    private final AiTranscriptionClient transcriptionClient;
    private final AiLogService aiLogService;

    public AiGateway(
            AiAvailabilityService availabilityService,
            AiSafetyGuard safetyGuard,
            AiUsageLimiter usageLimiter,
            AiPromptBuilder promptBuilder,
            AiProviderRouter providerRouter,
            AiTranscriptionClient transcriptionClient,
            AiLogService aiLogService) {
        this.availabilityService = availabilityService;
        this.safetyGuard = safetyGuard;
        this.usageLimiter = usageLimiter;
        this.promptBuilder = promptBuilder;
        this.providerRouter = providerRouter;
        this.transcriptionClient = transcriptionClient;
        this.aiLogService = aiLogService;
    }

    /**
     * One AI request. {@code userPrompt} holds the student's words with the feature's instructions; {@code history}
     * earlier turns of a conversation; {@code complete} decides whether a JSON reply has what the feature shows
     * (null for a text reply).
     */
    public record AiCall(
            User user,
            AiFeatureKey feature,
            String language,
            String userPrompt,
            Predicate<Map<String, Object>> complete,
            List<AiProviderPrompt.AiMessage> history,
            AiProviderPrompt.AiImageInput image,
            AiAudioInput audio,
            String relatedEntityType,
            Long relatedEntityId) {

        public AiCall {
            Objects.requireNonNull(user, "user");
            Objects.requireNonNull(feature, "feature");
            Objects.requireNonNull(userPrompt, "userPrompt");
            history = history == null ? List.of() : List.copyOf(history);
        }

        /** A request whose reply is a JSON object that {@code complete} accepts. */
        public static AiCall json(
                User user, AiFeatureKey feature, String language, String userPrompt,
                Predicate<Map<String, Object>> complete) {
            return new AiCall(user, feature, language, userPrompt, Objects.requireNonNull(complete, "complete"),
                    List.of(), null, null, null, null);
        }

        /** A request whose reply is plain text. */
        public static AiCall text(User user, AiFeatureKey feature, String language, String userPrompt) {
            return new AiCall(user, feature, language, userPrompt, null, List.of(), null, null, null, null);
        }

        public AiCall withHistory(List<AiProviderPrompt.AiMessage> turns) {
            return new AiCall(user, feature, language, userPrompt, complete, turns, image, audio,
                    relatedEntityType, relatedEntityId);
        }

        public AiCall withImage(AiProviderPrompt.AiImageInput photo) {
            return new AiCall(user, feature, language, userPrompt, complete, history, photo, audio,
                    relatedEntityType, relatedEntityId);
        }

        public AiCall withAudio(AiAudioInput recording) {
            return new AiCall(user, feature, language, userPrompt, complete, history, image, recording,
                    relatedEntityType, relatedEntityId);
        }

        public AiCall withRelatedEntity(String type, Long id) {
            return new AiCall(user, feature, language, userPrompt, complete, history, image, audio, type, id);
        }

        boolean expectsJson() {
            return complete != null;
        }
    }

    /** Runs the request; a reached quota is the only failure thrown (HTTP 429), every other one is returned. */
    public AiExecutionResult execute(AiCall call) {
        AiAvailabilityResponse availability = availabilityService.getAvailability();
        Usage usage = new Usage(call, availability.provider(), availability.model());
        if (!availability.available()) {
            return unavailable(usage, availability.message());
        }
        if (call.image() != null && !availability.imageAnalysisAvailable()) {
            return unavailable(usage, NO_IMAGE_SUPPORT_ERROR);
        }
        if (call.audio() != null && !availability.speechAnalysisAvailable()) {
            return unavailable(usage, NO_SPEECH_SUPPORT_ERROR);
        }

        // Admin "AI safety check": unsafe student words never reach the provider or the quota.
        if (safetyGuard.blocks(call.userPrompt()) || call.history().stream().anyMatch(turn -> safetyGuard.blocks(turn.content()))) {
            return failed(usage, null, AiSafetyGuard.BLOCKED_INPUT_MESSAGE, AiSafetyGuard.BLOCKED_INPUT_LOG);
        }

        try {
            usageLimiter.checkProviderCallAllowed(call.user().getId());
        } catch (ResponseStatusException quotaExceeded) {
            // Every AI request is summarised in the usage log, including quota rejections (HTTP 429).
            aiLogService.logUsage(usage.entry(AiRequestStatus.FAILED, null, null, QUOTA_EXCEEDED_ERROR));
            throw quotaExceeded;
        }

        String userPrompt = call.userPrompt();
        if (call.audio() != null) {
            AiProviderResult transcript = transcriptionClient.transcribe(call.audio());
            if (!transcript.success()) {
                return AiTranscriptionClient.NOTHING_HEARD.equals(transcript.errorSummary())
                        ? failed(usage, null, NOTHING_HEARD_MESSAGE, transcript.errorSummary())
                        : failed(usage, null, AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE, transcript.errorSummary());
            }
            String heard = AiInputSanitizer.sanitize(transcript.text(), MAX_TRANSCRIPT_CHARS);
            if (heard == null) {
                return failed(usage, null, NOTHING_HEARD_MESSAGE, AiTranscriptionClient.NOTHING_HEARD);
            }
            if (safetyGuard.blocks(heard)) {
                return failed(usage, null, AiSafetyGuard.BLOCKED_INPUT_MESSAGE, AiSafetyGuard.BLOCKED_INPUT_LOG);
            }
            userPrompt = userPrompt + " What the speech-to-text service heard in the recording: \"" + heard + "\".";
        }

        AiProviderPrompt prompt = promptBuilder.buildPrompt(
                call.feature(), call.language(), userPrompt, call.expectsJson(), call.history(), call.image());
        AiProviderResult result;
        try {
            result = providerRouter.resolveClient().generateText(prompt);
        } catch (RuntimeException exception) {
            log.warn("AI provider execution failed for feature {} with exception type {}.",
                    call.feature(), exception.getClass().getSimpleName());
            return failed(usage, null, AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE, PROVIDER_FAILED_ERROR);
        }
        if (!result.success()) {
            return failed(usage, null, AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE, result.errorSummary());
        }

        Map<String, Object> json = Map.of();
        if (call.expectsJson()) {
            json = AiJson.parse(result.text());
            if (json.isEmpty()) {
                return failed(usage, result.tokenCount(), AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE, INVALID_JSON_ERROR);
            }
            if (!call.complete().test(json)) {
                return failed(usage, result.tokenCount(), AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE, INCOMPLETE_JSON_ERROR);
            }
        }

        if (safetyGuard.blocks(result.text())) {
            return failed(usage, result.tokenCount(), AiSafetyGuard.BLOCKED_OUTPUT_MESSAGE, AiSafetyGuard.BLOCKED_OUTPUT_LOG);
        }

        aiLogService.logUsage(usage.entry(AiRequestStatus.SUCCESS, result.tokenCount(), result.text(), null));
        return AiExecutionResult.success(result.text(), json);
    }

    private AiExecutionResult unavailable(Usage usage, String reason) {
        aiLogService.logUsage(usage.entry(AiRequestStatus.UNAVAILABLE, null, null, reason));
        return AiExecutionResult.unavailable(AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE);
    }

    /** Logs a FAILED request and returns it: {@code userMessage} for the student, {@code errorSummary} for the log. */
    private AiExecutionResult failed(Usage usage, Integer tokenCount, String userMessage, String errorSummary) {
        aiLogService.logUsage(usage.entry(AiRequestStatus.FAILED, tokenCount, null, errorSummary));
        return AiExecutionResult.failed(userMessage);
    }

    /** The parts of an AI-usage log row that stay the same for one request. */
    private record Usage(AiCall call, String providerName, String modelName) {

        AiLogService.UsageEntry entry(AiRequestStatus status, Integer tokenCount, String output, String errorSummary) {
            return new AiLogService.UsageEntry(call.user(), call.feature(), call.language(), status, providerName,
                    modelName, tokenCount, call.userPrompt(), output, errorSummary, call.relatedEntityType(),
                    call.relatedEntityId());
        }
    }
}
