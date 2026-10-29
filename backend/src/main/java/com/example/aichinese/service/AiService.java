package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiChatResponse;
import com.example.aichinese.dto.ai.AiGeneratedQuizQuestionResponse;
import com.example.aichinese.dto.ai.AiHelperStatusResponse;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorResponse;
import com.example.aichinese.dto.ai.AiStoryComprehensionQuestionResponse;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorResponse;
import com.example.aichinese.dto.ai.AiStoryLineResponse;
import com.example.aichinese.dto.ai.AiStoryVocabularyItemResponse;
import com.example.aichinese.entity.AiChatMessage;
import com.example.aichinese.entity.AiChatSession;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiChatSenderRole;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.repository.AiChatMessageRepository;
import com.example.aichinese.repository.AiChatSessionRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.AiExecutionResult;
import com.example.aichinese.service.ai.AiGateway;
import com.example.aichinese.service.ai.AiJson;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiUsageLimiter;
import com.example.aichinese.service.ai.AiUserResolver;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.LanguageCodes;
import com.example.aichinese.util.PersonalDataRedactor;
import com.example.aichinese.util.TextUtils;

/**
 * Ask SparkBot (chat), Quick quiz and Story for students, the AI Helper status line, and teachers' AI quiz and story
 * drafts. Every request goes through {@link AiGateway}; nothing here is a canned answer.
 *
 * <p>Data minimisation: no user name, e-mail address or id is ever placed in a prompt; student text is sanitised
 * here and redacted of direct identifiers ({@link PersonalDataRedactor}) before it is stored as chat history and
 * again in the prompt builder before it is sent.</p>
 */
@Service
public class AiService {

    private static final int CHAT_HISTORY_LIMIT = 20;
    private static final int MAX_RETAINED_AI_CHAT_RESPONSE_CHARS = 4000;
    private static final int DEFAULT_STUDENT_QUIZ_QUESTIONS = 3;
    private static final int MAX_STUDENT_QUIZ_QUESTIONS = 5;
    private static final int DEFAULT_TEACHER_QUIZ_QUESTIONS = 5;
    private static final int MAX_TEACHER_QUIZ_QUESTIONS = 10;
    private static final int STUDENT_STORY_QUESTIONS = 2;
    private static final int MAX_STORY_LINES = 12;
    private static final int MAX_STORY_WORDS = 8;
    private static final String RELATED_ENTITY_CHAT_SESSION = "AiChatSession";
    private static final String DEFAULT_LEVEL = "Beginner (HSK 1-2)";

    private final AiUserResolver users;
    private final AiGateway gateway;
    private final AiChatSessionRepository aiChatSessionRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiAvailabilityService aiAvailabilityService;
    private final AiUsageLimiter aiUsageLimiter;
    private final AiLogService aiLogService;
    private final PlatformSettingsService platformSettingsService;

    public AiService(
            AiUserResolver users,
            AiGateway gateway,
            AiChatSessionRepository aiChatSessionRepository,
            AiChatMessageRepository aiChatMessageRepository,
            AiAvailabilityService aiAvailabilityService,
            AiUsageLimiter aiUsageLimiter,
            AiLogService aiLogService,
            PlatformSettingsService platformSettingsService) {
        this.users = users;
        this.gateway = gateway;
        this.aiChatSessionRepository = aiChatSessionRepository;
        this.aiChatMessageRepository = aiChatMessageRepository;
        this.aiAvailabilityService = aiAvailabilityService;
        this.aiUsageLimiter = aiUsageLimiter;
        this.aiLogService = aiLogService;
        this.platformSettingsService = platformSettingsService;
    }

