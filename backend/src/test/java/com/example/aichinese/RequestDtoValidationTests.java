package com.example.aichinese;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.admin.AdminDeactivationRequest;
import com.example.aichinese.dto.admin.AdminReactivationRequest;
import com.example.aichinese.dto.ai.AdminAiSettingsUpdateRequest;
import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.CreateAiResponseReportRequest;
import com.example.aichinese.dto.ai.QuizDraftRequest;
import com.example.aichinese.dto.ai.ReviewAiResponseReportRequest;
import com.example.aichinese.dto.ai.XiaomeiConversationRequest;
import com.example.aichinese.dto.ai.XiaomeiReplyRequest;
import com.example.aichinese.dto.ai.XiaomeiTurn;
import com.example.aichinese.dto.auth.RegisterStudentRequest;
import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.QuizOptionRequest;
import com.example.aichinese.dto.classes.QuizQuestionRequest;
import com.example.aichinese.dto.classes.QuizRequest;
import com.example.aichinese.dto.classes.QuizSubmissionAnswerRequest;
import com.example.aichinese.dto.communication.NotificationIdsRequest;
import com.example.aichinese.dto.learn.HintPolicyDto;
import com.example.aichinese.dto.learn.LevelCheckNextRequest;
import com.example.aichinese.dto.learn.LevelCheckQuestionData;
import com.example.aichinese.dto.learn.PracticeCompleteRequest;
import com.example.aichinese.dto.learn.PracticeItemAnswer;
import com.example.aichinese.dto.learn.QuestionAnswer;
import com.example.aichinese.dto.support.CreateSupportRequest;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LevelCheckLevel;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.PublishStatus;
import com.example.aichinese.entity.enums.SupportTopic;
import com.example.aichinese.entity.enums.XiaomeiScenario;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Pins the Jakarta Validation annotations declared on the class authoring,
 * learning, AI, notification, activity and settings request DTOs. Acts as a contract guard so a future refactor that
 * loosens a {@code @NotBlank} / {@code @Size} / {@code @Min} /
 * {@code @Positive} constraint trips an explicit unit failure rather than
 * silently widening the API attack surface.
 */
class RequestDtoValidationTests {

    @AutoClose
    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    private static final String IDEOGRAPHIC_SPACE = "\u3000";
    private static final String MULTIPLE_CHOICE = "MULTIPLE_CHOICE";
    private static final String QUESTION_PROMPT = "Choose the correct answer.";
    private static final int TOO_MANY_ITEMS = 201;

    @Test
    void contentRequestIdsMustBePositive() {
        ClassRequests.ClassDetails details = new ClassRequests.ClassDetails("Class", "Description", -3L, null);
        ClassRequests.LessonContent content = new ClassRequests.LessonContent("Lesson", -1L, List.of());
        QuizDraftRequest draft = new QuizDraftRequest(-1L, "Greetings", null, 3, null);

        assertViolationPathsContain(details, "categoryId");
        assertViolationPathsContain(content, "unitId");
        assertViolationPathsContain(draft, "lessonId");
        assertViolationPathsContain(new QuizDraftRequest(null, "Greetings", null, 3, null), "lessonId");
    }

    /** A lesson quiz has exactly 5 questions: an AI draft writes 1 to 5 of them, a new question goes at place 1 or later. */
    @Test
    void quizDraftsAndNewQuestionsStayWithinTheFiveQuestionsOfALessonQuiz() {
        assertThat(VALIDATOR.validate(new QuizDraftRequest(1L, "Greetings", null, 5, null))).isEmpty();
        assertViolationPathsContain(new QuizDraftRequest(1L, "Greetings", null, 6, null), "numberOfQuestions");
        assertViolationPathsContain(new QuizDraftRequest(1L, "Greetings", null, 0, null), "numberOfQuestions");
        assertViolationPathsContain(new QuizQuestionRequest(MULTIPLE_CHOICE, QUESTION_PROMPT, null, 5,
                List.of(new QuizOptionRequest("A", true, null)), null, null, null, null, 0, true), "position");
        assertViolationPathsContain(new ClassRequests.QuizSettings(null), "hintsAllowed");
    }

