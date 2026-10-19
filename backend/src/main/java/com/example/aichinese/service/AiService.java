package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.persistence.criteria.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.ai.AdminAiUsageLogResponse;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiChatResponse;
import com.example.aichinese.dto.ai.AiGeneratedQuizQuestionResponse;
import com.example.aichinese.dto.ai.AiPronunciationFeedbackResponse;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorResponse;
import com.example.aichinese.dto.ai.AiSentencePracticeRequest;
import com.example.aichinese.dto.ai.AiSentencePracticeResponse;
import com.example.aichinese.dto.ai.AiStoryComprehensionQuestionResponse;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorResponse;
import com.example.aichinese.dto.ai.AiStoryVocabularyItemResponse;
import com.example.aichinese.dto.ai.AiVocabularyHelperRequest;
import com.example.aichinese.dto.ai.AiVocabularyHelperResponse;
import com.example.aichinese.dto.ai.AiWritingFeedbackResponse;
import com.example.aichinese.entity.AiChatMessage;
import com.example.aichinese.entity.AiChatSession;
import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiChatSenderRole;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.PracticeSubmissionType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AiChatMessageRepository;
import com.example.aichinese.repository.AiChatSessionRepository;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.AiExecutionResult;
import com.example.aichinese.service.ai.AiPromptBuilder;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiProviderResult;
import com.example.aichinese.service.ai.AiProviderRouter;
import com.example.aichinese.service.ai.AiSafetyGuard;
import com.example.aichinese.service.ai.AiUsageLimiter;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.LanguageCodes;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.PersonalDataRedactor;
import com.example.aichinese.util.TextUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Learner AI functions in scope of the proposal: chat tutor, vocabulary helper, sentence
 * practice, quiz generator and story generator, plus prototype-level pronunciation (audio) and
 * writing (handwriting image) feedback that depends on the provider's capability.
 *
 * <p>Data minimisation: no user name, e-mail address or id is ever placed in a prompt; learner
 * text is sanitised here and redacted of direct identifiers ({@link PersonalDataRedactor}) before
 * it is stored as chat history and again in {@link AiPromptBuilder} before it is sent.</p>
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final int CHAT_HISTORY_LIMIT = 20;
    private static final int MAX_QUIZ_QUESTIONS = 10;
    private static final int MAX_STORY_VOCABULARY_ITEMS = 20;
    private static final int MAX_STORY_COMPREHENSION_QUESTIONS = 10;
    private static final int MAX_AI_OPTION_ITEMS = 8;
    private static final int DEFAULT_QUIZ_QUESTIONS = 5;
    private static final int MAX_RETAINED_AI_CHAT_RESPONSE_CHARS = 4000;
    private static final String QUOTA_EXCEEDED_ERROR = "Platform AI request quota exceeded.";
    private static final String BLOCKED_CHAT_MESSAGE_PLACEHOLDER = "[Message withheld by the AI safety check]";
    private static final String RELATED_ENTITY_CHAT_SESSION = "AiChatSession";
    private static final String RELATED_ENTITY_UPLOADED_FILE = "UploadedFile";
    private static final String PROVIDER_TIMEOUT_ERROR = "Provider request failed or timed out.";
    private static final String INVALID_JSON_ERROR = "Provider response was not valid JSON.";
    private static final String IMAGE_READ_ERROR = "Uploaded image could not be read for AI analysis.";
    private static final String MIME_IMAGE_JPEG = "image/jpeg";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final UserRepository userRepository;
    private final AiChatSessionRepository aiChatSessionRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiPromptBuilder aiPromptBuilder;
    private final AiProviderRouter aiProviderRouter;
    private final AiAvailabilityService aiAvailabilityService;
    private final AiLogService aiLogService;
    private final AiFileStorageService aiFileStorageService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final AiUsageLimiter aiUsageLimiter;
    private final AiSafetyGuard aiSafetyGuard;
    private final PracticeSubmissionService practiceSubmissionService;

    public AiService(
            UserRepository userRepository,
            AiChatSessionRepository aiChatSessionRepository,
            AiChatMessageRepository aiChatMessageRepository,
            AiUsageLogRepository aiUsageLogRepository,
            AiPromptBuilder aiPromptBuilder,
            AiProviderRouter aiProviderRouter,
            AiAvailabilityService aiAvailabilityService,
            AiLogService aiLogService,
            AiFileStorageService aiFileStorageService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            AiUsageLimiter aiUsageLimiter,
            AiSafetyGuard aiSafetyGuard,
            PracticeSubmissionService practiceSubmissionService) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.aiChatSessionRepository = Objects.requireNonNull(aiChatSessionRepository, "aiChatSessionRepository");
        this.aiChatMessageRepository = Objects.requireNonNull(aiChatMessageRepository, "aiChatMessageRepository");
        this.aiUsageLogRepository = Objects.requireNonNull(aiUsageLogRepository, "aiUsageLogRepository");
        this.aiPromptBuilder = Objects.requireNonNull(aiPromptBuilder, "aiPromptBuilder");
        this.aiProviderRouter = Objects.requireNonNull(aiProviderRouter, "aiProviderRouter");
        this.aiAvailabilityService = Objects.requireNonNull(aiAvailabilityService, "aiAvailabilityService");
        this.aiLogService = Objects.requireNonNull(aiLogService, "aiLogService");
        this.aiFileStorageService = Objects.requireNonNull(aiFileStorageService, "aiFileStorageService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.aiUsageLimiter = Objects.requireNonNull(aiUsageLimiter, "aiUsageLimiter");
        this.aiSafetyGuard = Objects.requireNonNull(aiSafetyGuard, "aiSafetyGuard");
        this.practiceSubmissionService = Objects.requireNonNull(practiceSubmissionService, "practiceSubmissionService");
    }

    public AiChatResponse chat(SecurityUserDetails currentUser, AiChatRequest request) {
        User user = requireUser(currentUser);
        requireRole(currentUser, RoleName.STUDENT, "AI features require a student account.");
        String language = resolveLanguage(user, request.languagePreference());
        String sanitizedUserMessage = PersonalDataRedactor.redact(AiInputSanitizer.sanitizeLong(request.message()));
        if (sanitizedUserMessage == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Message is required.");
        }
        AiChatSession session = resolveChatSession(user, request);
        List<AiProviderPrompt.AiMessage> history = aiChatMessageRepository.findBySessionIdOrderByCreatedAtDesc(
                session.getId(),
                PageRequest.of(0, CHAT_HISTORY_LIMIT)).getContent().stream()
                .sorted(Comparator.comparing(AiChatMessage::getCreatedAt))
                .map(this::toAiMessage)
                .toList();

        AiExecutionResult execution = generateText(
                user,
                AiFeatureKey.CHAT_TUTOR,
                language,
                buildChatPrompt(request, sanitizedUserMessage),
                false,
                history,
                RELATED_ENTITY_CHAT_SESSION,
                session.getId());
        // Stored only after the safety check and quota: a blocked message must not re-enter later
        // turns through the chat history, and a rejected (HTTP 429) turn leaves no orphan message.
        aiLogService.appendChatMessage(session, AiChatSenderRole.USER,
                AiSafetyGuard.BLOCKED_INPUT_LOG.equals(execution.errorSummary())
                        ? BLOCKED_CHAT_MESSAGE_PLACEHOLDER
                        : sanitizedUserMessage);

        String responseText = execution.status() == AiRequestStatus.SUCCESS
                ? execution.responseText()
                : execution.userMessage();
        String retainedResponseText = sanitizedAssistantMessage(responseText);

        aiLogService.appendChatMessage(session, AiChatSenderRole.ASSISTANT, retainedResponseText);
        return new AiChatResponse(
                session.getId(),
                sanitizedUserMessage,
                retainedResponseText,
                language,
                LocalDateTime.now(),
                execution.status().name());
    }

    public AiVocabularyHelperResponse vocabularyHelper(SecurityUserDetails currentUser, AiVocabularyHelperRequest request) {
        User user = requireUser(currentUser);
        requireRole(currentUser, RoleName.STUDENT, "AI features require a student account.");
        String language = resolveLanguage(user, request.languagePreference());
        AiExecutionResult execution = generateText(
                user,
                AiFeatureKey.VOCABULARY_HELPER,
                language,
                buildVocabularyPrompt(request, language),
                true,
                List.of(),
                null,
                null);

        if (execution.status() != AiRequestStatus.SUCCESS) {
            return unavailableVocabularyResponse(execution);
        }

        // generateText only reports SUCCESS for JSON tools after the reply parsed as a non-empty object.
        return parseVocabularyResponse(parseJsonMap(execution.responseText()).values(), execution.status().name());
    }

    public AiSentencePracticeResponse sentencePractice(SecurityUserDetails currentUser, AiSentencePracticeRequest request) {
        User user = requireUser(currentUser);
        requireRole(currentUser, RoleName.STUDENT, "AI features require a student account.");
        String language = resolveLanguage(user, request.languagePreference());
        String sanitizedSentence = PersonalDataRedactor.redact(
                requireSanitizedLong(request.sentence(), "Sentence is required."));
        AiExecutionResult execution = generateText(
                user,
                AiFeatureKey.SENTENCE_PRACTICE,
                language,
                buildSentencePrompt(request, sanitizedSentence, language),
                true,
                List.of(),
                null,
                null);

        if (execution.status() != AiRequestStatus.SUCCESS) {
            return unavailableSentenceResponse(sanitizedSentence, execution);
        }
        return parseSentenceResponse(
                sanitizedSentence, parseJsonMap(execution.responseText()).values(), execution.status().name());
    }

    /** Usage mode of a teacher AI draft (D6): returned to the teacher, never saved as content. */
    public static final String TEACHER_DRAFT_USAGE_MODE = "TEACHER_DRAFT";

    public AiQuizGeneratorResponse quizGenerator(SecurityUserDetails currentUser, AiQuizGeneratorRequest request) {
        requireRole(currentUser, RoleName.STUDENT, "AI features require a student account.");
        return generateQuiz(requireUser(currentUser), request, "TEMPORARY_SELF_PRACTICE");
    }

    /**
     * D6: an approved teacher asks for an AI quiz draft. The draft is returned only; nothing is
     * stored as course content. Usage is logged (role TEACHER) and rate-limited like student calls.
     */
    public AiQuizGeneratorResponse teacherQuizDraft(SecurityUserDetails currentUser, AiQuizGeneratorRequest request) {
        requireRole(currentUser, RoleName.TEACHER, "AI drafts require a teacher account.");
        return generateQuiz(requireUser(currentUser), request, TEACHER_DRAFT_USAGE_MODE);
    }

    /** D6: AI story draft for an approved teacher; returned only, never saved automatically. */
    public AiStoryGeneratorResponse teacherStoryDraft(SecurityUserDetails currentUser, AiStoryGeneratorRequest request) {
        requireRole(currentUser, RoleName.TEACHER, "AI drafts require a teacher account.");
        return generateStory(requireUser(currentUser), request, TEACHER_DRAFT_USAGE_MODE);
    }

    private AiQuizGeneratorResponse generateQuiz(User user, AiQuizGeneratorRequest request, String usageMode) {
        String language = resolveLanguage(user, request.languagePreference());
        String sanitizedTopic = requireSanitizedShort(request.topic(), "Topic is required.");
        AiExecutionResult execution = generateText(
                user,
                AiFeatureKey.QUIZ_GENERATOR,
                language,
                buildQuizPrompt(request, language, usageMode),
                true,
                List.of(),
                null,
                null);

        if (execution.status() != AiRequestStatus.SUCCESS) {
            return new AiQuizGeneratorResponse(
                    "AI quiz unavailable",
                    sanitizedTopic,
                    sanitizePromptFragment(request.level()),
                    sanitizePromptFragment(request.category()),
                    usageMode,
                    List.of(),
                    execution.status().name());
        }
        return parseQuizResponse(
                request, sanitizedTopic, usageMode, parseJsonMap(execution.responseText()).values(), execution.status().name());
    }

    public AiStoryGeneratorResponse storyGenerator(SecurityUserDetails currentUser, AiStoryGeneratorRequest request) {
        requireRole(currentUser, RoleName.STUDENT, "AI features require a student account.");
        return generateStory(requireUser(currentUser), request, "SELF_PRACTICE");
    }

    private AiStoryGeneratorResponse generateStory(User user, AiStoryGeneratorRequest request, String usageMode) {
        String language = resolveLanguage(user, request.languagePreference());
        String sanitizedTopic = requireSanitizedShort(request.topic(), "Topic is required.");
        AiExecutionResult execution = generateText(
                user,
                AiFeatureKey.STORY_GENERATOR,
                language,
                buildStoryPrompt(request, language, usageMode),
                true,
                List.of(),
                null,
                null);

        if (execution.status() != AiRequestStatus.SUCCESS) {
            return fallbackStoryResponse(sanitizedTopic, usageMode, execution.status().name(), execution.userMessage());
        }
        return parseStoryResponse(
                sanitizedTopic, usageMode, parseJsonMap(execution.responseText()).values(), execution.status().name());
    }

    /**
     * Stores the learner's recording for teacher review, optionally linked to a published lesson
     * ({@code lessonId} may be {@code null}). The configured models do not accept audio, so no
     * provider call is made and the response states this clearly. Every upload is kept as a practice
     * submission (D7a) that an approved teacher of the student can review.
     */
    public AiPronunciationFeedbackResponse pronunciationFeedback(
            SecurityUserDetails currentUser,
            MultipartFile audioFile,
            String targetText,
            String pinyin,
            String tone,
            String languagePreference,
            Long lessonId) {
        User user = requireUser(currentUser);
        requireRole(currentUser, RoleName.STUDENT, "Pronunciation feedback requires a student account.");
        String sanitizedTargetText = requireSanitizedShort(targetText, "Target text is required.");
        Long submissionLessonId = practiceSubmissionService.requireSubmittableLesson(lessonId);

        UploadedFile uploadedFile = aiFileStorageService.storePronunciationAudio(audioFile, user);
        String language = resolveLanguage(user, languagePreference);
        String inputSummary = pronunciationInputSummary(sanitizedTargetText, pinyin, tone);

        aiLogService.createFeedbackRecord(
                user,
                AiFeatureKey.PRONUNCIATION_FEEDBACK,
                language,
                inputSummary,
                AiAvailabilityService.PRONUNCIATION_UNAVAILABLE_MESSAGE,
                AiRequestStatus.UNAVAILABLE,
                uploadedFile);

        aiLogService.createUsageLog(
                user,
                primaryRole(user),
                AiFeatureKey.PRONUNCIATION_FEEDBACK,
                language,
                AiRequestStatus.UNAVAILABLE,
                aiAvailabilityService.normalizedProvider(),
                null,
                null,
                inputSummary,
                null,
                "Configured model does not support audio input.",
                RELATED_ENTITY_UPLOADED_FILE,
                uploadedFile.getId());

        Long submissionId = practiceSubmissionService.recordSubmission(
                user,
                PracticeSubmissionType.PRONUNCIATION_AUDIO,
                uploadedFile,
                sanitizedTargetText,
                submissionLessonId,
                AiRequestStatus.UNAVAILABLE,
                AiAvailabilityService.PRONUNCIATION_UNAVAILABLE_MESSAGE);

        return new AiPronunciationFeedbackResponse(
                AiAvailabilityService.PRONUNCIATION_UNAVAILABLE_MESSAGE,
                "Audio analysis is not available with the configured AI model.",
                "Tone feedback is not available with the configured AI model.",
                "Listen to the model audio, repeat the phrase slowly and compare the tones.",
                "Your teacher can review the saved recording.",
                AiRequestStatus.UNAVAILABLE.name(),
                AiAvailabilityService.MEDIA_FEEDBACK_NOTICE,
                submissionId);
    }

    /**
     * Stores the learner's handwriting image for teacher review, optionally linked to a published
     * lesson ({@code lessonId} may be {@code null}), and, when the configured provider accepts image
     * input (OpenAI Responses API), returns limited AI-assisted practice feedback. The result is never
     * a score. Every upload is kept as a practice submission (D7a) with the AI feedback status/text,
     * reviewable by the student's teacher.
     */
    public AiWritingFeedbackResponse writingFeedback(
            SecurityUserDetails currentUser,
            MultipartFile imageFile,
            String targetCharacter,
            String languagePreference,
            Long lessonId) {
        User user = requireUser(currentUser);
        requireRole(currentUser, RoleName.STUDENT, "Writing feedback requires a student account.");
        String sanitizedTargetCharacter = requireSanitizedShort(targetCharacter, "Target character is required.");
        Long submissionLessonId = practiceSubmissionService.requireSubmittableLesson(lessonId);

        UploadedFile uploadedFile = aiFileStorageService.storeWritingImage(imageFile, user);
        String language = resolveLanguage(user, languagePreference);
        String inputSummary = "Target character: " + sanitizedTargetCharacter;

        AiAvailabilityResponse availability = aiAvailabilityService.getAvailability();
        if (!availability.available() || !availability.imageAnalysisAvailable()) {
            AiWritingFeedbackResponse unavailable =
                    unavailableWritingFeedback(user, language, inputSummary, uploadedFile, availability);
            return unavailable.withSubmissionId(recordWritingSubmission(
                    user, uploadedFile, sanitizedTargetCharacter, submissionLessonId, unavailable));
        }

        AiProviderPrompt.AiImageInput image = readImage(imageFile);
        AiExecutionResult execution;
        if (image == null) {
            aiLogService.createUsageLog(
                    user,
                    primaryRole(user),
                    AiFeatureKey.WRITING_FEEDBACK,
                    language,
                    AiRequestStatus.FAILED,
                    availability.provider(),
                    availability.model(),
                    null,
                    inputSummary,
                    null,
                    IMAGE_READ_ERROR,
                    RELATED_ENTITY_UPLOADED_FILE,
                    uploadedFile.getId());
            execution = AiExecutionResult.failed(
                    availability.provider(),
                    availability.model(),
                    AiAvailabilityService.WRITING_UNAVAILABLE_MESSAGE,
                    IMAGE_READ_ERROR);
        } else {
            execution = generateText(
                    user,
                    AiFeatureKey.WRITING_FEEDBACK,
                    language,
                    buildWritingPrompt(sanitizedTargetCharacter),
                    true,
                    List.of(),
                    RELATED_ENTITY_UPLOADED_FILE,
                    uploadedFile.getId(),
                    image);
        }

        ParsedAiJson parsed = execution.status() == AiRequestStatus.SUCCESS
                ? parseJsonMap(execution.responseText())
                : ParsedAiJson.invalid();
        AiWritingFeedbackResponse response = parsed.valid()
                ? parseWritingResponse(parsed.values())
                : failedWritingFeedback(execution);

        aiLogService.createFeedbackRecord(
                user,
                AiFeatureKey.WRITING_FEEDBACK,
                language,
                inputSummary,
                response.feedback(),
                AiRequestStatus.fromValue(response.status()),
                uploadedFile);
        return response.withSubmissionId(recordWritingSubmission(
                user, uploadedFile, sanitizedTargetCharacter, submissionLessonId, response));
    }

    private Long recordWritingSubmission(
            User user,
            UploadedFile uploadedFile,
            String targetCharacter,
            Long lessonId,
            AiWritingFeedbackResponse response) {
        AiRequestStatus status = AiRequestStatus.tryFrom(response.status()).orElse(AiRequestStatus.FAILED);
        String aiText = status == AiRequestStatus.SUCCESS
                ? String.join("\n", Stream.of(
                                response.feedback(), response.clarityComment(), response.improvementTips(), response.encouragement())
                        .filter(value -> value != null && !value.isBlank())
                        .toList())
                : response.feedback();
        return practiceSubmissionService.recordSubmission(
                user, PracticeSubmissionType.HANDWRITING_IMAGE, uploadedFile, targetCharacter, lessonId, status, aiText);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAiUsageLogResponse> getUsageLogs(
            String featureType,
            String status,
            String userRole,
            LocalDate dateFrom,
            LocalDate dateTo,
            int page,
            int size) {
        Pageable pageable = PageRequest.of(
                PaginationUtils.sanitizePage(page),
                PaginationUtils.sanitizeSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedFeatureType = normalizeFilterValue(featureType);
        String normalizedStatus = normalizeFilterValue(status);
        String normalizedUserRole = normalizeFilterValue(userRole);

        Specification<AiUsageLog> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (normalizedFeatureType != null) {
                predicates.add(cb.equal(root.get("featureKey"), normalizedFeatureType));
            }
            if (normalizedStatus != null) {
                predicates.add(cb.equal(root.get("status"), normalizedStatus));
            }
            if (normalizedUserRole != null) {
                predicates.add(cb.equal(root.get("userRole"), normalizedUserRole));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom.atStartOfDay()));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), dateTo.plusDays(1).atStartOfDay()));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<AiUsageLog> result = aiUsageLogRepository.findAll(specification, pageable);
        List<AdminAiUsageLogResponse> items = result.getContent().stream()
                .map(this::toAdminUsageLogResponse)
                .toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public AiAvailabilityResponse getAvailability() {
        return aiAvailabilityService.getAvailability();
    }

    private AiExecutionResult generateText(
            User user,
            AiFeatureKey featureKey,
            String language,
            String userPrompt,
            boolean expectJson,
            List<AiProviderPrompt.AiMessage> history,
            String relatedEntityType,
            Long relatedEntityId) {
        return generateText(user, featureKey, language, userPrompt, expectJson, history,
                relatedEntityType, relatedEntityId, null);
    }

    private AiExecutionResult generateText(
            User user,
            AiFeatureKey featureKey,
            String language,
            String userPrompt,
            boolean expectJson,
            List<AiProviderPrompt.AiMessage> history,
            String relatedEntityType,
            Long relatedEntityId,
            AiProviderPrompt.AiImageInput image) {
        String userRole = primaryRole(user);
        String inputSummary = userPrompt;
        AiAvailabilityResponse availability = aiAvailabilityService.getAvailability();
        if (!availability.available()) {
            aiLogService.createUsageLog(
                    user,
                    userRole,
                    featureKey,
                    language,
                    AiRequestStatus.UNAVAILABLE,
                    availability.provider(),
                    availability.model(),
                    null,
                    inputSummary,
                    null,
                    availability.message(),
                    relatedEntityType,
                    relatedEntityId);
            return AiExecutionResult.unavailable(
                    availability.provider(),
                    availability.model(),
                    AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE,
                    availability.message());
        }

        // Admin "AI safety check": unsafe learner requests never reach the provider or the quota.
        if (aiSafetyGuard.blocksInput(userPrompt)) {
            aiLogService.createUsageLog(
                    user,
                    userRole,
                    featureKey,
                    language,
                    AiRequestStatus.FAILED,
                    availability.provider(),
                    availability.model(),
                    null,
                    inputSummary,
                    null,
                    AiSafetyGuard.BLOCKED_INPUT_LOG,
                    relatedEntityType,
                    relatedEntityId);
            return AiExecutionResult.failed(
                    availability.provider(),
                    availability.model(),
                    AiSafetyGuard.BLOCKED_INPUT_MESSAGE,
                    AiSafetyGuard.BLOCKED_INPUT_LOG);
        }

        try {
            aiUsageLimiter.checkProviderCallAllowed(user.getId());
        } catch (ResponseStatusException quotaExceeded) {
            // SDD 5.6: every AI request is summarised in the AI-usage log, including quota rejections (HTTP 429).
            aiLogService.createUsageLog(
                    user,
                    userRole,
                    featureKey,
                    language,
                    AiRequestStatus.FAILED,
                    availability.provider(),
                    availability.model(),
                    null,
                    inputSummary,
                    null,
                    QUOTA_EXCEEDED_ERROR,
                    relatedEntityType,
                    relatedEntityId);
            throw quotaExceeded;
        }
        AiProviderPrompt prompt = aiPromptBuilder.buildPrompt(featureKey, language, userPrompt, expectJson, history, image);
        Timer.Sample providerTimer = Timer.start(meterRegistry);
        AiProviderResult result;
        try {
            result = aiProviderRouter.resolveClient().generateText(prompt);
        } catch (RuntimeException exception) {
            recordProviderDuration(providerTimer, featureKey, "exception");
            log.warn("AI provider execution failed for feature {} with exception type {}.",
                    featureKey, exception.getClass().getSimpleName());
            aiLogService.createUsageLog(
                    user,
                    userRole,
                    featureKey,
                    language,
                    AiRequestStatus.FAILED,
                    availability.provider(),
                    availability.model(),
                    null,
                    inputSummary,
                    null,
                    PROVIDER_TIMEOUT_ERROR,
                    relatedEntityType,
                    relatedEntityId);
            return AiExecutionResult.failed(
                    availability.provider(),
                    availability.model(),
                    AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE,
                    PROVIDER_TIMEOUT_ERROR);
        }
        recordProviderDuration(providerTimer, featureKey, result.success() ? "success" : "failure");
        if (!result.success()) {
            aiLogService.createUsageLog(
                    user,
                    userRole,
                    featureKey,
                    language,
                    AiRequestStatus.FAILED,
                    result.providerName(),
                    result.modelName(),
                    null,
                    inputSummary,
                    null,
                    result.errorSummary(),
                    relatedEntityType,
                    relatedEntityId);
            return AiExecutionResult.failed(
                    result.providerName(),
                    result.modelName(),
                    AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE,
                    result.errorSummary());
        }

        if (expectJson && !parseJsonMap(result.text()).valid()) {
            aiLogService.createUsageLog(
                    user,
                    userRole,
                    featureKey,
                    language,
                    AiRequestStatus.FAILED,
                    result.providerName(),
                    result.modelName(),
                    result.tokenCount(),
                    inputSummary,
                    null,
                    INVALID_JSON_ERROR,
                    relatedEntityType,
                    relatedEntityId);
            return AiExecutionResult.failed(
                    result.providerName(),
                    result.modelName(),
                    AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE,
                    INVALID_JSON_ERROR);
        }

        if (aiSafetyGuard.blocksOutput(result.text())) {
            aiLogService.createUsageLog(
                    user,
                    userRole,
                    featureKey,
                    language,
                    AiRequestStatus.FAILED,
                    result.providerName(),
                    result.modelName(),
                    result.tokenCount(),
                    inputSummary,
                    null,
                    AiSafetyGuard.BLOCKED_OUTPUT_LOG,
                    relatedEntityType,
                    relatedEntityId);
            return AiExecutionResult.failed(
                    result.providerName(),
                    result.modelName(),
                    AiSafetyGuard.BLOCKED_OUTPUT_MESSAGE,
                    AiSafetyGuard.BLOCKED_OUTPUT_LOG);
        }

        aiLogService.createUsageLog(
                user,
                userRole,
                featureKey,
                language,
                AiRequestStatus.SUCCESS,
                result.providerName(),
                result.modelName(),
                result.tokenCount(),
                inputSummary,
                result.text(),
                null,
                relatedEntityType,
                relatedEntityId);
        return AiExecutionResult.success(result.providerName(), result.modelName(), result.text(), result.tokenCount());
    }

    private User requireUser(SecurityUserDetails currentUser) {
        Long userId = currentUser == null ? null : currentUser.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(NOT_FOUND, "Authenticated user could not be found.");
        }

        return userRepository.findByIdIn(List.of(userId)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Authenticated user could not be found."));
    }

    /**
     * The learner's current interface language (en, ms or zh-CN) wins; the account preference is the
     * fallback. A legacy "zh" value is mapped to "zh-CN".
     */
    private String resolveLanguage(User user, String requestedLanguage) {
        String requested = LanguageCodes.normalize(requestedLanguage);
        if (requested != null) {
            return requested;
        }
        String preferred = LanguageCodes.normalize(user.getPreferredLanguage());
        return preferred != null ? preferred : LanguageCodes.ENGLISH;
    }

    private static String normalizeFilterValue(String value) {
        String trimmed = TextUtils.trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private AiChatSession resolveChatSession(User user, AiChatRequest request) {
        if (request.sessionId() == null) {
            return aiLogService.createChatSession(user, AiFeatureKey.CHAT_TUTOR,
                    resolveLanguage(user, request.languagePreference()), request.relatedLessonId());
        }

        return aiChatSessionRepository.findByIdAndUserId(request.sessionId(), user.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AI chat session was not found."));
    }

    private AiProviderPrompt.AiMessage toAiMessage(AiChatMessage message) {
        String role = message.getSenderRole() == AiChatSenderRole.USER
                ? "user"
                : "assistant";
        String sanitized = AiInputSanitizer.sanitizeLong(message.getMessageText());
        return new AiProviderPrompt.AiMessage(role, sanitized == null ? "" : sanitized);
    }

    private String buildChatPrompt(AiChatRequest request, String sanitizedUserMessage) {
        StringBuilder builder = new StringBuilder();
        builder.append("Learner message: \"")
                .append(sanitizedUserMessage)
                .append("\".");
        String sanitizedContext = AiInputSanitizer.sanitizeLong(request.context());
        if (sanitizedContext != null) {
            builder.append(" Learning context: ").append(sanitizedContext).append(".");
        }
        if (request.relatedLessonId() != null) {
            builder.append(" Related lesson id: ").append(request.relatedLessonId()).append(".");
        }
        builder.append(" Reply as a concise beginner Mandarin tutor in at most about 120 words.")
                .append(" If the message is not about learning Mandarin, politely decline and suggest a Mandarin-learning question instead.");
        return builder.toString();
    }

    private String sanitizedAssistantMessage(String responseText) {
        String sanitized = AiInputSanitizer.sanitize(responseText, MAX_RETAINED_AI_CHAT_RESPONSE_CHARS);
        return sanitized == null ? AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE : sanitized;
    }

    private String buildVocabularyPrompt(AiVocabularyHelperRequest request, String language) {
        String outputLanguage = normalizedPromptLanguage(request.outputLanguage(), language);
        return "Create a short beginner vocabulary card in JSON for the word or phrase \""
                + requireSanitizedShort(request.word(), "Word is required.")
                + "\". Input language: "
                + TextUtils.defaultIfBlank(AiInputSanitizer.sanitizeShort(request.inputLanguage()), "not specified")
                + ". Output language preference: " + outputLanguage
                + ". Learner level: " + defaultLevel(request.learnerLevel())
                + ". Use an everyday young-adult example (university, work, food, travel or daily life)."
                + " Return keys: chineseWord, pinyin, englishMeaning, bahasaMelayuMeaning, simpleExplanation, exampleSentence, exampleSentenceTranslation, encouragement.";
    }

    private String buildSentencePrompt(AiSentencePracticeRequest request, String sanitizedSentence, String language) {
        return "Check this beginner Mandarin practice sentence and return JSON: \""
                + sanitizedSentence
                + "\". Learner level: " + defaultLevel(request.learnerLevel())
                + ". Language preference: " + language
                + ". Explain at most two corrections briefly; do not give a grade."
                + " Return keys: correctedSentence, pinyin, englishMeaning, bahasaMelayuMeaning, improvementExplanation, encouragement.";
    }

    private String buildQuizPrompt(AiQuizGeneratorRequest request, String language, String usageMode) {
        return "Generate a short beginner Mandarin self-practice quiz in JSON for a young adult learner. Topic: "
                + AiInputSanitizer.sanitizeShort(request.topic())
                + ". Level: " + defaultLevel(request.level())
                + ". Category: " + TextUtils.defaultIfBlank(AiInputSanitizer.sanitizeShort(request.category()), "general")
                + ". Number of questions: " + resolveNumberOfQuestions(request.numberOfQuestions())
                + ". Question types: " + sanitizedQuestionTypes(request.questionTypes())
                + ". Usage mode: " + usageMode
                + ". Language preference: " + language
                + ". Return keys: title, questions. Each question should include questionText, questionType, options, correctAnswer, explanation, pinyin, translation.";
    }

    private String buildStoryPrompt(AiStoryGeneratorRequest request, String language, String usageMode) {
        return "Generate a short beginner Mandarin reading passage in JSON about an everyday young-adult situation. Topic: "
                + AiInputSanitizer.sanitizeShort(request.topic())
                + ". Level: " + defaultLevel(request.level())
                + ". Length preference: " + TextUtils.defaultIfBlank(AiInputSanitizer.sanitizeShort(request.lengthPreference()), "short")
                + ". Usage mode: " + usageMode
                + ". Language preference: " + language
                + ". Return keys: title, chineseStory, pinyin, englishTranslation, bahasaMelayuTranslation, vocabularyList, comprehensionQuestions, encouragement.";
    }

    private static String buildWritingPrompt(String targetCharacter) {
        return "Give short handwriting practice feedback in JSON on the attached photo of the learner writing \""
                + targetCharacter
                + "\". Comment only on what is visible. Do not give a score, grade or pass/fail result."
                + " Return keys: feedback, clarityComment, improvementTips, encouragement.";
    }

    private static String pronunciationInputSummary(String targetText, String pinyin, String tone) {
        List<String> parts = new ArrayList<>();
        parts.add("Target text: " + targetText);
        addSummaryPart(parts, "Pinyin", pinyin);
        addSummaryPart(parts, "Tone", tone);
        return String.join("; ", parts);
    }

    private static void addSummaryPart(List<String> parts, String label, String value) {
        String trimmed = TextUtils.trimToNull(value);
        if (trimmed != null) {
            parts.add(label + ": " + trimmed);
        }
    }

    private static AiProviderPrompt.AiImageInput readImage(MultipartFile imageFile) {
        try {
            byte[] bytes = imageFile.getBytes();
            if (bytes.length == 0) {
                return null;
            }
            String mimeType = UploadStorageSupport.normalizeContentType(imageFile.getContentType());
            if (mimeType == null || mimeType.isBlank() || "image/jpg".equals(mimeType)) {
                mimeType = MIME_IMAGE_JPEG;
            }
            return new AiProviderPrompt.AiImageInput(bytes, mimeType);
        } catch (IOException | IllegalArgumentException exception) {
            log.warn("Handwriting image could not be read for AI analysis ({}).", exception.getClass().getSimpleName());
            return null;
        }
    }

    private AiWritingFeedbackResponse unavailableWritingFeedback(
            User user,
            String language,
            String inputSummary,
            UploadedFile uploadedFile,
            AiAvailabilityResponse availability) {
        aiLogService.createFeedbackRecord(
                user,
                AiFeatureKey.WRITING_FEEDBACK,
                language,
                inputSummary,
                AiAvailabilityService.WRITING_UNAVAILABLE_MESSAGE,
                AiRequestStatus.UNAVAILABLE,
                uploadedFile);

        aiLogService.createUsageLog(
                user,
                primaryRole(user),
                AiFeatureKey.WRITING_FEEDBACK,
                language,
                AiRequestStatus.UNAVAILABLE,
                availability.provider(),
                availability.model(),
                null,
                inputSummary,
                null,
                availability.available()
                        ? "Configured provider does not support image input."
                        : availability.message(),
                RELATED_ENTITY_UPLOADED_FILE,
                uploadedFile.getId());

        return new AiWritingFeedbackResponse(
                AiAvailabilityService.WRITING_UNAVAILABLE_MESSAGE,
                "Image analysis is not available with the current AI configuration.",
                "Write the character again slowly and compare it with the model character.",
                "Your teacher can review the saved image.",
                AiRequestStatus.UNAVAILABLE.name(),
                AiAvailabilityService.MEDIA_FEEDBACK_NOTICE);
    }

    private static AiWritingFeedbackResponse failedWritingFeedback(AiExecutionResult execution) {
        return new AiWritingFeedbackResponse(
                execution.userMessage(),
                null,
                "Write the character again slowly and compare it with the model character.",
                "Your teacher can review the saved image.",
                execution.status() == AiRequestStatus.UNAVAILABLE
                        ? AiRequestStatus.UNAVAILABLE.name()
                        : AiRequestStatus.FAILED.name(),
                AiAvailabilityService.MEDIA_FEEDBACK_NOTICE);
    }

    private AiWritingFeedbackResponse parseWritingResponse(Map<String, Object> map) {
        return new AiWritingFeedbackResponse(
                TextUtils.defaultIfBlank(text(map, "feedback"), "The AI could not describe this image clearly."),
                text(map, "clarityComment"),
                TextUtils.defaultIfBlank(text(map, "improvementTips"),
                        "Write the character again slowly and compare it with the model character."),
                TextUtils.defaultIfBlank(text(map, "encouragement"), "Keep practising one character at a time."),
                AiRequestStatus.SUCCESS.name(),
                AiAvailabilityService.MEDIA_FEEDBACK_NOTICE);
    }

    private void requireRole(SecurityUserDetails currentUser, RoleName roleName, String message) {
        if (!hasRole(currentUser, roleName)) {
            throw new ResponseStatusException(FORBIDDEN, message);
        }
    }

    private static boolean hasRole(SecurityUserDetails currentUser, RoleName roleName) {
        return currentUser != null
                && currentUser.getRoleNames().stream().anyMatch(role -> role.equals(roleName.name()));
    }

    private AiVocabularyHelperResponse unavailableVocabularyResponse(AiExecutionResult execution) {
        return new AiVocabularyHelperResponse(
                null,
                null,
                null,
                null,
                execution.userMessage(),
                null,
                null,
                "You can also check the word with your teacher.",
                execution.status().name());
    }

    private AiSentencePracticeResponse unavailableSentenceResponse(String originalSentence, AiExecutionResult execution) {
        return new AiSentencePracticeResponse(
                originalSentence,
                originalSentence,
                null,
                null,
                null,
                execution.userMessage(),
                "You can also ask your teacher to check the sentence.",
                execution.status().name());
    }

    private AiStoryGeneratorResponse fallbackStoryResponse(String topic, String usageMode, String status, String message) {
        return new AiStoryGeneratorResponse(
                topic,
                message,
                null,
                null,
                null,
                List.of(),
                List.of(),
                "Try again later or review a reading activity from your lessons.",
                usageMode,
                status);
    }

    private AiVocabularyHelperResponse parseVocabularyResponse(Map<String, Object> map, String status) {
        return new AiVocabularyHelperResponse(
                text(map, "chineseWord"),
                text(map, "pinyin"),
                text(map, "englishMeaning"),
                text(map, "bahasaMelayuMeaning"),
                TextUtils.defaultIfBlank(text(map, "simpleExplanation"), "This word is useful for beginner practice."),
                text(map, "exampleSentence"),
                text(map, "exampleSentenceTranslation"),
                TextUtils.defaultIfBlank(text(map, "encouragement"), "Try using the word in your own sentence."),
                status);
    }

    private AiSentencePracticeResponse parseSentenceResponse(String originalSentence, Map<String, Object> map, String status) {
        return new AiSentencePracticeResponse(
                originalSentence,
                TextUtils.defaultIfBlank(text(map, "correctedSentence"), originalSentence),
                text(map, "pinyin"),
                text(map, "englishMeaning"),
                text(map, "bahasaMelayuMeaning"),
                TextUtils.defaultIfBlank(text(map, "improvementExplanation"), "No major issue was found in this sentence."),
                TextUtils.defaultIfBlank(text(map, "encouragement"), "Keep practising with short sentences."),
                status);
    }

    private AiQuizGeneratorResponse parseQuizResponse(
            AiQuizGeneratorRequest request,
            String topic,
            String usageMode,
            Map<String, Object> map,
            String status) {
        // Only questions the learner can answer and check in the browser are kept (SDD 5.6, Table 5.6).
        List<AiGeneratedQuizQuestionResponse> questions = mapObjectList(
                map.get("questions"),
                MAX_QUIZ_QUESTIONS,
                question -> new AiGeneratedQuizQuestionResponse(
                        text(question, "questionText"),
                        TextUtils.defaultIfBlank(text(question, "questionType"), "MULTIPLE_CHOICE"),
                        boundedStringList(question.get("options"), MAX_AI_OPTION_ITEMS),
                        text(question, "correctAnswer"),
                        text(question, "explanation"),
                        text(question, "pinyin"),
                        text(question, "translation")));
        questions = questions.stream()
                .filter(question -> question.questionText() != null && question.correctAnswer() != null)
                .toList();
        if (questions.isEmpty()) {
            return new AiQuizGeneratorResponse(
                    "AI quiz unavailable",
                    topic,
                    sanitizePromptFragment(request.level()),
                    sanitizePromptFragment(request.category()),
                    usageMode,
                    List.of(),
                    AiRequestStatus.FAILED.name());
        }

        return new AiQuizGeneratorResponse(
                TextUtils.defaultIfBlank(text(map, "title"), topic + " Quiz"),
                topic,
                sanitizePromptFragment(request.level()),
                sanitizePromptFragment(request.category()),
                usageMode,
                questions,
                status);
    }

    private AiStoryGeneratorResponse parseStoryResponse(
            String topic,
            String usageMode,
            Map<String, Object> map,
            String status) {
        List<AiStoryVocabularyItemResponse> vocabularyList = mapObjectList(
                map.get("vocabularyList"),
                MAX_STORY_VOCABULARY_ITEMS,
                vocab -> new AiStoryVocabularyItemResponse(
                        text(vocab, "chineseWord"),
                        text(vocab, "pinyin"),
                        text(vocab, "englishMeaning"),
                        text(vocab, "bahasaMelayuMeaning")));
        List<AiStoryComprehensionQuestionResponse> comprehensionQuestions = mapObjectList(
                map.get("comprehensionQuestions"),
                MAX_STORY_COMPREHENSION_QUESTIONS,
                question -> new AiStoryComprehensionQuestionResponse(
                        text(question, "question"),
                        boundedStringList(question.get("options"), MAX_AI_OPTION_ITEMS),
                        text(question, "answer"),
                        text(question, "explanation")));

        return new AiStoryGeneratorResponse(
                TextUtils.defaultIfBlank(text(map, "title"), topic),
                TextUtils.defaultIfBlank(text(map, "chineseStory"), AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE),
                text(map, "pinyin"),
                text(map, "englishTranslation"),
                text(map, "bahasaMelayuTranslation"),
                vocabularyList,
                comprehensionQuestions,
                TextUtils.defaultIfBlank(text(map, "encouragement"), "Read it once more aloud to practise."),
                usageMode,
                status);
    }

    private ParsedAiJson parseJsonMap(String responseText) {
        try {
            Map<String, Object> values = objectMapper.readValue(responseText, MAP_TYPE);
            return values.isEmpty() ? ParsedAiJson.invalid() : ParsedAiJson.valid(values);
        } catch (JsonProcessingException exception) {
            log.warn("AI JSON parsing fallback triggered for invalid provider response.");
            return ParsedAiJson.invalid();
        }
    }

    private String text(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : AiInputSanitizer.sanitizeLong(String.valueOf(value));
    }

    private <T> List<T> mapObjectList(Object value, int maxItems, Function<Map<?, ?>, T> mapper) {
        if (!(value instanceof List<?> list) || maxItems <= 0) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .limit(maxItems)
                .map(map -> mapper.apply(map))
                .toList();
    }

    private List<String> boundedStringList(Object value, int maxItems) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .limit(Math.max(maxItems, 0))
                .map(item -> item == null ? null : sanitizePromptFragment(String.valueOf(item)))
                .filter(value1 -> value1 != null && !value1.isBlank())
                .toList();
    }

    private String normalizedPromptLanguage(String requestedLanguage, String fallbackLanguage) {
        String normalized = LanguageCodes.normalize(sanitizePromptFragment(requestedLanguage));
        return normalized == null ? fallbackLanguage : normalized;
    }

    private String sanitizePromptFragment(String value) {
        return AiInputSanitizer.sanitizeShort(value);
    }

    private String requireSanitizedShort(String value, String message) {
        String sanitized = AiInputSanitizer.sanitizeShort(value);
        if (sanitized == null) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        return sanitized;
    }

    private String requireSanitizedLong(String value, String message) {
        String sanitized = AiInputSanitizer.sanitizeLong(value);
        if (sanitized == null) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        return sanitized;
    }

    private int resolveNumberOfQuestions(Integer requested) {
        if (requested == null) {
            return DEFAULT_QUIZ_QUESTIONS;
        }
        if (requested < 1) {
            return 1;
        }
        if (requested > MAX_QUIZ_QUESTIONS) {
            return MAX_QUIZ_QUESTIONS;
        }
        return requested;
    }

    private String sanitizedQuestionTypes(List<String> questionTypes) {
        List<String> sanitized = questionTypes == null
                ? List.of()
                : questionTypes.stream()
                        .limit(MAX_QUIZ_QUESTIONS)
                        .map(AiInputSanitizer::sanitizeShort)
                        .filter(value -> value != null && !value.isBlank())
                        .toList();
        return sanitized.isEmpty() ? "MIXED_BEGINNER_TYPES" : String.join(", ", sanitized);
    }

    private String defaultLevel(String value) {
        return TextUtils.defaultIfBlank(AiInputSanitizer.sanitizeShort(value), "Beginner");
    }

    private String primaryRole(User user) {
        return user.getRoles().stream()
                .map(role -> role.getName().name())
                .sorted()
                .findFirst()
                .orElse("USER");
    }

    private record ParsedAiJson(boolean valid, Map<String, Object> values) {

        private static ParsedAiJson valid(Map<String, Object> values) {
            return new ParsedAiJson(true, Collections.unmodifiableMap(new LinkedHashMap<>(values)));
        }

        private static ParsedAiJson invalid() {
            return new ParsedAiJson(false, Map.of());
        }
    }

    private void recordProviderDuration(Timer.Sample sample, AiFeatureKey featureKey, String outcome) {
        sample.stop(Timer.builder("aichinese.ai.provider.duration")
                .description("Latency of text generation calls to the configured AI provider")
                .tag("feature", featureKey.name())
                .tag("outcome", outcome)
                .register(meterRegistry));
    }

    private AdminAiUsageLogResponse toAdminUsageLogResponse(AiUsageLog logRecord) {
        User user = logRecord.getUser();
        return new AdminAiUsageLogResponse(
                logRecord.getId(),
                user != null ? user.getId() : null,
                user != null ? user.getDisplayName() : null,
                user != null ? user.getEmail() : null,
                logRecord.getUserRole(),
                logRecord.getFeatureKey(),
                logRecord.getProviderName(),
                logRecord.getModelName(),
                logRecord.getStatus(),
                logRecord.getTokenCount(),
                logRecord.getLanguagePreference(),
                logRecord.getInputSummary(),
                logRecord.getOutputSummary(),
                logRecord.getErrorSummary(),
                logRecord.getRelatedEntityType(),
                logRecord.getRelatedEntityId(),
                logRecord.getCreatedAt());
    }
}