    /** The AI Helper toolbar: availability, today's request count against the daily limit, and the AI notice. */
    public AiHelperStatusResponse status(SecurityUserDetails currentUser) {
        AiAvailabilityResponse availability = aiAvailabilityService.getAvailability();
        return new AiHelperStatusResponse(
                availability.available(),
                availability.speechAnalysisAvailable(),
                availability.imageAnalysisAvailable(),
                aiUsageLimiter.usedToday(currentUser.getUserId()),
                aiUsageLimiter.requestsPerDay(),
                platformSettingsService.getPublicPlatformSettings().aiDisclaimerText());
    }

    /**
     * Availability for teachers' drafts. The configuration reason (missing key, invalid URL...) is a technical
     * detail: it is only written to the admin AI usage log.
     */
    public AiAvailabilityResponse getAvailability() {
        AiAvailabilityResponse availability = aiAvailabilityService.getAvailability();
        return availability.available()
                ? availability
                : AiAvailabilityResponse.unavailable(
                        availability.provider(), availability.model(), AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE);
    }

    /**
     * One Ask SparkBot turn. The session's earlier turns give the answer its context; a turn is stored (redacted)
     * only when SparkBot answered, so a blocked or failed question never comes back through the history.
     */
    public AiChatResponse chat(SecurityUserDetails currentUser, AiChatRequest request) {
        User user = users.require(currentUser);
        String language = AiUserResolver.language(user, request.languagePreference());
        String message = PersonalDataRedactor.redact(requireLong(request.message(), "Message is required."));
        AiChatSession session = request.sessionId() == null
                ? null
                : aiChatSessionRepository.findByIdAndUserId(request.sessionId(), user.getId())
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AI chat session was not found."));

        AiExecutionResult result = gateway.execute(
                AiGateway.AiCall.text(user, AiFeatureKey.CHAT_TUTOR, language, chatPrompt(message))
                        .withHistory(session == null ? List.of() : chatHistory(session))
                        .withRelatedEntity(session == null ? null : RELATED_ENTITY_CHAT_SESSION,
                                session == null ? null : session.getId()));
        if (!result.succeeded()) {
            return new AiChatResponse(
                    session == null ? null : session.getId(), null, result.userMessage(), result.status().name());
        }

        AiChatSession activeSession = session != null ? session : aiLogService.createChatSession(user, language);
        aiLogService.appendChatMessage(activeSession, AiChatSenderRole.USER, message);
        String answerText = TextUtils.defaultIfBlank(
                AiInputSanitizer.sanitize(result.responseText(), MAX_RETAINED_AI_CHAT_RESPONSE_CHARS),
                AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE);
        AiChatMessage answer = aiLogService.appendChatMessage(activeSession, AiChatSenderRole.ASSISTANT, answerText);
        return new AiChatResponse(activeSession.getId(), answer.getId(), answerText, result.status().name());
    }

    public AiQuizGeneratorResponse quizGenerator(SecurityUserDetails currentUser, AiQuizGeneratorRequest request) {
        return generateQuiz(users.require(currentUser), request, false);
    }

    /** An approved teacher's quiz draft: returned only, never saved as content. */
    public AiQuizGeneratorResponse teacherQuizDraft(SecurityUserDetails currentUser, AiQuizGeneratorRequest request) {
        return generateQuiz(users.require(currentUser), request, true);
    }

    public AiStoryGeneratorResponse storyGenerator(SecurityUserDetails currentUser, AiStoryGeneratorRequest request) {
        return generateStory(users.require(currentUser), request);
    }