    /** The Undo of "Mark all as read" sends the ids it got back: a list is required, at most 1000 of them. */
    @Test
    void markingNotificationsUnreadNeedsAListOfIds() {
        assertThat(VALIDATOR.validate(new NotificationIdsRequest(List.of()))).isEmpty();
        assertViolationPathsContain(new NotificationIdsRequest(null), "ids");
        assertViolationPathsContain(new NotificationIdsRequest(
                LongStream.rangeClosed(1, 1001).boxed().toList()), "ids");
        assertViolationPathStartsWith(new NotificationIdsRequest(singletonList(null)), "ids");
        assertViolationPathStartsWith(new NotificationIdsRequest(List.of(0L)), "ids");
        assertViolationPathStartsWith(new NotificationIdsRequest(List.of(-5L)), "ids");
    }

    /** BCrypt caps passwords at 72 UTF-8 bytes: 25 CJK characters (75 bytes) must fail even though length < 72. */
    @Test
    void maxUtf8BytesRejectsMultiBytePasswordsExceeding72Bytes() {
        String multiByteOverLimit = "汉".repeat(25);
        assertThat(multiByteOverLimit).hasSize(25);
        var request = new RegisterStudentRequest(
                "Amira", "amira@example.com", multiByteOverLimit, multiByteOverLimit, " ");

        assertViolationPathsContain(request, "password", "confirmPassword");
        assertThat(request.preferredLanguage()).isNull();
    }

    @Test
    void bestAfterUnitIsOptionalAndOneToEight() {
        assertThat(VALIDATOR.validate(new ClassRequests.ClassDetails("Class", null, null, null))).isEmpty();
        assertThat(VALIDATOR.validate(new ClassRequests.ClassDetails("Class", null, null, 8))).isEmpty();
        assertViolationPathsContain(new ClassRequests.ClassDetails("Class", null, null, 0), "bestAfterUnit");
        assertViolationPathsContain(new ClassRequests.ClassDetails("Class", null, null, 9), "bestAfterUnit");
    }

    @Test
    void aiRequestIdsMustBePositive() {
        AiChatRequest chatRequest = new AiChatRequest("你好", "en", -1L);
        CreateAiResponseReportRequest reportRequest = new CreateAiResponseReportRequest(
                0L,
                -3L,
                "CHAT_TUTOR",
                "INCORRECT_CONTENT",
                null,
                null,
                "Response snapshot");

        assertViolationPathsContain(chatRequest, "sessionId");
        assertViolationPathsContain(reportRequest, "chatMessageId", "practiceCheckId");
    }

    @Test
    void aiQuizGeneratorRejectsNonPositiveQuestionCountAndAllowsNullDefault() {
        assertThat(VALIDATOR.validate(new AiQuizGeneratorRequest("Tones", null, null, null))).isEmpty();
        assertThat(VALIDATOR.validate(new AiQuizGeneratorRequest("Tones", null, 1, null))).isEmpty();
        assertViolationPathsContain(new AiQuizGeneratorRequest("Tones", null, 0, null), "numberOfQuestions");
        assertViolationPathsContain(new AiQuizGeneratorRequest("Tones", null, -5, null), "numberOfQuestions");
    }

