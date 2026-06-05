package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.criteria.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
import com.example.aichinese.dto.ai.AiLearningRecommendationRequest;
import com.example.aichinese.dto.ai.AiLearningRecommendationResponse;
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
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiChatSenderRole;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AiChatMessageRepository;
import com.example.aichinese.repository.AiChatSessionRepository;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.AiExecutionResult;
import com.example.aichinese.service.ai.AiPromptBuilder;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiProviderResult;
import com.example.aichinese.service.ai.AiProviderRouter;
import com.example.aichinese.service.ai.AiUsageLimiter;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final int CHAT_HISTORY_LIMIT = 20;
    private static final int LEARNING_CONTEXT_ITEM_LIMIT = 10;
    private static final int MAX_QUIZ_QUESTIONS = 10;
    private static final int MAX_STORY_VOCABULARY_ITEMS = 20;
    private static final int MAX_STORY_COMPREHENSION_QUESTIONS = 10;
    private static final int MAX_RECOMMENDATION_PRACTICE_TYPES = 10;
    private static final int MAX_RECOMMENDATION_CONTEXT_ITEMS = 10;
    private static final int MAX_AI_OPTION_ITEMS = 8;
    private static final int DEFAULT_QUIZ_QUESTIONS = 5;
    private static final int MAX_RETAINED_AI_CHAT_RESPONSE_CHARS = 4000;

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final StudentLearningAccessService studentLearningAccessService;
    private final AiChatSessionRepository aiChatSessionRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiPromptBuilder aiPromptBuilder;
    private final AiProviderRouter aiProviderRouter;
    private final AiAvailabilityService aiAvailabilityService;
    private final AiLogService aiLogService;
    private final AiFileStorageService aiFileStorageService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate recommendationContextReadTransaction;
    private final MeterRegistry meterRegistry;
    private final AiUsageLimiter aiUsageLimiter;

    public AiService(
            UserRepository userRepository,
            StudentProfileRepository studentProfileRepository,
            QuizAttemptAnswerRepository quizAttemptAnswerRepository,
            LessonProgressRepository lessonProgressRepository,
            StudentLearningAccessService studentLearningAccessService,
            AiChatSessionRepository aiChatSessionRepository,
            AiChatMessageRepository aiChatMessageRepository,
            AiUsageLogRepository aiUsageLogRepository,
            AiPromptBuilder aiPromptBuilder,
            AiProviderRouter aiProviderRouter,
            AiAvailabilityService aiAvailabilityService,
            AiLogService aiLogService,
            AiFileStorageService aiFileStorageService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry,
            AiUsageLimiter aiUsageLimiter) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "studentProfileRepository");
        this.quizAttemptAnswerRepository = Objects.requireNonNull(quizAttemptAnswerRepository, "quizAttemptAnswerRepository");
        this.lessonProgressRepository = Objects.requireNonNull(lessonProgressRepository, "lessonProgressRepository");
        this.studentLearningAccessService = Objects.requireNonNull(studentLearningAccessService, "studentLearningAccessService");
        this.aiChatSessionRepository = Objects.requireNonNull(aiChatSessionRepository, "aiChatSessionRepository");
        this.aiChatMessageRepository = Objects.requireNonNull(aiChatMessageRepository, "aiChatMessageRepository");
        this.aiUsageLogRepository = Objects.requireNonNull(aiUsageLogRepository, "aiUsageLogRepository");
        this.aiPromptBuilder = Objects.requireNonNull(aiPromptBuilder, "aiPromptBuilder");
        this.aiProviderRouter = Objects.requireNonNull(aiProviderRouter, "aiProviderRouter");
        this.aiAvailabilityService = Objects.requireNonNull(aiAvailabilityService, "aiAvailabilityService");
        this.aiLogService = Objects.requireNonNull(aiLogService, "aiLogService");
        this.aiFileStorageService = Objects.requireNonNull(aiFileStorageService, "aiFileStorageService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.recommendationContextReadTransaction = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
        this.recommendationContextReadTransaction.setReadOnly(true);
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.aiUsageLimiter = Objects.requireNonNull(aiUsageLimiter, "aiUsageLimiter");
    }

    public AiChatResponse chat(SecurityUserDetails currentUser, AiChatRequest request) {
        User user = requireUser(currentUser);
        requireRole(currentUser, RoleName.STUDENT, "AI features require a student account.");
        String language = resolveLanguage(user, request.languagePreference());
        String sanitizedUserMessage = AiInputSanitizer.sanitizeLong(request.message());
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

        aiLogService.appendChatMessage(session, AiChatSenderRole.USER, sanitizedUserMessage);
        AiExecutionResult execution = generateText(
                user,
                AiFeatureKey.CHAT_TUTOR,
                language,
                buildChatPrompt(request, sanitizedUserMessage),
                false,
                history,
                "AiChatSession",
                session.getId());

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

        ParsedAiJson parsed = parseJsonMap(execution.responseText());
        if (!parsed.valid()) {
            return unavailableVocabularyResponse(unparseableProviderResponse(
                    execution,
                    "AI vocabulary helper response could not be parsed."));
        }
        return parseVocabularyResponse(parsed.values(), execution.status().name());
    }

    public AiSentencePracticeResponse sentencePractice(SecurityUserDetails currentUser, AiSentencePracticeRequest request) {
        User user = requireUser(currentUser);
        requireRole(currentUser, RoleName.STUDENT, "AI features require a student account.");
        String language = resolveLanguage(user, request.languagePreference());
        String sanitizedSentence = requireSanitizedLong(request.sentence(), "Sentence is required.");
        AiExecutionResult execution = generateText(
                user,
                AiFeatureKey.SENTENCE_PRACTICE,
                language,
                buildSentencePrompt(request, language),
                true,
                List.of(),
                null,
                null);

        if (execution.status() != AiRequestStatus.SUCCESS) {
            return unavailableSentenceResponse(sanitizedSentence, execution);
        }

        ParsedAiJson parsed = parseJsonMap(execution.responseText());
        if (!parsed.valid()) {
            return unavailableSentenceResponse(
                    sanitizedSentence,
                    unparseableProviderResponse(execution, "AI sentence practice response could not be parsed."));
        }
        return parseSentenceResponse(sanitizedSentence, parsed.values(), execution.status().name());
    }

    public AiQuizGeneratorResponse quizGenerator(SecurityUserDetails currentUser, AiQuizGeneratorRequest request) {
        User user = requireUser(currentUser);
        requireRole(currentUser, RoleName.STUDENT, "AI features require a student account.");
        String language = resolveLanguage(user, request.languagePreference());
        String sanitizedTopic = requireSanitizedShort(request.topic(), "Topic is required.");
        String usageMode = "TEMPORARY_SELF_PRACTICE";
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

        ParsedAiJson parsed = parseJsonMap(execution.responseText());
        if (!parsed.valid()) {
            return new AiQuizGeneratorResponse(
                    "AI quiz unavailable",
                    sanitizedTopic,
                    sanitizePromptFragment(request.level()),
                    sanitizePromptFragment(request.category()),
                    usageMode,
                    List.of(),
                    AiRequestStatus.FAILED.name());
        }
        return parseQuizResponse(request, usageMode, parsed.values(), execution.status().name());
    }

    public AiStoryGeneratorResponse storyGenerator(SecurityUserDetails currentUser, AiStoryGeneratorRequest request) {
        User user = requireUser(currentUser);
        requireRole(currentUser, RoleName.STUDENT, "AI features require a student account.");
        String language = resolveLanguage(user, request.languagePreference());
        requireSanitizedShort(request.topic(), "Topic is required.");
        String usageMode = "SELF_PRACTICE";
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
            return fallbackStoryResponse(request, usageMode, execution.status().name());
        }

        ParsedAiJson parsed = parseJsonMap(execution.responseText());
        if (!parsed.valid()) {
            return fallbackStoryResponse(request, usageMode, AiRequestStatus.FAILED.name());
        }
        return parseStoryResponse(request, usageMode, parsed.values(), execution.status().name());
    }

    public AiLearningRecommendationResponse learningRecommendation(
            SecurityUserDetails currentUser,
            AiLearningRecommendationRequest request) {
        requireRole(currentUser, RoleName.STUDENT, "AI features require a student account.");
        LearningRecommendationContext context = Objects.requireNonNull(
                recommendationContextReadTransaction.execute(status -> prepareLearningRecommendationContext(currentUser, request)),
                "learning recommendation context");

        AiExecutionResult execution = generateText(
                context.user(),
                AiFeatureKey.LEARNING_RECOMMENDATION,
                context.language(),
                buildLearningRecommendationPrompt(request, context.summary(), context.language()),
                true,
                List.of(),
                "StudentProfile",
                context.studentProfileId());

        if (execution.status() != AiRequestStatus.SUCCESS) {
            return fallbackLearningRecommendation(execution.status().name());
        }

        ParsedAiJson parsed = parseJsonMap(execution.responseText());
        if (!parsed.valid()) {
            return fallbackLearningRecommendation(AiRequestStatus.FAILED.name());
        }
        return parseLearningRecommendation(parsed.values(), execution.status().name());
    }

    public AiPronunciationFeedbackResponse pronunciationFeedback(
            SecurityUserDetails currentUser,
            MultipartFile audioFile,
            String targetText,
            String pinyin,
            String tone,
            String languagePreference) {
        User user = requireUser(currentUser);
        requireRole(currentUser, RoleName.STUDENT, "Pronunciation feedback requires a student account.");
        if (targetText == null || targetText.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Target text is required.");
        }

        UploadedFile uploadedFile = aiFileStorageService.storePronunciationAudio(audioFile, user);
        String language = resolveLanguage(user, languagePreference);
        String inputSummary = "Target text: " + targetText.trim() + "; Pinyin: " + TextUtils.trimToNull(pinyin) + "; Tone: " + TextUtils.trimToNull(tone);

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
                summarize(inputSummary, 500),
                null,
                "Selected provider does not support audio analysis in this phase.",
                "UploadedFile",
                uploadedFile.getId());

        return new AiPronunciationFeedbackResponse(
                AiAvailabilityService.PRONUNCIATION_UNAVAILABLE_MESSAGE,
                "Audio analysis is not available in the current provider configuration.",
                "Tone feedback is not available with the current provider configuration.",
                "Keep practising slowly and repeat the target phrase clearly.",
                "You are doing a great job. Keep trying!",
                AiRequestStatus.UNAVAILABLE.name());
    }

    public AiWritingFeedbackResponse writingFeedback(
            SecurityUserDetails currentUser,
            MultipartFile imageFile,
            String targetCharacter,
            String languagePreference) {
        User user = requireUser(currentUser);
        requireRole(currentUser, RoleName.STUDENT, "Writing feedback requires a student account.");
        if (targetCharacter == null || targetCharacter.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Target character is required.");
        }

        UploadedFile uploadedFile = aiFileStorageService.storeWritingImage(imageFile, user);
        String language = resolveLanguage(user, languagePreference);
        String inputSummary = "Target character: " + targetCharacter.trim();

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
                aiAvailabilityService.normalizedProvider(),
                null,
                null,
                summarize(inputSummary, 500),
                null,
                "Selected provider does not support image analysis in this phase.",
                "UploadedFile",
                uploadedFile.getId());

        return new AiWritingFeedbackResponse(
                AiAvailabilityService.WRITING_UNAVAILABLE_MESSAGE,
                "Image analysis is not available in the current provider configuration.",
                "Try writing the character again slowly and compare it with the example.",
                "Nice effort. Keep practising one character at a time!",
                AiRequestStatus.UNAVAILABLE.name());
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

        Specification<AiUsageLog> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (featureType != null && !featureType.isBlank()) {
                predicates.add(cb.equal(root.get("featureKey"), featureType.trim().toUpperCase(Locale.ROOT)));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase(Locale.ROOT)));
            }
            if (userRole != null && !userRole.isBlank()) {
                predicates.add(cb.equal(root.get("userRole"), userRole.trim().toUpperCase(Locale.ROOT)));
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

    @Transactional(readOnly = true)
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
        String userRole = primaryRole(user);
        String inputSummary = summarize(userPrompt, 500);
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

        aiUsageLimiter.checkProviderCallAllowed(user.getId());
        AiProviderPrompt prompt = aiPromptBuilder.buildPrompt(featureKey, language, userPrompt, expectJson, history);
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
                    "Provider request failed or timed out.",
                    relatedEntityType,
                    relatedEntityId);
            return AiExecutionResult.failed(
                    availability.provider(),
                    availability.model(),
                    AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE,
                    "Provider request failed or timed out.");
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
                    "Provider response was not valid JSON.",
                    relatedEntityType,
                    relatedEntityId);
            return AiExecutionResult.failed(
                    result.providerName(),
                    result.modelName(),
                    AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE,
                    "Provider response was not valid JSON.");
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
                summarize(result.text(), 500),
                null,
                relatedEntityType,
                relatedEntityId);
        return AiExecutionResult.success(result.providerName(), result.modelName(), result.text(), result.tokenCount());
    }

    private User requireUser(SecurityUserDetails currentUser) {
        Long userId = currentUser.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(NOT_FOUND, "Authenticated user could not be found.");
        }

        return userRepository.findByIdIn(List.of(userId)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Authenticated user could not be found."));
    }

    private String resolveLanguage(User user, String requestedLanguage) {
        if (requestedLanguage != null && !requestedLanguage.isBlank()) {
            return normalizeLanguage(requestedLanguage);
        }
        if (user.getPreferredLanguage() != null && !user.getPreferredLanguage().isBlank()) {
            return normalizeLanguage(user.getPreferredLanguage());
        }
        return "en";
    }

    private String normalizeLanguage(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("ms".equals(normalized) || normalized.startsWith("ms-") || normalized.startsWith("ms_")) {
            return "ms";
        }
        if ("zh".equals(normalized) || normalized.startsWith("zh-") || normalized.startsWith("zh_")) {
            return "zh";
        }
        return "en";
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
        builder.append("Help the learner with this message: ")
                .append(sanitizedUserMessage)
                .append(".");
        String sanitizedContext = AiInputSanitizer.sanitizeLong(request.context());
        if (sanitizedContext != null) {
            builder.append(" Context: ").append(sanitizedContext).append(".");
        }
        if (request.relatedLessonId() != null) {
            builder.append(" Related lesson id: ").append(request.relatedLessonId()).append(".");
        }
        builder.append(" Keep the answer child-friendly and concise.");
        return builder.toString();
    }

    private String sanitizedAssistantMessage(String responseText) {
        String sanitized = AiInputSanitizer.sanitize(responseText, MAX_RETAINED_AI_CHAT_RESPONSE_CHARS);
        return sanitized == null ? AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE : sanitized;
    }

    private String buildVocabularyPrompt(AiVocabularyHelperRequest request, String language) {
        String outputLanguage = normalizedPromptLanguage(request.outputLanguage(), language);
        return "Create a beginner-friendly vocabulary helper in structured JSON for the word \""
                + AiInputSanitizer.sanitizeShort(request.word())
                + "\". Input language: " + AiInputSanitizer.sanitizeShort(request.inputLanguage())
                + ". Output language preference: " + outputLanguage
                + ". Learner level: " + defaultLevel(request.learnerLevel())
                + ". Return keys: chineseWord, pinyin, englishMeaning, bahasaMelayuMeaning, simpleExplanation, exampleSentence, exampleSentencePinyin, exampleSentenceTranslation, imagePromptOrDescription, miniPracticeQuestion, encouragement.";
    }

    private String buildSentencePrompt(AiSentencePracticeRequest request, String language) {
        return "Check this beginner Chinese learning sentence and return structured JSON: \""
                + AiInputSanitizer.sanitizeLong(request.sentence())
                + "\". Learner level: " + defaultLevel(request.learnerLevel())
                + ". Language preference: " + language
                + ". Return keys: correctedSentence, pinyin, englishMeaning, bahasaMelayuMeaning, improvementExplanation, naturalSentenceVersion, practiceTask, encouragement.";
    }

    private String buildQuizPrompt(AiQuizGeneratorRequest request, String language, String usageMode) {
        String targetRoleContext = sanitizePromptFragment(request.targetRoleContext());
        return "Generate a child-friendly Chinese learning quiz draft in JSON. Topic: "
                + AiInputSanitizer.sanitizeShort(request.topic())
                + ". Level: " + AiInputSanitizer.sanitizeShort(request.level())
                + ". Category: " + AiInputSanitizer.sanitizeShort(request.category())
                + ". Number of questions: " + resolveNumberOfQuestions(request.numberOfQuestions())
                + ". Question types: " + sanitizedQuestionTypes(request.questionTypes())
                + (targetRoleContext == null ? "" : ". Target role context: " + targetRoleContext)
                + ". Usage mode: " + usageMode
                + ". Language preference: " + language
                + ". Return keys: title, questions. Each question should include questionText, questionType, options, correctAnswer, explanation, pinyin, translation.";
    }

    private String buildStoryPrompt(AiStoryGeneratorRequest request, String language, String usageMode) {
        return "Generate a short, child-friendly beginner Chinese story in JSON. Topic: "
                + AiInputSanitizer.sanitizeShort(request.topic())
                + ". Level: " + defaultLevel(request.level())
                + ". Length preference: " + AiInputSanitizer.sanitizeShort(request.lengthPreference())
                + ". Usage mode: " + usageMode
                + ". Language preference: " + language
                + ". Return keys: title, chineseStory, pinyin, englishTranslation, bahasaMelayuTranslation, vocabularyList, comprehensionQuestions, encouragement.";
    }

    private String buildLearningRecommendationPrompt(AiLearningRecommendationRequest request, String summary, String language) {
        String recentQuizResults = joinPromptList("Recent quiz results", request.recentQuizResults(), MAX_RECOMMENDATION_CONTEXT_ITEMS);
        String wrongAnswers = joinPromptList("Wrong answers", request.wrongAnswers(), MAX_RECOMMENDATION_CONTEXT_ITEMS);
        String weakAreas = joinPromptList("Weak areas", request.weakAreas(), MAX_RECOMMENDATION_CONTEXT_ITEMS);
        String completedLessons = joinPromptList("Completed lessons", request.completedLessons(), MAX_RECOMMENDATION_CONTEXT_ITEMS);
        return "Based on this student learning summary, return a short structured learning recommendation in JSON. Summary: "
                + summary
                + ". Current level override: " + AiInputSanitizer.sanitizeShort(request.currentLevel())
                + (recentQuizResults == null ? "" : ". " + recentQuizResults)
                + (wrongAnswers == null ? "" : ". " + wrongAnswers)
                + (weakAreas == null ? "" : ". " + weakAreas)
                + (completedLessons == null ? "" : ". " + completedLessons)
                + ". Language preference: " + language
                + ". Return keys: recommendedNextStep, recommendedLessonOrActivity, weakAreaSummary, revisionSuggestion, shortEncouragement, suggestedPracticeTypes.";
    }

    private StudentProfile resolveCurrentStudentProfile(SecurityUserDetails currentUser) {
        return studentProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student profile could not be found."));
    }

    private LearningRecommendationContext prepareLearningRecommendationContext(
            SecurityUserDetails currentUser,
            AiLearningRecommendationRequest request) {
        User user = requireUser(currentUser);
        String language = resolveLanguage(user, request.languagePreference());
        StudentProfile studentProfile = resolveCurrentStudentProfile(currentUser);
        if (request.studentId() != null && !studentProfile.getId().equals(request.studentId())) {
            throw new ResponseStatusException(FORBIDDEN, "Students can only request learning recommendations for their own account.");
        }

        String summary = buildStudentContextSummary(studentProfile);
        return new LearningRecommendationContext(user, studentProfile.getId(), language, summary);
    }

    private void requireRole(SecurityUserDetails currentUser, RoleName roleName, String message) {
        if (!hasRole(currentUser, roleName)) {
            throw new ResponseStatusException(FORBIDDEN, message);
        }
    }

    private boolean hasRole(SecurityUserDetails currentUser, RoleName roleName) {
        return currentUser.getRoleNames().stream().anyMatch(role -> role.equals(roleName.name()));
    }

    private String buildStudentContextSummary(StudentProfile studentProfile) {
        List<LessonProgress> accessibleLessons = lessonProgressRepository.findByStudentIdOrderByUpdatedAtDesc(studentProfile.getId()).stream()
                .filter(progress -> hasCurrentLessonProgressAccess(studentProfile, progress))
                .toList();
        List<QuizAttemptAnswer> wrongAnswers = quizAttemptAnswerRepository
                .findByQuizAttemptStudentIdAndCorrectFalseOrderByCreatedAtDesc(studentProfile.getId()).stream()
                .filter(answer -> hasCurrentWrongAnswerAccess(studentProfile, answer))
                .limit(LEARNING_CONTEXT_ITEM_LIMIT)
                .toList();

        String level = studentProfile.getCurrentLevel() != null ? studentProfile.getCurrentLevel().getName() : "Beginner";
        int totalPoints = studentProfile.getTotalPoints() == null ? 0 : studentProfile.getTotalPoints();
        int completion = overallCompletionPercent(accessibleLessons);
        String weakAreas = String.join(", ", wrongAnswers.stream()
                .map(answer -> answer.getQuestion() == null ? null : answer.getQuestion().getQuestionType())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(5)
                .toList());

        return "Student level: " + level
                + ". Total points: " + totalPoints
                + ". Completion percent: " + completion
                + ". Weak areas: " + weakAreas
                + ". Recent lessons: " + accessibleLessons.stream().limit(3).map(progress -> progress.getLesson().getTitle()).toList()
                + ". Recent wrong answers: " + wrongAnswers.stream().limit(3).map(answer -> answer.getQuestion().getQuestionType()).toList() + ".";
    }

    private boolean hasCurrentLessonProgressAccess(StudentProfile student, LessonProgress progress) {
        return progress.getLesson() == null || studentLearningAccessService.hasLessonAccess(student, progress.getLesson());
    }

    private boolean hasCurrentWrongAnswerAccess(StudentProfile student, QuizAttemptAnswer answer) {
        return answer.getQuestion() != null
                && answer.getQuestion().getQuiz() != null
                && answer.getQuestion().getQuiz().getLesson() != null
                && studentLearningAccessService.hasLessonAccess(student, answer.getQuestion().getQuiz().getLesson());
    }

    private int overallCompletionPercent(List<LessonProgress> lessonProgressList) {
        if (lessonProgressList.isEmpty()) {
            return 0;
        }
        long completedLessons = lessonProgressList.stream().filter(LessonProgress::isCompleted).count();
        return (int) Math.round((completedLessons * 100.0) / lessonProgressList.size());
    }

    private AiVocabularyHelperResponse unavailableVocabularyResponse(AiExecutionResult execution) {
        return new AiVocabularyHelperResponse(
                null,
                null,
                null,
                null,
                AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE,
                null,
                null,
                null,
                null,
                "Try again later with a simple Chinese word or phrase.",
                "You are doing a great job. Keep learning!",
                execution.status().name());
    }

    private AiSentencePracticeResponse unavailableSentenceResponse(String originalSentence, AiExecutionResult execution) {
        return new AiSentencePracticeResponse(
                originalSentence,
                originalSentence,
                null,
                null,
                null,
                AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE,
                originalSentence,
                "Try writing one short sentence again later.",
                "Nice try. Keep practising one sentence at a time!",
                execution.status().name());
    }

    private AiStoryGeneratorResponse fallbackStoryResponse(
            AiStoryGeneratorRequest request,
            String usageMode,
            String status) {
        return new AiStoryGeneratorResponse(
                requireSanitizedShort(request.topic(), "Topic is required."),
                AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE,
                null,
                null,
                null,
                List.of(),
                List.of(),
                "Keep reading and try again later.",
                usageMode,
                status);
    }

    private AiLearningRecommendationResponse fallbackLearningRecommendation(String status) {
        return new AiLearningRecommendationResponse(
                AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE,
                null,
                "AI insight is not available right now, so no personalized recommendation was generated.",
                "Please try again later when AI helper is available.",
                "Keep going. Small steps help you improve every day!",
                List.of(),
                status);
    }

    private AiExecutionResult unparseableProviderResponse(AiExecutionResult execution, String errorSummary) {
        return AiExecutionResult.failed(
                execution.providerName(),
                execution.modelName(),
                AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE,
                errorSummary);
    }

    private AiVocabularyHelperResponse parseVocabularyResponse(Map<String, Object> map, String status) {
        return new AiVocabularyHelperResponse(
                text(map, "chineseWord"),
                text(map, "pinyin"),
                text(map, "englishMeaning"),
                text(map, "bahasaMelayuMeaning"),
                TextUtils.defaultIfBlank(text(map, "simpleExplanation"), "This word is useful for beginner practice."),
                text(map, "exampleSentence"),
                text(map, "exampleSentencePinyin"),
                text(map, "exampleSentenceTranslation"),
                text(map, "imagePromptOrDescription"),
                text(map, "miniPracticeQuestion"),
                TextUtils.defaultIfBlank(text(map, "encouragement"), "Great job learning new words!"),
                status);
    }

    private AiSentencePracticeResponse parseSentenceResponse(String originalSentence, Map<String, Object> map, String status) {
        return new AiSentencePracticeResponse(
                originalSentence,
                TextUtils.defaultIfBlank(text(map, "correctedSentence"), originalSentence),
                text(map, "pinyin"),
                text(map, "englishMeaning"),
                text(map, "bahasaMelayuMeaning"),
                TextUtils.defaultIfBlank(text(map, "improvementExplanation"), "Your sentence is a good try. Keep practising."),
                TextUtils.defaultIfBlank(text(map, "naturalSentenceVersion"), originalSentence),
                TextUtils.defaultIfBlank(text(map, "practiceTask"), "Write one more short sentence about the same topic."),
                TextUtils.defaultIfBlank(text(map, "encouragement"), "Good effort. Keep practising!"),
                status);
    }

    private AiQuizGeneratorResponse parseQuizResponse(
            AiQuizGeneratorRequest request,
            String usageMode,
            Map<String, Object> map,
            String status) {
        List<AiGeneratedQuizQuestionResponse> questions = new ArrayList<>();
        Object questionsObject = map.get("questions");
        if (questionsObject instanceof List<?> list) {
            for (Object item : list) {
                if (questions.size() >= MAX_QUIZ_QUESTIONS) {
                    break;
                }
                if (item instanceof Map<?, ?> question) {
                    questions.add(new AiGeneratedQuizQuestionResponse(
                            text(question, "questionText"),
                            TextUtils.defaultIfBlank(text(question, "questionType"), "MULTIPLE_CHOICE"),
                            boundedStringList(question.get("options"), MAX_AI_OPTION_ITEMS),
                            text(question, "correctAnswer"),
                            text(question, "explanation"),
                            text(question, "pinyin"),
                            text(question, "translation")));
                }
            }
        }
        if (questions.isEmpty()) {
            return new AiQuizGeneratorResponse(
                    "AI quiz unavailable",
                    requireSanitizedShort(request.topic(), "Topic is required."),
                    sanitizePromptFragment(request.level()),
                    sanitizePromptFragment(request.category()),
                    usageMode,
                    List.of(),
                    AiRequestStatus.FAILED.name());
        }

        return new AiQuizGeneratorResponse(
                TextUtils.defaultIfBlank(text(map, "title"), requireSanitizedShort(request.topic(), "Topic is required.") + " Quiz"),
                requireSanitizedShort(request.topic(), "Topic is required."),
                sanitizePromptFragment(request.level()),
                sanitizePromptFragment(request.category()),
                usageMode,
                questions,
                status);
    }

    private AiStoryGeneratorResponse parseStoryResponse(
            AiStoryGeneratorRequest request,
            String usageMode,
            Map<String, Object> map,
            String status) {
        List<AiStoryVocabularyItemResponse> vocabularyList = new ArrayList<>();
        Object vocabObject = map.get("vocabularyList");
        if (vocabObject instanceof List<?> list) {
            for (Object item : list) {
                if (vocabularyList.size() >= MAX_STORY_VOCABULARY_ITEMS) {
                    break;
                }
                if (item instanceof Map<?, ?> vocab) {
                    vocabularyList.add(new AiStoryVocabularyItemResponse(
                            text(vocab, "chineseWord"),
                            text(vocab, "pinyin"),
                            text(vocab, "englishMeaning"),
                            text(vocab, "bahasaMelayuMeaning")));
                }
            }
        }

        List<AiStoryComprehensionQuestionResponse> comprehensionQuestions = new ArrayList<>();
        Object questionObject = map.get("comprehensionQuestions");
        if (questionObject instanceof List<?> list) {
            for (Object item : list) {
                if (comprehensionQuestions.size() >= MAX_STORY_COMPREHENSION_QUESTIONS) {
                    break;
                }
                if (item instanceof Map<?, ?> question) {
                    comprehensionQuestions.add(new AiStoryComprehensionQuestionResponse(
                            text(question, "question"),
                            boundedStringList(question.get("options"), MAX_AI_OPTION_ITEMS),
                            text(question, "answer"),
                            text(question, "explanation")));
                }
            }
        }

        return new AiStoryGeneratorResponse(
                TextUtils.defaultIfBlank(text(map, "title"), requireSanitizedShort(request.topic(), "Topic is required.")),
                TextUtils.defaultIfBlank(text(map, "chineseStory"), AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE),
                text(map, "pinyin"),
                text(map, "englishTranslation"),
                text(map, "bahasaMelayuTranslation"),
                vocabularyList,
                comprehensionQuestions,
                TextUtils.defaultIfBlank(text(map, "encouragement"), "Great reading practice!"),
                usageMode,
                status);
    }

    private AiLearningRecommendationResponse parseLearningRecommendation(
            Map<String, Object> map,
            String status) {
        List<String> practiceTypes = boundedStringList(map.get("suggestedPracticeTypes"), MAX_RECOMMENDATION_PRACTICE_TYPES);
        return new AiLearningRecommendationResponse(
                TextUtils.defaultIfBlank(text(map, "recommendedNextStep"), "Continue with a short lesson and one revision activity."),
                TextUtils.defaultIfBlank(text(map, "recommendedLessonOrActivity"), "Vocabulary review"),
                TextUtils.defaultIfBlank(text(map, "weakAreaSummary"), "Keep reviewing any difficult words slowly."),
                TextUtils.defaultIfBlank(text(map, "revisionSuggestion"), "Repeat one short activity from your recent lesson."),
                TextUtils.defaultIfBlank(text(map, "shortEncouragement"), "You are improving with each practice session!"),
                practiceTypes.isEmpty()
                        ? List.of("flashcards", "quiz", "story")
                        : practiceTypes,
                status);
    }

    private ParsedAiJson parseJsonMap(String responseText) {
        try {
            Map<String, Object> values = objectMapper.readValue(responseText, new TypeReference<Map<String, Object>>() {
            });
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
        String sanitized = sanitizePromptFragment(requestedLanguage);
        return sanitized == null ? fallbackLanguage : normalizeLanguage(sanitized);
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

    private String joinPromptList(String label, List<String> values, int maxItems) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        Set<String> sanitized = new LinkedHashSet<>();
        for (String value : values) {
            if (sanitized.size() >= maxItems) {
                break;
            }
            String item = sanitizePromptFragment(value);
            if (item != null && !item.isBlank()) {
                sanitized.add(item);
            }
        }

        if (sanitized.isEmpty()) {
            return null;
        }

        return label + ": " + String.join(", ", sanitized);
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

    private record LearningRecommendationContext(
            User user,
            Long studentProfileId,
            String language,
            String summary) {
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

    private String summarize(String value, int limit) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() <= limit) {
            return trimmed;
        }
        int end = limit;
        if (Character.isHighSurrogate(trimmed.charAt(end - 1))) {
            end--;
        }
        return trimmed.substring(0, end);
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