    private AiQuizGeneratorResponse generateQuiz(User user, AiQuizGeneratorRequest request, boolean teacherDraft) {
        String language = AiUserResolver.language(user, request.languagePreference());
        String topic = requireShort(request.topic(), "Topic is required.");
        int questionCount = teacherDraft
                ? clamp(request.numberOfQuestions(), DEFAULT_TEACHER_QUIZ_QUESTIONS, MAX_TEACHER_QUIZ_QUESTIONS)
                : clamp(request.numberOfQuestions(), DEFAULT_STUDENT_QUIZ_QUESTIONS, MAX_STUDENT_QUIZ_QUESTIONS);
        String prompt = "Generate a short beginner Mandarin quiz in JSON for a young adult learner. Topic: " + topic
                + ". Level: " + level(request.level())
                + ". Number of questions: " + questionCount
                + (teacherDraft
                        ? ". Question types: MIXED_BEGINNER_TYPES. Usage mode: TEACHER_DRAFT"
                        : ". Every question is multiple choice with exactly 3 options and correctAnswer is exactly one"
                                + " of the options; write questionText and explanation in the learner's language."
                                + " Usage mode: SELF_PRACTICE")
                + ". Language preference: " + language
                + ". Return keys: title, questions. Each question should include questionText, questionType, options,"
                + " correctAnswer, explanation, pinyin, translation.";
        AiExecutionResult result = gateway.execute(AiGateway.AiCall.json(
                user, teacherDraft ? AiFeatureKey.TEACHER_DRAFT : AiFeatureKey.QUIZ_GENERATOR, language, prompt,
                json -> !quizQuestions(json, teacherDraft, questionCount).isEmpty()));
        if (!result.succeeded()) {
            return new AiQuizGeneratorResponse(topic, topic, List.of(), result.status().name());
        }
        return new AiQuizGeneratorResponse(
                TextUtils.defaultIfBlank(AiJson.text(result.json(), "title"), topic),
                topic,
                quizQuestions(result.json(), teacherDraft, questionCount),
                result.status().name());
    }

    /**
     * The questions a student can answer and check in the browser: a question, a correct answer and, for students,
     * multiple choice with the answer among the options (at most the number asked for).
     */
    private static List<AiGeneratedQuizQuestionResponse> quizQuestions(
            Map<String, Object> json, boolean teacherDraft, int questionCount) {
        return AiJson.objects(json, "questions", MAX_TEACHER_QUIZ_QUESTIONS, question -> new AiGeneratedQuizQuestionResponse(
                        AiJson.text(question, "questionText"),
                        TextUtils.defaultIfBlank(AiJson.text(question, "questionType"), "MULTIPLE_CHOICE"),
                        AiJson.strings(question, "options"),
                        AiJson.text(question, "correctAnswer"),
                        AiJson.text(question, "explanation"),
                        AiJson.text(question, "pinyin"),
                        AiJson.text(question, "translation")))
                .stream()
                .filter(question -> question.questionText() != null && question.correctAnswer() != null)
                .filter(question -> teacherDraft
                        || (question.options().size() >= 2 && question.options().contains(question.correctAnswer())))
                .limit(questionCount)
                .toList();
    }