    @Test
    void levelCheckQuestionRequestStripsUnicodeSpacesAndCopiesOptionsImmutably() {
        List<LevelCheckQuestionData.Option> mutableOptions = new ArrayList<>(List.of(
                new LevelCheckQuestionData.Option(ideographicWrapped("Hello"), true, ideographicWrapped("Greeting")),
                new LevelCheckQuestionData.Option("Goodbye", false, IDEOGRAPHIC_SPACE)));
        LevelCheckQuestionData.Request blankPrompt = new LevelCheckQuestionData.Request(
                IDEOGRAPHIC_SPACE, IDEOGRAPHIC_SPACE, IDEOGRAPHIC_SPACE, IDEOGRAPHIC_SPACE,
                LevelCheckSkill.VOCABULARY, ActivityDifficulty.EASY, LevelCheckLevel.BEGINNER_1,
                PublishStatus.DRAFT, mutableOptions);
        mutableOptions.clear();

        assertViolationPathsContain(blankPrompt, "prompt");
        assertThat(blankPrompt.hanzi()).isNull();
        assertThat(blankPrompt.pinyin()).isNull();
        assertThat(blankPrompt.audioText()).isNull();
        assertThat(blankPrompt.options()).hasSize(2);
        assertThat(blankPrompt.options().get(0).text()).isEqualTo("Hello");
        assertThat(blankPrompt.options().get(0).explanation()).isEqualTo("Greeting");
        assertThat(blankPrompt.options().get(1).explanation()).isNull();
        assertThatThrownBy(() -> blankPrompt.options().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void quizAnswerRequestsBoundTheirFields() {
        QuizSubmissionAnswerRequest answerRequest = new QuizSubmissionAnswerRequest(0L, null, "A".repeat(2001));

        assertViolationPathsContain(answerRequest, "questionId", "answerText");
    }

    @Test
    void quizAggregateRequestsRejectUnboundedNestedCollections() {
        List<QuizOptionRequest> options = IntStream.rangeClosed(1, TOO_MANY_ITEMS)
                .mapToObj(index -> new QuizOptionRequest("Option " + index, index == 1, null))
                .toList();

        List<ClassRequests.ChosenPair> pairs = IntStream.rangeClosed(1, 51)
                .mapToObj(index -> new ClassRequests.ChosenPair((long) index, "meaning " + index))
                .toList();
        ClassRequests.MatchingAnswer matchingAnswer = new ClassRequests.MatchingAnswer(pairs);

        assertViolationPathsContain(matchingAnswer, "pairs");
        assertViolationPathsContain(questionWithOptions(options), "options");
    }

    @Test
    void dtoConstructorsNormalizeBoundaryTextBeforeValidation() {
        ClassRequests.ClassDetails classDetails = new ClassRequests.ClassDetails(
                ideographicWrapped("Class title"),
                IDEOGRAPHIC_SPACE,
                1L,
                null);
        ClassRequests.KeyPhrase keyPhrase = new ClassRequests.KeyPhrase(
                ideographicWrapped("你好"), ideographicWrapped("nǐ hǎo"), ideographicWrapped("hello"));
        AiChatRequest chatRequest = new AiChatRequest(
                ideographicWrapped("你好"),
                ideographicWrapped("zh"),
                null);
        AiStoryGeneratorRequest storyGeneratorRequest = new AiStoryGeneratorRequest(
                ideographicWrapped("food"),
                ideographicWrapped("en"));
        XiaomeiReplyRequest xiaomeiRequest = new XiaomeiReplyRequest(
                XiaomeiScenario.CAFE,
                List.of(new XiaomeiTurn(XiaomeiTurn.Speaker.XIAOMEI, ideographicWrapped("你好！"))),
                ideographicWrapped("我要一杯咖啡"),
                ideographicWrapped("en"));
        CreateAiResponseReportRequest aiReportRequest = new CreateAiResponseReportRequest(
                null,
                null,
                ideographicWrapped("CHAT_TUTOR"),
                ideographicWrapped("OTHER"),
                ideographicWrapped("Description"),
                ideographicWrapped("Prompt"),
                ideographicWrapped("Response"));
        ReviewAiResponseReportRequest reportReviewRequest = new ReviewAiResponseReportRequest(
                ideographicWrapped("RESOLVED"),
                ideographicWrapped("Handled"));
        QuizRequest quizRequest = new QuizRequest(ideographicWrapped("Quiz 1"), null);
        QuizOptionRequest optionRequest = new QuizOptionRequest(ideographicWrapped("Option A"), true, null);
        QuizSubmissionAnswerRequest quizAnswerRequest =
                new QuizSubmissionAnswerRequest(1L, null, ideographicWrapped("Answer A"));
        AdminAiSettingsUpdateRequest settingsRequest = new AdminAiSettingsUpdateRequest(
                ideographicWrapped("AI can make mistakes"),
                true,
                true);
        AiQuizGeneratorRequest quizGeneratorRequest = new AiQuizGeneratorRequest(
                ideographicWrapped("tones"),
                ideographicWrapped("beginner"),
                3,
                " en ");
        QuizDraftRequest quizDraftRequest = new QuizDraftRequest(
                1L,
                ideographicWrapped("greetings"),
                ideographicWrapped("beginner"),
                2,
                " en ");

        assertThat(classDetails.title()).isEqualTo("Class title");
        assertThat(classDetails.description()).isNull();
        assertThat(keyPhrase.chinese()).isEqualTo("你好");
        assertThat(keyPhrase.pinyin()).isEqualTo("nǐ hǎo");
        assertThat(keyPhrase.meaning()).isEqualTo("hello");
        assertThat(chatRequest.message()).isEqualTo("你好");
        assertThat(chatRequest.languagePreference()).isEqualTo("zh");
        assertThat(storyGeneratorRequest.topic()).isEqualTo("food");
        assertThat(storyGeneratorRequest.languagePreference()).isEqualTo("en");
        assertThat(xiaomeiRequest.message()).isEqualTo("我要一杯咖啡");
        assertThat(xiaomeiRequest.turns().get(0).text()).isEqualTo("你好！");
        assertThat(xiaomeiRequest.languagePreference()).isEqualTo("en");
        assertThat(aiReportRequest.featureKey()).isEqualTo("CHAT_TUTOR");
        assertThat(aiReportRequest.reasonCategory()).isEqualTo("OTHER");
        assertThat(aiReportRequest.description()).isEqualTo("Description");
        assertThat(aiReportRequest.promptSnapshot()).isEqualTo("Prompt");
        assertThat(aiReportRequest.aiResponseSnapshot()).isEqualTo("Response");
        assertThat(reportReviewRequest.status()).isEqualTo("RESOLVED");
        assertThat(reportReviewRequest.reviewerNote()).isEqualTo("Handled");
        assertThat(quizRequest.title()).isEqualTo("Quiz 1");
        assertThat(optionRequest.optionText()).isEqualTo("Option A");
        assertThat(quizAnswerRequest.answerText()).isEqualTo("Answer A");
        assertThat(settingsRequest.aiNoticeText()).isEqualTo("AI can make mistakes");
        assertThat(quizGeneratorRequest.topic()).isEqualTo("tones");
        assertThat(quizGeneratorRequest.level()).isEqualTo("beginner");
        assertThat(quizGeneratorRequest.languagePreference()).isEqualTo("en");
        assertThat(quizDraftRequest.topic()).isEqualTo("greetings");
        assertThat(quizDraftRequest.level()).isEqualTo("beginner");
        assertThat(quizDraftRequest.languagePreference()).isEqualTo("en");
    }

    @Test
    void listBackedRequestDtosAreDefensivelyCopiedAndImmutable() {
        List<XiaomeiTurn> turns = new ArrayList<>(List.of(
                new XiaomeiTurn(XiaomeiTurn.Speaker.XIAOMEI, ideographicWrapped("你好！"))));
        XiaomeiConversationRequest conversationRequest = new XiaomeiConversationRequest(
                XiaomeiScenario.MEET_XIAOMEI, turns, " en ");
        turns.add(new XiaomeiTurn(XiaomeiTurn.Speaker.STUDENT, "后来的"));

        assertThat(conversationRequest.turns()).hasSize(1);
        assertThat(conversationRequest.turns().get(0).text()).isEqualTo("你好！");
        assertThat(conversationRequest.languagePreference()).isEqualTo("en");
        assertThatThrownBy(() -> conversationRequest.turns().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new XiaomeiConversationRequest(XiaomeiScenario.MEET_XIAOMEI, null, null).turns()).isEmpty();
    }

    @Test
    void aggregateRequestsRejectNullNestedElementsThroughBeanValidation() {
        List<HintPolicyDto.Level> hintLevels = new ArrayList<>(List.of(
                new HintPolicyDto.Level("HSK1", 15, 0),
                new HintPolicyDto.Level("HSK2", 30, 3)));
        hintLevels.add(null);
        ClassRequests.MatchingAnswer matchingAnswer = new ClassRequests.MatchingAnswer(singletonList(null));

        assertViolationPathStartsWith(new HintPolicyDto.UpdateRequest(hintLevels), "levels");
        assertViolationPathsContain(new ClassRequests.MatchingAnswer(null), "pairs");
        assertViolationPathStartsWith(matchingAnswer, "pairs");
        assertViolationPathStartsWith(questionWithOptions(singletonList(null)), "options");
        assertViolationPathStartsWith(new LevelCheckNextRequest(null, singletonList(null), null), "answers");
        assertViolationPathStartsWith(new PracticeCompleteRequest(singletonList(null), null), "answers");
        assertViolationPathStartsWith(
                new XiaomeiConversationRequest(XiaomeiScenario.CAFE, singletonList(null), "en"), "turns");
    }

    @Test
    void freeTextAndAnswerDtosStripInvisibleControlsAndNormalizeLineBreaks() {
        assertViolationPathsContain(new AdminAiSettingsUpdateRequest("\u200B\u202E", true, true), "aiNoticeText");
        assertThat(new AdminAiSettingsUpdateRequest("Line 1\r\nLine 2\u200B", true, true).aiNoticeText())
                .isEqualTo("Line 1\nLine 2");

        assertViolationPathsContain(new AdminDeactivationRequest("\u0007\u202E"), "reason");
        assertThat(new AdminReactivationRequest("\u200B\u0000").reason()).isNull();

        CreateSupportRequest normalizedSupport = new CreateSupportRequest(
                SupportTopic.ACCOUNT, "  Login\r\nissue\u200B  ", "First\r\nSecond\u200B");
        assertThat(normalizedSupport.subject()).isEqualTo("Login issue");
        assertThat(normalizedSupport.message()).isEqualTo("First\nSecond");
        assertViolationPathsContain(
                new CreateSupportRequest(SupportTopic.ACCOUNT, "\u0000\u200B", "\u202E"), "subject", "message");

        assertThat(new PracticeItemAnswer(" item-1 ", ideographicWrapped("GOT_IT"), null, false).answer())
                .isEqualTo("GOT_IT");
        assertThat(new PracticeItemAnswer("item-1", IDEOGRAPHIC_SPACE, null, false).answer()).isNull();
        assertThat(new QuestionAnswer(1L, ideographicWrapped("你好")).answer()).isEqualTo("你好");
        assertThat(new QuestionAnswer(1L, IDEOGRAPHIC_SPACE).answer()).isNull();
    }

    private static QuizQuestionRequest questionWithOptions(List<QuizOptionRequest> options) {
        return new QuizQuestionRequest(MULTIPLE_CHOICE, QUESTION_PROMPT, null, 5, options,
                null, null, null, null, null, null);
    }

    private static void assertViolationPathsContain(Object request, String... paths) {
        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(paths);
    }

    private static void assertViolationPathStartsWith(Object request, String prefix) {
        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.startsWith(prefix + "["));
    }

    private static String ideographicWrapped(String value) {
        return IDEOGRAPHIC_SPACE + value + IDEOGRAPHIC_SPACE;
    }
}
