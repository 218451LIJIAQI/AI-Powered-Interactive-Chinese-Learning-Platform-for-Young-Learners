package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiChatResponse;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorResponse;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorResponse;
import com.example.aichinese.dto.ai.AiStoryLineResponse;
import com.example.aichinese.dto.ai.QuizDraftRequest;
import com.example.aichinese.entity.AiChatMessage;
import com.example.aichinese.entity.AiChatSession;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiChatSenderRole;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.QuizQuestionType;
import com.example.aichinese.repository.AiChatMessageRepository;
import com.example.aichinese.repository.AiChatSessionRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.service.ai.AiExecutionResult;
import com.example.aichinese.service.ai.AiGateway;
import com.example.aichinese.service.ai.AiJson;
import com.example.aichinese.service.ai.AiPromptBuilder;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.classes.LessonEditorService;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.LanguageCodes;
import com.example.aichinese.util.PersonalDataRedactor;
import com.example.aichinese.util.TextUtils;

/**
 * Ask SparkBot, Quick quiz and Story for learners, and quiz drafts for lesson authors.
 * Every request goes through {@link AiGateway}; nothing here is a canned answer.
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
    private static final int MAX_DRAFT_OPTION_COUNT = 4;
    /** Questions read from an answer before the unusable ones are dropped and the rest cut to the number asked for. */
    private static final int MAX_QUESTIONS_READ = 10;
    private static final int STUDENT_STORY_QUESTIONS = 2;
    private static final int MAX_STORY_LINES = 12;
    private static final int MAX_STORY_WORDS = 8;
    private static final String DEFAULT_LEVEL = "Beginner (HSK 1-2)";
    /**
     * The quiz editor's question types a draft may use: the editor refuses a listen-and-choose question
     * without a recording and a word-and-image one without a picture, and the AI can give neither.
     */
    private static final Set<QuizQuestionType> DRAFT_QUESTION_TYPES = Arrays.stream(QuizQuestionType.values())
            .filter(type -> !type.needsAudio() && !type.needsImage())
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(QuizQuestionType.class)));

    private final CurrentAccount currentAccount;
    private final AiGateway gateway;
    private final AiChatSessionRepository aiChatSessionRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final LessonEditorService lessonEditorService;
    private final TransactionTemplate transaction;

    public AiService(
            CurrentAccount currentAccount,
            AiGateway gateway,
            AiChatSessionRepository aiChatSessionRepository,
            AiChatMessageRepository aiChatMessageRepository,
            LessonEditorService lessonEditorService,
            PlatformTransactionManager transactionManager) {
        this.currentAccount = currentAccount;
        this.gateway = gateway;
        this.aiChatSessionRepository = aiChatSessionRepository;
        this.aiChatMessageRepository = aiChatMessageRepository;
        this.lessonEditorService = lessonEditorService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** Validated inputs and prior turns for a single Ask SparkBot turn, ready for provider execution. */
    public record PreparedChat(
            User user,
            String language,
            String message,
            Long sessionId,
            List<AiProviderPrompt.AiMessage> history) {
    }

    /**
     * One Ask SparkBot turn. The session's earlier turns give the answer its context; a turn is stored (redacted)
     * only when SparkBot answered, so a blocked or failed question never comes back through the history.
     */
    public AiChatResponse chat(SecurityUserDetails currentUser, AiChatRequest request) {
        return completeChat(currentUser, prepareChat(currentUser, request));
    }

    /**
     * Validates the learner, message and optional session before opening an SSE stream so client errors (400/401/404)
     * return normal JSON HTTP error responses instead of HTTP 200 text/event-stream.
     */
    public PreparedChat prepareChat(SecurityUserDetails currentUser, AiChatRequest request) {
        User user = currentAccount.require(currentUser);
        String language = LanguageCodes.forRequest(request.languagePreference(), user.getPreferredLanguage());
        String message = PersonalDataRedactor.redact(AiInputSanitizer.requireLong(request.message(), "Message is required."));
        AiChatSession session = request.sessionId() == null
                ? null
                : aiChatSessionRepository.findByIdAndUserId(request.sessionId(), user.getId())
                        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AI chat session was not found."));
        List<AiProviderPrompt.AiMessage> history = session == null ? List.of() : chatHistory(session);
        return new PreparedChat(user, language, message, session == null ? null : session.getId(), history);
    }

    /** Calls the AI provider and atomically stores a completed Ask SparkBot turn. */
    public AiChatResponse completeChat(SecurityUserDetails currentUser, PreparedChat prepared) {
        AiExecutionResult result = gateway.execute(
                AiGateway.AiCall.text(prepared.user(), AiFeatureKey.CHAT_TUTOR, prepared.language(), chatPrompt(prepared.message()))
                        .withSession(currentUser.getSessionId())
                        .withHistory(prepared.history()));
        if (!result.succeeded()) {
            return new AiChatResponse(prepared.sessionId(), null, result.userMessage(), result.status());
        }

        String answerText = AiInputSanitizer.sanitize(result.responseText(), MAX_RETAINED_AI_CHAT_RESPONSE_CHARS);
        if (Thread.currentThread().isInterrupted()) {
            // The Ask SparkBot stream interrupts its worker when it closes (timed out, or the student left): nobody
            // receives this answer, so the turn is not kept in the history or offered for a report.
            throw new IllegalStateException("The AI chat stream closed before the answer was stored.");
        }
        // Keep a turn atomic and adjacent for history and reports; never hold a lock while waiting for the provider.
        return transaction.execute(status -> {
            User current = currentAccount.lock(currentUser);
            AiChatSession activeSession = prepared.sessionId() == null ? newChatSession(current)
                    : aiChatSessionRepository.findLockedByIdAndUserId(prepared.sessionId(), current.getId())
                            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AI chat session was not found."));
            saveChatMessage(activeSession, AiChatSenderRole.USER, prepared.message());
            AiChatMessage answer = saveChatMessage(activeSession, AiChatSenderRole.ASSISTANT, answerText);
            return new AiChatResponse(activeSession.getId(), answer.getId(), answerText, result.status());
        });
    }

    private AiChatSession newChatSession(User user) {
        AiChatSession session = new AiChatSession();
        session.setUser(user);
        session.setFeatureKey(AiFeatureKey.CHAT_TUTOR);
        return aiChatSessionRepository.save(session);
    }

    private AiChatMessage saveChatMessage(AiChatSession session, AiChatSenderRole senderRole, String text) {
        AiChatMessage message = new AiChatMessage();
        message.setSession(session);
        message.setSenderRole(senderRole);
        message.setMessageText(text);
        return aiChatMessageRepository.save(message);
    }

    public AiQuizGeneratorResponse quizGenerator(SecurityUserDetails currentUser, AiQuizGeneratorRequest request) {
        return generateQuiz(currentAccount.require(currentUser), currentUser.getSessionId(),
                request.topic(), request.level(), request.languagePreference(),
                clamp(request.numberOfQuestions(), DEFAULT_STUDENT_QUIZ_QUESTIONS, MAX_STUDENT_QUIZ_QUESTIONS), false);
    }

    /**
     * A quiz draft for the admin lesson editor, returned only (never saved as content): at most the places left in the
     * lesson's quiz (all of them unless the request asks for fewer; refused when the quiz is full).
     */
    public AiQuizGeneratorResponse quizDraft(SecurityUserDetails currentUser, QuizDraftRequest request) {
        User admin = currentAccount.require(currentUser);
        int openPlaces = lessonEditorService.openQuestionSlots(request.lessonId());
        return generateQuiz(admin, currentUser.getSessionId(),
                request.topic(), request.level(), request.languagePreference(),
                clamp(request.numberOfQuestions(), openPlaces, openPlaces), true);
    }

    private AiQuizGeneratorResponse generateQuiz(
            User user, String sessionId, String requestedTopic, String requestedLevel, String languagePreference,
            int questionCount, boolean draft) {
        String language = LanguageCodes.forRequest(languagePreference, user.getPreferredLanguage());
        String topic = AiInputSanitizer.requireShort(requestedTopic, "Topic is required.");
        String prompt = "Generate a short beginner Mandarin quiz in JSON for a young adult learner. Topic: " + topic
                + ". Level: " + level(requestedLevel)
                + ". Number of questions: " + questionCount
                + (draft
                        ? ". Mix these question types; questionType is exactly one of them: "
                                + String.join(", ", DRAFT_QUESTION_TYPES.stream().map(Enum::name).toList())
                                + ". FILL_BLANK and REARRANGE_SENTENCE have no options; every other type has 2 to 4"
                                + " options and correctAnswer is exactly one of the options. Usage mode: LESSON_QUIZ_DRAFT"
                        : ". Every question is multiple choice with exactly 3 options and correctAnswer is exactly one"
                                + " of the options; write questionText and explanation in the learner's language."
                                + " Usage mode: SELF_PRACTICE")
                + ". Language preference: " + language
                + ". Return keys: title, questions. Each question should include questionText, questionType, options,"
                + " correctAnswer, explanation, pinyin, translation.";
        AiExecutionResult result = gateway.execute(AiGateway.AiCall.json(
                        user, draft ? AiFeatureKey.QUIZ_DRAFT : AiFeatureKey.QUIZ_GENERATOR, language, prompt,
                        json -> !quizQuestions(json, draft, questionCount).isEmpty())
                .withSession(sessionId));
        if (!result.succeeded()) {
            return new AiQuizGeneratorResponse(topic, List.of(), result.status());
        }
        return new AiQuizGeneratorResponse(
                TextUtils.defaultIfBlank(AiJson.text(result.json(), "title"), topic),
                quizQuestions(result.json(), draft, questionCount),
                result.status());
    }

    /**
     * The questions the page can use (at most the number asked for), each with a question and a correct answer. A
     * learner answers multiple choice in the browser, so the answer must be among the options. A lesson quiz draft's
     * questions are added to the lesson quiz as they are: each has a type of the quiz editor that needs no recording or
     * picture (an unknown type is read as multiple choice, as the editor does), and an option-based one has the answer
     * among its options, so that the editor can save it.
     */
    private static List<AiQuizGeneratorResponse.Question> quizQuestions(
            Map<String, Object> json, boolean draft, int questionCount) {
        return AiJson.objects(json, "questions", MAX_QUESTIONS_READ, question -> {
                    QuizQuestionType type = draft
                            ? draftQuestionType(AiJson.text(question, "questionType"))
                            : QuizQuestionType.MULTIPLE_CHOICE;
                    List<String> options = draft && !type.isOptionBased() ? List.of() : AiJson.strings(question, "options");
                    return new AiQuizGeneratorResponse.Question(
                            AiJson.text(question, "questionText"),
                            type,
                            options,
                            AiJson.text(question, "correctAnswer"),
                            AiJson.text(question, "explanation"),
                            AiJson.text(question, "pinyin"),
                            AiJson.text(question, "translation"));
                })
                .stream()
                .filter(question -> question.questionText() != null && question.correctAnswer() != null)
                .filter(question -> draft
                        ? savableDraft(question)
                        : answerAmongOptions(question.options(), question.correctAnswer()))
                .limit(questionCount)
                .toList();
    }

    /** The editor's question type the AI named; an unknown or missing one is multiple choice. */
    private static QuizQuestionType draftQuestionType(String value) {
        return QuizQuestionType.tryFrom(value == null ? null : value.strip().replaceAll("[\\s-]+", "_"))
                .orElse(QuizQuestionType.MULTIPLE_CHOICE);
    }

    private static boolean savableDraft(AiQuizGeneratorResponse.Question question) {
        QuizQuestionType type = question.questionType();
        return DRAFT_QUESTION_TYPES.contains(type)
                && (!type.isOptionBased()
                        || (question.options().size() <= MAX_DRAFT_OPTION_COUNT
                                && answerAmongOptions(question.options(), question.correctAnswer())));
    }

    /** The answer is one of at least two distinct labels, as shown in the quiz or story. */
    private static boolean answerAmongOptions(List<String> options, String answer) {
        return options.size() >= 2 && options.contains(answer) && options.stream().distinct().count() == options.size();
    }

    public AiStoryGeneratorResponse storyGenerator(SecurityUserDetails currentUser, AiStoryGeneratorRequest request) {
        User user = currentAccount.require(currentUser);
        String language = LanguageCodes.forRequest(request.languagePreference(), user.getPreferredLanguage());
        String topic = AiInputSanitizer.requireShort(request.topic(), "Topic is required.");
        String prompt = "Write a short beginner Mandarin story in JSON about an everyday young-adult situation in"
                + " Malaysia. Topic: " + topic
                + ". Level: " + DEFAULT_LEVEL
                + ". Length: 4 to 6 short sentences"
                + ". Usage mode: SELF_PRACTICE"
                + ". Language preference: " + language
                + ". Return keys: title (in Chinese), titleTranslation (in "
                + AiPromptBuilder.translationLanguage(language) + "), lines (one object per sentence with keys"
                + " chinese, pinyin, english, malay), vocabularyList (4 to 6 key words, each with keys chineseWord,"
                + " pinyin, englishMeaning, bahasaMelayuMeaning), comprehensionQuestions ("
                + "exactly " + STUDENT_STORY_QUESTIONS
                + ", each with keys question, options (3 short options) and answer (exactly one of the options),"
                + " written in the learner's language).";
        AiExecutionResult result = gateway.execute(AiGateway.AiCall.json(
                        user, AiFeatureKey.STORY_GENERATOR, language, prompt, json -> !storyLines(json).isEmpty())
                .withSession(currentUser.getSessionId()));
        if (!result.succeeded()) {
            return new AiStoryGeneratorResponse(topic, null, List.of(), List.of(), List.of(), result.status());
        }

        Map<String, Object> json = result.json();
        List<Map<?, ?>> lines = storyLines(json);
        boolean malay = LanguageCodes.MALAY.equals(language);
        List<AiStoryGeneratorResponse.ComprehensionQuestion> questions = AiJson.objects(json, "comprehensionQuestions",
                STUDENT_STORY_QUESTIONS, question -> {
                    String promptText = AiJson.text(question, "question");
                    List<String> options = AiJson.strings(question, "options");
                    String answer = AiJson.text(question, "answer");
                    return promptText != null && answer != null && answerAmongOptions(options, answer)
                            ? new AiStoryGeneratorResponse.ComprehensionQuestion(promptText, options, answer)
                            : null;
                });
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
                        ? new AiStoryGeneratorResponse.VocabularyItem(
                                AiJson.text(word, "chineseWord"),
                                AiJson.text(word, "pinyin"),
                                AiJson.text(word, "englishMeaning"),
                                AiJson.text(word, "bahasaMelayuMeaning"))
                        : null),
                questions,
                result.status());
    }

    /** Story sentences with Chinese text to display and read aloud. */
    private static List<Map<?, ?>> storyLines(Map<String, Object> json) {
        return AiJson.objects(json, "lines", MAX_STORY_LINES,
                line -> AiJson.hasText(line, "chinese") ? line : null);
    }

    private List<AiProviderPrompt.AiMessage> chatHistory(AiChatSession session) {
        return aiChatMessageRepository.findBySessionIdOrderByCreatedAtDescIdDesc(session.getId(), PageRequest.of(0, CHAT_HISTORY_LIMIT))
                .reversed().stream()
                .map(message -> new AiProviderPrompt.AiMessage(
                        message.getSenderRole() == AiChatSenderRole.USER ? "user" : "assistant",
                        TextUtils.defaultIfBlank(AiInputSanitizer.sanitizeLong(message.getMessageText()), "")))
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
        return requested == null ? fallback : Math.clamp(requested, 1, max);
    }

    private static String level(String value) {
        return TextUtils.defaultIfBlank(AiInputSanitizer.sanitizeShort(value), DEFAULT_LEVEL);
    }
}