    private AiStoryGeneratorResponse generateStory(User user, AiStoryGeneratorRequest request) {
        String language = AiUserResolver.language(user, request.languagePreference());
        String topic = requireShort(request.topic(), "Topic is required.");
        String prompt = "Write a short beginner Mandarin story in JSON about an everyday young-adult situation in"
                + " Malaysia. Topic: " + topic
                + ". Level: " + DEFAULT_LEVEL
                + ". Length: 4 to 6 short sentences"
                + ". Usage mode: SELF_PRACTICE"
                + ". Language preference: " + language
                + ". Return keys: title (in Chinese), titleTranslation (in "
                + AiUserResolver.translationLanguage(language) + "), lines (one object per sentence with keys"
                + " chinese, pinyin, english, malay), vocabularyList (4 to 6 key words, each with keys chineseWord,"
                + " pinyin, englishMeaning, bahasaMelayuMeaning), comprehensionQuestions ("
                + "exactly " + STUDENT_STORY_QUESTIONS
                + ", each with keys question, options (3 short options), answer (exactly one of the options) and"
                + " explanation, written in the learner's language).";
        AiExecutionResult result = gateway.execute(AiGateway.AiCall.json(
                user, AiFeatureKey.STORY_GENERATOR, language, prompt, json -> !storyLines(json).isEmpty()));
        if (!result.succeeded()) {
            return new AiStoryGeneratorResponse(topic, null, List.of(), List.of(), List.of(), result.status().name());
        }

        Map<String, Object> json = result.json();
        List<Map<?, ?>> lines = storyLines(json);
        boolean malay = LanguageCodes.MALAY.equals(language);
        List<AiStoryComprehensionQuestionResponse> questions = AiJson.objects(json, "comprehensionQuestions",
                        STUDENT_STORY_QUESTIONS, question -> new AiStoryComprehensionQuestionResponse(
                                AiJson.text(question, "question"),
                                AiJson.strings(question, "options"),
                                AiJson.text(question, "answer"),
                                AiJson.text(question, "explanation")))
                .stream()
                .filter(question -> question.question() != null && question.answer() != null)
                .filter(question -> question.options().size() >= 2 && question.options().contains(question.answer()))
                .limit(STUDENT_STORY_QUESTIONS)
                .toList();
        return new AiStoryGeneratorResponse(
                TextUtils.defaultIfBlank(AiJson.text(json, "title"), topic),
                AiJson.text(json, "titleTranslation"),
                lines.stream()
                        .map(line -> new AiStoryLineResponse(
                                AiJson.text(line, "chinese"),
                                AiJson.text(line, "pinyin"),
                                AiJson.text(line, malay ? "malay" : "english")))
                        .toList(),
                AiJson.objects(json, "vocabularyList", MAX_STORY_WORDS, word -> AiJson.hasText(word, "chineseWord")
                        ? new AiStoryVocabularyItemResponse(
                                AiJson.text(word, "chineseWord"),
                                AiJson.text(word, "pinyin"),
                                AiJson.text(word, "englishMeaning"),
                                AiJson.text(word, "bahasaMelayuMeaning"))
                        : null),
                questions,
                result.status().name());
    }

    /** The story sentences that have Chinese text. */
    private static List<Map<?, ?>> storyLines(Map<String, Object> json) {
        return AiJson.objects(json, "lines", MAX_STORY_LINES, line -> AiJson.hasText(line, "chinese") ? line : null);
    }

    private List<AiProviderPrompt.AiMessage> chatHistory(AiChatSession session) {
        Function<AiChatMessage, AiProviderPrompt.AiMessage> toMessage = message -> new AiProviderPrompt.AiMessage(
                message.getSenderRole() == AiChatSenderRole.USER ? "user" : "assistant",
                TextUtils.defaultIfBlank(AiInputSanitizer.sanitizeLong(message.getMessageText()), ""));
        return aiChatMessageRepository.findBySessionIdOrderByCreatedAtDesc(session.getId(), PageRequest.of(0, CHAT_HISTORY_LIMIT))
                .getContent().stream()
                .sorted(Comparator.comparing(AiChatMessage::getCreatedAt).thenComparing(AiChatMessage::getId))
                .map(toMessage)
                .toList();
    }

    private static String chatPrompt(String message) {
        return "Learner message: \"" + message + "\"."
                + " Reply as a concise beginner Mandarin tutor in at most about 120 words."
                + " If the message asks about a word, give the word, its pinyin, its meaning and one short example."
                + " If it asks you to check a sentence, give the corrected sentence and at most two short reasons."
                + " If the message is not about learning Mandarin, politely decline and suggest a Mandarin-learning"
                + " question instead.";
    }

    private static int clamp(Integer requested, int fallback, int max) {
        return requested == null ? fallback : Math.max(1, Math.min(max, requested));
    }

    private static String level(String value) {
        return TextUtils.defaultIfBlank(AiInputSanitizer.sanitizeShort(value), DEFAULT_LEVEL);
    }

    static String requireShort(String value, String message) {
        String sanitized = AiInputSanitizer.sanitizeShort(value);
        if (sanitized == null) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        return sanitized;
    }

    static String requireLong(String value, String message) {
        String sanitized = AiInputSanitizer.sanitizeLong(value);
        if (sanitized == null) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        return sanitized;
    }
}
