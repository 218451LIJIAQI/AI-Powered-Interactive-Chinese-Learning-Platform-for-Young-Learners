package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.ai.AdminAiUsageLogResponse;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiChatResponse;
import com.example.aichinese.dto.ai.AiGeneratedQuizQuestionResponse;
import com.example.aichinese.dto.ai.AiLearningRecommendationRequest;
import com.example.aichinese.dto.ai.AiLearningRecommendationResponse;
import com.example.aichinese.dto.ai.AiProgressInsightRequest;
import com.example.aichinese.dto.ai.AiProgressInsightResponse;
import com.example.aichinese.dto.ai.AiPronunciationFeedbackResponse;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorResponse;
import com.example.aichinese.dto.ai.AiSentencePracticeRequest;
import com.example.aichinese.dto.ai.AiSentencePracticeResponse;
import com.example.aichinese.dto.ai.AiStoryComprehensionQuestionResponse;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorResponse;
import com.example.aichinese.dto.ai.AiStoryVocabularyItemResponse;
import com.example.aichinese.dto.ai.AiTeacherAssistantRequest;
import com.example.aichinese.dto.ai.AiTeacherAssistantResponse;
import com.example.aichinese.dto.ai.AiVocabularyEntryResponse;
import com.example.aichinese.dto.ai.AiVocabularyHelperRequest;
import com.example.aichinese.dto.ai.AiVocabularyHelperResponse;
import com.example.aichinese.dto.ai.AiWritingFeedbackResponse;
import com.example.aichinese.entity.AiChatMessage;
import com.example.aichinese.entity.AiChatSession;
import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.StudentProgress;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.AiChatMessageRepository;
import com.example.aichinese.repository.AiChatSessionRepository;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.StudentProgressRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.AiExecutionResult;
import com.example.aichinese.service.ai.AiPromptBuilder;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiProviderRouter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final UserRepository userRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final AiChatSessionRepository aiChatSessionRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiPromptBuilder aiPromptBuilder;
    private final AiProviderRouter aiProviderRouter;
    private final AiAvailabilityService aiAvailabilityService;
    private final AiLogService aiLogService;
    private final AiFileStorageService aiFileStorageService;
    private final ObjectMapper objectMapper;

    public AiService(
            UserRepository userRepository,
            TeacherProfileRepository teacherProfileRepository,
            StudentProfileRepository studentProfileRepository,
            StudentProgressRepository studentProgressRepository,
            QuizAttemptAnswerRepository quizAttemptAnswerRepository,
            LessonProgressRepository lessonProgressRepository,
            AiChatSessionRepository aiChatSessionRepository,
            AiChatMessageRepository aiChatMessageRepository,
            AiUsageLogRepository aiUsageLogRepository,
            AiPromptBuilder aiPromptBuilder,
            AiProviderRouter aiProviderRouter,
            AiAvailabilityService aiAvailabilityService,
            AiLogService aiLogService,
            AiFileStorageService aiFileStorageService,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.studentProgressRepository = studentProgressRepository;
        this.quizAttemptAnswerRepository = quizAttemptAnswerRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.aiChatSessionRepository = aiChatSessionRepository;
        this.aiChatMessageRepository = aiChatMessageRepository;
        this.aiUsageLogRepository = aiUsageLogRepository;
        this.aiPromptBuilder = aiPromptBuilder;
        this.aiProviderRouter = aiProviderRouter;
        this.aiAvailabilityService = aiAvailabilityService;
        this.aiLogService = aiLogService;
        this.aiFileStorageService = aiFileStorageService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiChatResponse chat(SecurityUserDetails currentUser, AiChatRequest request) {
        User user = requireUser(currentUser);
        String language = resolveLanguage(user, request.languagePreference());
        AiChatSession session = resolveChatSession(user, request);
        List<AiProviderPrompt.AiMessage> history = aiChatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(this::toAiMessage)
                .toList();

        aiLogService.appendChatMessage(session, "USER", request.message().trim());
        AiExecutionResult execution = generateText(
                user,
                AiFeatureKey.CHAT_TUTOR,
                language,
                buildChatPrompt(request),
                false,
                history,
                "AiChatSession",
                session.getId());

        String responseText = execution.status() == AiRequestStatus.SUCCESS
                ? execution.responseText()
                : execution.userMessage();

        aiLogService.appendChatMessage(session, "ASSISTANT", responseText);
        return new AiChatResponse(
                session.getId(),
                request.message().trim(),
                responseText,
                language,
                LocalDateTime.now(),
                execution.status().name());
    }

    @Transactional
    public AiVocabularyHelperResponse vocabularyHelper(SecurityUserDetails currentUser, AiVocabularyHelperRequest request) {
        User user = requireUser(currentUser);
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

        return parseVocabularyResponse(execution.responseText(), execution.status().name());
    }

    @Transactional
    public AiSentencePracticeResponse sentencePractice(SecurityUserDetails currentUser, AiSentencePracticeRequest request) {
        User user = requireUser(currentUser);
        String language = resolveLanguage(user, request.languagePreference());
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
            return unavailableSentenceResponse(request.sentence(), execution);
        }

        return parseSentenceResponse(request.sentence(), execution.responseText(), execution.status().name());
    }

    @Transactional
    public AiQuizGeneratorResponse quizGenerator(SecurityUserDetails currentUser, AiQuizGeneratorRequest request) {
        User user = requireUser(currentUser);
        String language = resolveLanguage(user, request.languagePreference());
        String usageMode = resolveQuizUsageMode(currentUser);
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
                    "Practice Quiz",
                    request.topic().trim(),
                    nullableText(request.level()),
                    nullableText(request.category()),
                    usageMode,
                    fallbackQuizQuestions(request.topic()),
                    execution.status().name());
        }

        return parseQuizResponse(request, usageMode, execution.responseText(), execution.status().name());
    }

    @Transactional
    public AiStoryGeneratorResponse storyGenerator(SecurityUserDetails currentUser, AiStoryGeneratorRequest request) {
        User user = requireUser(currentUser);
        String language = resolveLanguage(user, request.languagePreference());
        String usageMode = resolveStoryUsageMode(currentUser);
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

        return parseStoryResponse(request, usageMode, execution.responseText(), execution.status().name());
    }

    @Transactional
    public AiLearningRecommendationResponse learningRecommendation(
            SecurityUserDetails currentUser,
            AiLearningRecommendationRequest request) {
        User user = requireUser(currentUser);
        String language = resolveLanguage(user, request.languagePreference());
        StudentProfile studentProfile = null;
        String relatedEntityType = null;
        Long relatedEntityId = null;
        String summary;

        if (hasRole(currentUser, RoleName.STUDENT)) {
            studentProfile = resolveCurrentStudentProfile(currentUser);
            if (request.studentId() != null && !studentProfile.getId().equals(request.studentId())) {
                throw new ResponseStatusException(FORBIDDEN, "Students can only request learning recommendations for their own account.");
            }
            summary = buildStudentContextSummary(studentProfile);
            relatedEntityType = "StudentProfile";
            relatedEntityId = studentProfile.getId();
        } else {
            summary = buildSafeRecommendationSummary(request);
        }

        AiExecutionResult execution = generateText(
                user,
                AiFeatureKey.LEARNING_RECOMMENDATION,
                language,
                buildLearningRecommendationPrompt(request, summary, language),
                true,
                List.of(),
                relatedEntityType,
                relatedEntityId);

        if (execution.status() != AiRequestStatus.SUCCESS) {
            return studentProfile != null
                    ? fallbackLearningRecommendation(studentProfile, execution.status().name())
                    : fallbackGenericLearningRecommendation(request.currentLevel(), execution.status().name());
        }

        return parseLearningRecommendation(execution.responseText(), execution.status().name());
    }

    @Transactional
    public AiProgressInsightResponse progressInsight(SecurityUserDetails currentUser, AiProgressInsightRequest request) {
        User user = requireUser(currentUser);
        String language = resolveLanguage(user, request.languagePreference());
        String audience = request.audience().trim().toUpperCase(Locale.ROOT);
        validateAudienceForRole(currentUser, audience);
        String progressSummary = resolveProgressSummary(currentUser, request);

        AiExecutionResult execution = generateText(
                user,
                AiFeatureKey.PROGRESS_INSIGHT,
                language,
                buildProgressInsightPrompt(request, progressSummary, language),
                true,
                List.of(),
                null,
                null);

        if (execution.status() != AiRequestStatus.SUCCESS) {
            return fallbackProgressInsight(progressSummary, audience, execution.status().name());
        }

        return parseProgressInsight(execution.responseText(), audience, execution.status().name());
    }

    @Transactional
    public AiTeacherAssistantResponse teacherAssistant(SecurityUserDetails currentUser, AiTeacherAssistantRequest request) {
        User user = requireUser(currentUser);
        requireTeacherAssistantAccess(currentUser);
        String language = resolveLanguage(user, request.languagePreference());
        AiExecutionResult execution = generateText(
                user,
                AiFeatureKey.TEACHER_ASSISTANT,
                language,
                buildTeacherAssistantPrompt(request, language),
                true,
                List.of(),
                null,
                null);

        if (execution.status() != AiRequestStatus.SUCCESS) {
            return fallbackTeacherAssistant(request.topic(), execution.status().name());
        }

        return parseTeacherAssistant(execution.responseText(), execution.status().name());
    }

    @Transactional
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
        String inputSummary = "Target text: " + targetText.trim() + "; Pinyin: " + nullableText(pinyin) + "; Tone: " + nullableText(tone);

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
                "Tone feedback is not available in this prototype workflow.",
                "Keep practising slowly and repeat the target phrase clearly.",
                "You are doing a great job. Keep trying!",
                AiRequestStatus.UNAVAILABLE.name());
    }

    @Transactional
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
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        org.springframework.data.jpa.domain.Specification<AiUsageLog> specification = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

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

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };

        org.springframework.data.domain.Page<AiUsageLog> result = aiUsageLogRepository.findAll(specification, pageable);
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
        AiAvailabilityResponse availability = aiAvailabilityService.getAvailability();
        if (!availability.available()) {
            aiLogService.createGenerationRecord(
                    user,
                    featureKey,
                    language,
                    userPrompt,
                    availability.message(),
                    AiRequestStatus.UNAVAILABLE,
                    availability.provider(),
                    availability.model(),
                    relatedEntityType,
                    relatedEntityId);
            aiLogService.createUsageLog(
                    user,
                    primaryRole(user),
                    featureKey,
                    language,
                    AiRequestStatus.UNAVAILABLE,
                    availability.provider(),
                    availability.model(),
                    null,
                    summarize(userPrompt, 500),
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

        AiProviderPrompt prompt = aiPromptBuilder.buildPrompt(featureKey, language, userPrompt, expectJson, history);
        var result = aiProviderRouter.resolveClient().generateText(prompt);
        if (!result.success()) {
            aiLogService.createGenerationRecord(
                    user,
                    featureKey,
                    language,
                    userPrompt,
                    result.errorSummary(),
                    AiRequestStatus.FAILED,
                    result.providerName(),
                    result.modelName(),
                    relatedEntityType,
                    relatedEntityId);
            aiLogService.createUsageLog(
                    user,
                    primaryRole(user),
                    featureKey,
                    language,
                    AiRequestStatus.FAILED,
                    result.providerName(),
                    result.modelName(),
                    null,
                    summarize(userPrompt, 500),
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

        aiLogService.createGenerationRecord(
                user,
                featureKey,
                language,
                userPrompt,
                result.text(),
                AiRequestStatus.SUCCESS,
                result.providerName(),
                result.modelName(),
                relatedEntityType,
                relatedEntityId);
        aiLogService.createUsageLog(
                user,
                primaryRole(user),
                featureKey,
                language,
                AiRequestStatus.SUCCESS,
                result.providerName(),
                result.modelName(),
                result.tokenCount(),
                summarize(userPrompt, 500),
                summarize(result.text(), 500),
                null,
                relatedEntityType,
                relatedEntityId);
        return AiExecutionResult.success(result.providerName(), result.modelName(), result.text(), result.tokenCount());
    }

    private User requireUser(SecurityUserDetails currentUser) {
        return userRepository.findById(currentUser.getUserId())
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
        if (normalized.startsWith("ms")) {
            return "ms";
        }
        if (normalized.startsWith("zh")) {
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
        String role = "USER".equalsIgnoreCase(message.getSenderRole()) ? "user" : "assistant";
        return new AiProviderPrompt.AiMessage(role, message.getMessageText());
    }

    private String buildChatPrompt(AiChatRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("Help the learner with this message: ").append(request.message().trim()).append(".");
        if (request.context() != null && !request.context().isBlank()) {
            builder.append(" Context: ").append(request.context().trim()).append(".");
        }
        if (request.relatedLessonId() != null) {
            builder.append(" Related lesson id: ").append(request.relatedLessonId()).append(".");
        }
        builder.append(" Keep the answer child-friendly and concise.");
        return builder.toString();
    }

    private String buildVocabularyPrompt(AiVocabularyHelperRequest request, String language) {
        return "Create a beginner-friendly vocabulary helper in structured JSON for the word \"" + request.word().trim()
                + "\". Input language: " + nullableText(request.inputLanguage())
                + ". Output language preference: " + language
                + ". Learner level: " + defaultLevel(request.learnerLevel())
                + ". Return keys: chineseWord, pinyin, englishMeaning, bahasaMelayuMeaning, simpleExplanation, exampleSentence, exampleSentencePinyin, exampleSentenceTranslation, imagePromptOrDescription, miniPracticeQuestion, encouragement.";
    }

    private String buildSentencePrompt(AiSentencePracticeRequest request, String language) {
        return "Check this beginner Chinese learning sentence and return structured JSON: \"" + request.sentence().trim()
                + "\". Learner level: " + defaultLevel(request.learnerLevel())
                + ". Language preference: " + language
                + ". Return keys: correctedSentence, pinyin, englishMeaning, bahasaMelayuMeaning, improvementExplanation, naturalSentenceVersion, practiceTask, encouragement.";
    }

    private String buildQuizPrompt(AiQuizGeneratorRequest request, String language, String usageMode) {
        return "Generate a child-friendly Chinese learning quiz draft in JSON. Topic: " + request.topic().trim()
                + ". Level: " + nullableText(request.level())
                + ". Category: " + nullableText(request.category())
                + ". Number of questions: " + request.numberOfQuestions()
                + ". Question types: " + (request.questionTypes() == null || request.questionTypes().isEmpty()
                        ? "MIXED_BEGINNER_TYPES"
                        : String.join(", ", request.questionTypes()))
                + ". Usage mode: " + usageMode
                + ". Language preference: " + language
                + ". Return keys: title, questions. Each question should include questionText, questionType, options, correctAnswer, explanation, pinyin, translation.";
    }

    private String buildStoryPrompt(AiStoryGeneratorRequest request, String language, String usageMode) {
        return "Generate a short, child-friendly beginner Chinese story in JSON. Topic: " + request.topic().trim()
                + ". Level: " + defaultLevel(request.level())
                + ". Length preference: " + nullableText(request.lengthPreference())
                + ". Usage mode: " + usageMode
                + ". Language preference: " + language
                + ". Return keys: title, chineseStory, pinyin, englishTranslation, bahasaMelayuTranslation, vocabularyList, comprehensionQuestions, encouragement.";
    }

    private String buildLearningRecommendationPrompt(AiLearningRecommendationRequest request, String summary, String language) {
        return "Based on this student learning summary, return a short structured learning recommendation in JSON. Summary: "
                + summary
                + ". Current level override: " + nullableText(request.currentLevel())
                + ". Language preference: " + language
                + ". Return keys: recommendedNextStep, recommendedLessonOrActivity, weakAreaSummary, revisionSuggestion, shortEncouragement, suggestedPracticeTypes.";
    }

    private String buildProgressInsightPrompt(AiProgressInsightRequest request, String progressSummary, String language) {
        return "Summarize this learner progress in structured JSON for audience " + request.audience().trim().toUpperCase(Locale.ROOT)
                + ". Progress summary: " + progressSummary
                + ". Weak areas: " + (request.weakAreas() == null ? List.of() : request.weakAreas())
                + ". Language preference: " + language
                + ". Return keys: performanceSummary, weakAreas, nextStepSuggestions, studentEncouragement, teacherSuggestion.";
    }

    private String buildTeacherAssistantPrompt(AiTeacherAssistantRequest request, String language) {
        return "Create a teacher-facing beginner Chinese lesson support draft in JSON. Topic: " + request.topic().trim()
                + ". Level: " + nullableText(request.level())
                + ". Category: " + nullableText(request.category())
                + ". Learning objective: " + nullableText(request.learningObjective())
                + ". Activity types: " + (request.activityTypes() == null ? List.of() : request.activityTypes())
                + ". Language preference: " + language
                + ". Return keys: lessonOutline, vocabularyList, exampleSentences, quizQuestions, flashcardContent, matchingGameContent, storyBasedLessonIdea, teachingNotes.";
    }

    private StudentProfile resolveCurrentStudentProfile(SecurityUserDetails currentUser) {
        return studentProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student profile could not be found."));
    }

    private void validateAudienceForRole(SecurityUserDetails currentUser, String audience) {
        if ("STUDENT".equals(audience) && !hasRole(currentUser, RoleName.STUDENT)) {
            throw new ResponseStatusException(FORBIDDEN, "Student insight audience requires a student account.");
        }
        if ("TEACHER".equals(audience) && !hasRole(currentUser, RoleName.TEACHER) && !hasRole(currentUser, RoleName.ADMIN)) {
            throw new ResponseStatusException(FORBIDDEN, "Teacher insight audience requires a teacher or admin account.");
        }
        if ("ADMIN".equals(audience) && !hasRole(currentUser, RoleName.ADMIN)) {
            throw new ResponseStatusException(FORBIDDEN, "Admin insight audience requires an admin account.");
        }
    }

    private void requireTeacherAssistantAccess(SecurityUserDetails currentUser) {
        if (hasRole(currentUser, RoleName.ADMIN)) {
            return;
        }
        requireRole(currentUser, RoleName.TEACHER, "Teacher assistant requires a teacher or admin account.");
        TeacherProfile profile = teacherProfileRepository.findDetailedByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));
        if (profile.getTeacherStatus() != TeacherStatus.APPROVED) {
            throw new ResponseStatusException(FORBIDDEN, "Only approved teachers can use the teacher assistant.");
        }
    }

    private void requireRole(SecurityUserDetails currentUser, RoleName roleName, String message) {
        if (!hasRole(currentUser, roleName)) {
            throw new ResponseStatusException(FORBIDDEN, message);
        }
    }

    private boolean hasRole(SecurityUserDetails currentUser, RoleName roleName) {
        return currentUser.getRoleNames().stream().anyMatch(role -> role.equals(roleName.name()));
    }

    private String resolveQuizUsageMode(SecurityUserDetails currentUser) {
        return hasRole(currentUser, RoleName.STUDENT) ? "TEMPORARY_SELF_PRACTICE" : "EDITABLE_DRAFT";
    }

    private String resolveStoryUsageMode(SecurityUserDetails currentUser) {
        return hasRole(currentUser, RoleName.STUDENT) ? "SELF_PRACTICE" : "DRAFT_ONLY";
    }

    private String resolveProgressSummary(SecurityUserDetails currentUser, AiProgressInsightRequest request) {
        if (hasRole(currentUser, RoleName.STUDENT) && request.studentId() != null) {
            StudentProfile student = resolveCurrentStudentProfile(currentUser);
            if (!student.getId().equals(request.studentId())) {
                throw new ResponseStatusException(FORBIDDEN, "Students can only request progress insight for their own account.");
            }
        }

        if (request.progressSummary() != null && !request.progressSummary().isBlank()) {
            return request.progressSummary().trim();
        }

        if (hasRole(currentUser, RoleName.STUDENT)) {
            StudentProfile student = resolveCurrentStudentProfile(currentUser);
            return buildStudentContextSummary(student);
        }

        return "General learner progress summary is not available. Provide safe summary data to receive teacher or admin insights.";
    }

    private String buildSafeRecommendationSummary(AiLearningRecommendationRequest request) {
        return "Safe learning summary. Current level: " + defaultLevel(request.currentLevel())
                + ". Recent quiz results: " + joinSummaryList(request.recentQuizResults())
                + ". Wrong answers: " + joinSummaryList(request.wrongAnswers())
                + ". Weak areas: " + joinSummaryList(request.weakAreas())
                + ". Completed lessons: " + joinSummaryList(request.completedLessons())
                + ". If there is not enough information, return a short beginner-friendly default recommendation.";
    }

    private String buildStudentContextSummary(StudentProfile studentProfile) {
        StudentProgress latestProgress = studentProgressRepository.findFirstByStudentIdOrderByUpdatedAtDesc(studentProfile.getId())
                .orElse(null);
        List<LessonProgress> recentLessons = lessonProgressRepository.findByStudentIdOrderByUpdatedAtDesc(studentProfile.getId());
        List<QuizAttemptAnswer> wrongAnswers = quizAttemptAnswerRepository.findByQuizAttemptStudentIdAndCorrectFalseOrderByCreatedAtDesc(studentProfile.getId());

        String level = studentProfile.getCurrentLevel() != null ? studentProfile.getCurrentLevel().getName() : "Beginner";
        int totalPoints = studentProfile.getTotalPoints() == null ? 0 : studentProfile.getTotalPoints();
        int completion = latestProgress != null && latestProgress.getCompletionPercent() != null
                ? latestProgress.getCompletionPercent()
                : 0;
        String weakAreas = latestProgress != null && latestProgress.getWeakAreas() != null
                ? latestProgress.getWeakAreas()
                : "";

        return "Student level: " + level
                + ". Total points: " + totalPoints
                + ". Completion percent: " + completion
                + ". Weak areas: " + weakAreas
                + ". Recent lessons: " + recentLessons.stream().limit(3).map(progress -> progress.getLesson().getTitle()).toList()
                + ". Recent wrong answers: " + wrongAnswers.stream().limit(3).map(answer -> answer.getQuestion().getQuestionType()).toList() + ".";
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

    private List<AiGeneratedQuizQuestionResponse> fallbackQuizQuestions(String topic) {
        return List.of(
                new AiGeneratedQuizQuestionResponse(
                        "Which word is related to " + topic + "?",
                        "MULTIPLE_CHOICE",
                        List.of("Option A", "Option B", "Option C"),
                        "Option A",
                        "This is a safe fallback quiz draft.",
                        null,
                        null));
    }

    private AiStoryGeneratorResponse fallbackStoryResponse(
            AiStoryGeneratorRequest request,
            String usageMode,
            String status) {
        return new AiStoryGeneratorResponse(
                request.topic().trim(),
                "AI tutor is currently unavailable. Please try again later.",
                null,
                null,
                null,
                List.of(),
                List.of(),
                "Keep reading and try again later.",
                usageMode,
                status);
    }

    private AiLearningRecommendationResponse fallbackLearningRecommendation(StudentProfile studentProfile, String status) {
        String level = studentProfile.getCurrentLevel() != null ? studentProfile.getCurrentLevel().getName() : "Beginner";
        return new AiLearningRecommendationResponse(
                "Continue with one short " + level + " lesson and review one vocabulary activity.",
                "Beginner vocabulary practice",
                "Not enough AI insight is available right now.",
                "Review your recent wrong answers and repeat one quiz.",
                "Keep going. Small steps help you improve every day!",
                List.of("flashcards", "short quiz", "reading"),
                status);
    }

    private AiLearningRecommendationResponse fallbackGenericLearningRecommendation(String currentLevel, String status) {
        String level = defaultLevel(currentLevel);
        return new AiLearningRecommendationResponse(
                "Continue with one short " + level + " lesson and revise one easy vocabulary set.",
                "Beginner vocabulary practice",
                "Not enough learner-specific data is available right now.",
                "Review one recent mistake and repeat a short activity.",
                "You are doing well. A little practice every day helps a lot!",
                List.of("flashcards", "short quiz", "story"),
                status);
    }

    private AiProgressInsightResponse fallbackProgressInsight(String progressSummary, String audience, String status) {
        String teacherSuggestion = "STUDENT".equals(audience)
                ? null
                : "Use recent quiz results and weak areas to choose the next revision activity.";
        return new AiProgressInsightResponse(
                progressSummary,
                List.of(),
                List.of("Review one weak area", "Repeat a short practice task"),
                "Keep going. Progress grows with regular practice.",
                teacherSuggestion,
                status);
    }

    private AiTeacherAssistantResponse fallbackTeacherAssistant(String topic, String status) {
        return new AiTeacherAssistantResponse(
                "Short lesson outline for topic: " + topic,
                List.of(),
                List.of("Introduce the topic with one simple sentence."),
                fallbackQuizQuestions(topic),
                List.of("Add one keyword flashcard."),
                List.of("Prepare one simple matching pair."),
                "Create a short story based on the topic later.",
                "Review all AI-generated content before saving it as official material.",
                status);
    }

    private AiVocabularyHelperResponse parseVocabularyResponse(String responseText, String status) {
        Map<String, Object> map = parseJsonMap(responseText);
        return new AiVocabularyHelperResponse(
                text(map, "chineseWord"),
                text(map, "pinyin"),
                text(map, "englishMeaning"),
                text(map, "bahasaMelayuMeaning"),
                defaultIfBlank(text(map, "simpleExplanation"), "This word is useful for beginner practice."),
                text(map, "exampleSentence"),
                text(map, "exampleSentencePinyin"),
                text(map, "exampleSentenceTranslation"),
                text(map, "imagePromptOrDescription"),
                text(map, "miniPracticeQuestion"),
                defaultIfBlank(text(map, "encouragement"), "Great job learning new words!"),
                status);
    }

    private AiSentencePracticeResponse parseSentenceResponse(String originalSentence, String responseText, String status) {
        Map<String, Object> map = parseJsonMap(responseText);
        return new AiSentencePracticeResponse(
                originalSentence,
                defaultIfBlank(text(map, "correctedSentence"), originalSentence),
                text(map, "pinyin"),
                text(map, "englishMeaning"),
                text(map, "bahasaMelayuMeaning"),
                defaultIfBlank(text(map, "improvementExplanation"), "Your sentence is a good try. Keep practising."),
                defaultIfBlank(text(map, "naturalSentenceVersion"), originalSentence),
                defaultIfBlank(text(map, "practiceTask"), "Write one more short sentence about the same topic."),
                defaultIfBlank(text(map, "encouragement"), "Good effort. Keep practising!"),
                status);
    }

    @SuppressWarnings("unchecked")
    private AiQuizGeneratorResponse parseQuizResponse(
            AiQuizGeneratorRequest request,
            String usageMode,
            String responseText,
            String status) {
        Map<String, Object> map = parseJsonMap(responseText);
        List<AiGeneratedQuizQuestionResponse> questions = new ArrayList<>();
        Object questionsObject = map.get("questions");
        if (questionsObject instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> question) {
                    questions.add(new AiGeneratedQuizQuestionResponse(
                            text(question, "questionText"),
                            defaultIfBlank(text(question, "questionType"), "MULTIPLE_CHOICE"),
                            stringList(question.get("options")),
                            text(question, "correctAnswer"),
                            text(question, "explanation"),
                            text(question, "pinyin"),
                            text(question, "translation")));
                }
            }
        }
        if (questions.isEmpty()) {
            questions = fallbackQuizQuestions(request.topic());
        }

        return new AiQuizGeneratorResponse(
                defaultIfBlank(text(map, "title"), request.topic().trim() + " Quiz"),
                request.topic().trim(),
                nullableText(request.level()),
                nullableText(request.category()),
                usageMode,
                questions,
                status);
    }

    @SuppressWarnings("unchecked")
    private AiStoryGeneratorResponse parseStoryResponse(
            AiStoryGeneratorRequest request,
            String usageMode,
            String responseText,
            String status) {
        Map<String, Object> map = parseJsonMap(responseText);

        List<AiStoryVocabularyItemResponse> vocabularyList = new ArrayList<>();
        Object vocabObject = map.get("vocabularyList");
        if (vocabObject instanceof List<?> list) {
            for (Object item : list) {
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
                if (item instanceof Map<?, ?> question) {
                    comprehensionQuestions.add(new AiStoryComprehensionQuestionResponse(
                            text(question, "question"),
                            stringList(question.get("options")),
                            text(question, "answer"),
                            text(question, "explanation")));
                }
            }
        }

        return new AiStoryGeneratorResponse(
                defaultIfBlank(text(map, "title"), request.topic().trim()),
                defaultIfBlank(text(map, "chineseStory"), "AI tutor is currently unavailable. Please try again later."),
                text(map, "pinyin"),
                text(map, "englishTranslation"),
                text(map, "bahasaMelayuTranslation"),
                vocabularyList,
                comprehensionQuestions,
                defaultIfBlank(text(map, "encouragement"), "Great reading practice!"),
                usageMode,
                status);
    }

    private AiLearningRecommendationResponse parseLearningRecommendation(
            String responseText,
            String status) {
        Map<String, Object> map = parseJsonMap(responseText);
        return new AiLearningRecommendationResponse(
                defaultIfBlank(text(map, "recommendedNextStep"), "Continue with a short lesson and one revision activity."),
                defaultIfBlank(text(map, "recommendedLessonOrActivity"), "Vocabulary review"),
                defaultIfBlank(text(map, "weakAreaSummary"), "Keep reviewing any difficult words slowly."),
                defaultIfBlank(text(map, "revisionSuggestion"), "Repeat one short activity from your recent lesson."),
                defaultIfBlank(text(map, "shortEncouragement"), "You are improving with each practice session!"),
                stringList(map.get("suggestedPracticeTypes")).isEmpty()
                        ? List.of("flashcards", "quiz", "story")
                        : stringList(map.get("suggestedPracticeTypes")),
                status);
    }

    private AiProgressInsightResponse parseProgressInsight(String responseText, String audience, String status) {
        Map<String, Object> map = parseJsonMap(responseText);
        return new AiProgressInsightResponse(
                defaultIfBlank(text(map, "performanceSummary"), "Keep building your Chinese practice step by step."),
                stringList(map.get("weakAreas")),
                stringList(map.get("nextStepSuggestions")).isEmpty()
                        ? List.of("Review one weak area", "Try one short activity")
                        : stringList(map.get("nextStepSuggestions")),
                defaultIfBlank(text(map, "studentEncouragement"), "Keep going. Practice a little every day!"),
                "STUDENT".equals(audience) ? null : text(map, "teacherSuggestion"),
                status);
    }

    @SuppressWarnings("unchecked")
    private AiTeacherAssistantResponse parseTeacherAssistant(String responseText, String status) {
        Map<String, Object> map = parseJsonMap(responseText);

        List<AiVocabularyEntryResponse> vocabularyList = new ArrayList<>();
        Object vocabObject = map.get("vocabularyList");
        if (vocabObject instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> vocab) {
                    vocabularyList.add(new AiVocabularyEntryResponse(
                            text(vocab, "chineseWord"),
                            text(vocab, "pinyin"),
                            text(vocab, "englishMeaning"),
                            text(vocab, "bahasaMelayuMeaning")));
                }
            }
        }

        List<AiGeneratedQuizQuestionResponse> quizQuestions = new ArrayList<>();
        Object quizObject = map.get("quizQuestions");
        if (quizObject instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> question) {
                    quizQuestions.add(new AiGeneratedQuizQuestionResponse(
                            text(question, "questionText"),
                            defaultIfBlank(text(question, "questionType"), "MULTIPLE_CHOICE"),
                            stringList(question.get("options")),
                            text(question, "correctAnswer"),
                            text(question, "explanation"),
                            text(question, "pinyin"),
                            text(question, "translation")));
                }
            }
        }

        return new AiTeacherAssistantResponse(
                defaultIfBlank(text(map, "lessonOutline"), "Create a short outline with vocabulary, practice, and review."),
                vocabularyList,
                stringList(map.get("exampleSentences")),
                quizQuestions,
                stringList(map.get("flashcardContent")),
                stringList(map.get("matchingGameContent")),
                text(map, "storyBasedLessonIdea"),
                defaultIfBlank(text(map, "teachingNotes"), "Review and refine the draft before saving it."),
                status);
    }

    private Map<String, Object> parseJsonMap(String responseText) {
        try {
            return objectMapper.readValue(responseText, Map.class);
        } catch (JsonProcessingException exception) {
            log.warn("AI JSON parsing fallback triggered: {}", exception.getMessage());
            return Map.of();
        }
    }

    private String text(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .toList();
    }

    private String joinSummaryList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "none";
        }
        return String.join(", ", values.stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .limit(8)
                .toList());
    }

    private String nullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String defaultLevel(String value) {
        return defaultIfBlank(nullableText(value), "Beginner");
    }

    private String primaryRole(User user) {
        return user.getRoles().stream()
                .map(role -> role.getName().name())
                .sorted()
                .findFirst()
                .orElse("USER");
    }

    private String summarize(String value, int limit) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit);
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
