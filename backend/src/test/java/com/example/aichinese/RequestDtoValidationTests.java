package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.example.aichinese.dto.admin.ApproveTeacherRequest;
import com.example.aichinese.dto.ai.AdminAiSettingsUpdateRequest;
import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.CreateAiResponseReportRequest;
import com.example.aichinese.dto.ai.ReviewAiResponseReportRequest;
import com.example.aichinese.dto.ai.XiaomeiConversationRequest;
import com.example.aichinese.dto.ai.XiaomeiReplyRequest;
import com.example.aichinese.dto.ai.XiaomeiTurn;
import com.example.aichinese.dto.auth.RegisterTeacherRequest;
import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.learning.QuizOptionRequest;
import com.example.aichinese.dto.learning.QuizQuestionRequest;
import com.example.aichinese.dto.learning.QuizRequest;
import com.example.aichinese.dto.learning.QuizSubmissionAnswerRequest;
import com.example.aichinese.dto.review.RejectionReasonRequest;
import com.example.aichinese.dto.teacher.UpdateTeacherProfileRequest;
import com.example.aichinese.entity.enums.XiaomeiScenario;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Pins the Jakarta Validation annotations declared on the content,
 * learning, AI, feedback, teacher-profile, teacher-registration, activity
 * and settings request DTOs. Acts as a contract guard so a future refactor that
 * loosens a {@code @NotBlank} / {@code @Size} / {@code @Email} /
 * {@code @Min} constraint trips an explicit unit failure rather than
 * silently widening the API attack surface.
 */
class RequestDtoValidationTests {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    private static final String IDEOGRAPHIC_SPACE = "\u3000";
    private static final String MULTIPLE_CHOICE = "MULTIPLE_CHOICE";
    private static final String QUESTION_PROMPT = "Choose the correct answer.";
    private static final String UNSAFE_MEETING_LINK = "javascript:alert(1)";
    private static final int TOO_MANY_ITEMS = 201;

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void contentRequestIdsMustBePositive() {
        ClassRequests.ClassDetails details = new ClassRequests.ClassDetails("Class", "Description", 0L, -3L, null);
        ClassRequests.LessonContent content = new ClassRequests.LessonContent("Lesson", -1L, 0, null, List.of());

        assertViolationPathsContain(details, "levelId", "topicId");
        assertViolationPathsContain(content, "unitId", "minutes");
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
    void feedbackShortFieldsAreBounded() {
        ClassRequests.Feedback feedbackRequest = new ClassRequests.Feedback(
                0L,
                -3L,
                -1L,
                -2L,
                "Z".repeat(51),
                "Useful feedback message");

        assertViolationPathsContain(feedbackRequest, "studentId", "classId", "lessonId", "quizAttemptId", "type");
    }

    @Test
    void learningRequestsRejectUnsafeLinksAndOversizedAnswers() {
        ClassRequests.LiveSession liveSession = new ClassRequests.LiveSession(
                "Session",
                -1L,
                -2L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(1),
                "X".repeat(65),
                0,
                UNSAFE_MEETING_LINK + "x".repeat(500),
                "M".repeat(41));
        QuizSubmissionAnswerRequest answerRequest = new QuizSubmissionAnswerRequest(0L, null, "A".repeat(2001));

        assertViolationPathsContain(
                liveSession,
                "classId",
                "lessonId",
                "timeZone",
                "capacity",
                "meetingLink",
                "meetingPlatform");
        assertViolationPathsContain(answerRequest, "questionId", "answerText");
    }

    @Test
    void quizAggregateRequestsRejectUnboundedNestedCollections() {
        List<QuizOptionRequest> options = IntStream.rangeClosed(1, TOO_MANY_ITEMS)
                .mapToObj(index -> new QuizOptionRequest("Option " + index, index == 1))
                .toList();

        List<ClassRequests.ChosenPair> pairs = IntStream.rangeClosed(1, 51)
                .mapToObj(index -> new ClassRequests.ChosenPair((long) index, "meaning " + index))
                .toList();
        ClassRequests.MatchingAnswer matchingAnswer = new ClassRequests.MatchingAnswer(pairs);
        QuizQuestionRequest questionRequest = new QuizQuestionRequest(
                MULTIPLE_CHOICE,
                QUESTION_PROMPT,
                null,
                5,
                options,
                null,
                null,
                null,
                null);

        assertViolationPathsContain(matchingAnswer, "pairs");
        assertViolationPathsContain(questionRequest, "options");
    }

    @Test
    void dtoConstructorsNormalizeBoundaryTextBeforeValidation() {
        ClassRequests.ClassDetails classDetails = new ClassRequests.ClassDetails(
                ideographicWrapped("Class title"),
                IDEOGRAPHIC_SPACE,
                1L,
                1L,
                null);
        ClassRequests.KeyPhrase keyPhrase = new ClassRequests.KeyPhrase(
                ideographicWrapped("你好"), ideographicWrapped("nǐ hǎo"), ideographicWrapped("hello"));
        ClassRequests.Feedback feedbackRequest = new ClassRequests.Feedback(
                1L,
                2L,
                null,
                null,
                ideographicWrapped("ENCOURAGING"),
                ideographicWrapped("Thoughtful feedback"));
        ApproveTeacherRequest approveTeacherRequest = new ApproveTeacherRequest(ideographicWrapped("Ready"));
        RejectionReasonRequest rejectTeacherRequest = new RejectionReasonRequest(ideographicWrapped("Missing certificate"));
        AiChatRequest chatRequest = new AiChatRequest(
                ideographicWrapped("你好"),
                ideographicWrapped("zh"),
                null);
        AiStoryGeneratorRequest storyGeneratorRequest = new AiStoryGeneratorRequest(
                ideographicWrapped("food"),
                ideographicWrapped("en"));
        XiaomeiReplyRequest xiaomeiRequest = new XiaomeiReplyRequest(
                com.example.aichinese.entity.enums.XiaomeiScenario.CAFE,
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
        ClassRequests.LiveSession liveSession = new ClassRequests.LiveSession(
                ideographicWrapped("Tone practice"),
                1L,
                null,
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(1),
                ideographicWrapped("Asia/Singapore"),
                10,
                ideographicWrapped("https://example.com/class"),
                ideographicWrapped("zoom"));
        QuizRequest quizRequest = new QuizRequest(ideographicWrapped("Quiz 1"), IDEOGRAPHIC_SPACE, 0, null);
        QuizRequest describedQuizRequest = new QuizRequest(
                ideographicWrapped("Quiz 2"),
                ideographicWrapped("Updated"),
                5,
                null);
        QuizOptionRequest optionRequest = new QuizOptionRequest(ideographicWrapped("Option A"), true);
        QuizSubmissionAnswerRequest quizAnswerRequest =
                new QuizSubmissionAnswerRequest(1L, null, ideographicWrapped("Answer A"));
        AdminAiSettingsUpdateRequest settingsRequest = new AdminAiSettingsUpdateRequest(
                ideographicWrapped("AI can make mistakes"),
                true,
                true);

        assertThat(classDetails.title()).isEqualTo("Class title");
        assertThat(classDetails.description()).isNull();
        assertThat(keyPhrase.chinese()).isEqualTo("你好");
        assertThat(keyPhrase.pinyin()).isEqualTo("nǐ hǎo");
        assertThat(keyPhrase.meaning()).isEqualTo("hello");
        assertThat(feedbackRequest.type()).isEqualTo("ENCOURAGING");
        assertThat(feedbackRequest.message()).isEqualTo("Thoughtful feedback");
        assertThat(approveTeacherRequest.note()).isEqualTo("Ready");
        assertThat(rejectTeacherRequest.reason()).isEqualTo("Missing certificate");
        assertThat(chatRequest.message()).isEqualTo("你好");
        assertThat(chatRequest.languagePreference()).isEqualTo("zh");
        assertThat(storyGeneratorRequest.topic()).isEqualTo("food");
        assertThat(storyGeneratorRequest.languagePreference()).isEqualTo("en");
        assertThat(xiaomeiRequest.message()).isEqualTo("我要一杯咖啡");
        assertThat(xiaomeiRequest.turns().get(0).text()).isEqualTo("你好！");
        assertThat(xiaomeiRequest.languagePreference()).isEqualTo("en");
        assertThat(aiReportRequest.featureKey()).isEqualTo("CHAT_TUTOR");
        assertThat(aiReportRequest.promptSnapshot()).isEqualTo("Prompt");
        assertThat(reportReviewRequest.status()).isEqualTo("RESOLVED");
        assertThat(reportReviewRequest.reviewerNote()).isEqualTo("Handled");
        assertThat(liveSession.title()).isEqualTo("Tone practice");
        assertThat(liveSession.timeZone()).isEqualTo("Asia/Singapore");
        assertThat(liveSession.meetingLink()).isEqualTo("https://example.com/class");
        assertThat(liveSession.meetingPlatform()).isEqualTo("zoom");
        assertThat(quizRequest.title()).isEqualTo("Quiz 1");
        assertThat(quizRequest.description()).isNull();
        assertThat(describedQuizRequest.title()).isEqualTo("Quiz 2");
        assertThat(describedQuizRequest.description()).isEqualTo("Updated");
        assertThat(optionRequest.optionText()).isEqualTo("Option A");
        assertThat(quizAnswerRequest.answerText()).isEqualTo("Answer A");
        assertThat(settingsRequest.aiNoticeText()).isEqualTo("AI can make mistakes");
    }

    @Test
    void requestDtosNormaliseTheirTextBeforeValidation() {
        AiQuizGeneratorRequest quizGeneratorRequest = new AiQuizGeneratorRequest(
                ideographicWrapped("tones"),
                ideographicWrapped("beginner"),
                3,
                " en ");

        assertThat(quizGeneratorRequest.topic()).isEqualTo("tones");
        assertThat(quizGeneratorRequest.level()).isEqualTo("beginner");
        assertThat(quizGeneratorRequest.languagePreference()).isEqualTo("en");

        UpdateTeacherProfileRequest teacherProfileRequest = new UpdateTeacherProfileRequest("  Teacher Example  ", "  ");

        assertThat(teacherProfileRequest.fullName()).isEqualTo("Teacher Example");
        assertThat(teacherProfileRequest.bio()).isNull();
        assertViolationPathStartsWith(teacherProfileRequest, "bio");
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
        ClassRequests.MatchingAnswer matchingAnswer = new ClassRequests.MatchingAnswer(nullElementList());
        QuizQuestionRequest questionRequest = new QuizQuestionRequest(
                MULTIPLE_CHOICE,
                QUESTION_PROMPT,
                null,
                5,
                nullElementList(),
                null,
                null,
                null,
                null);

        assertViolationPathStartsWith(matchingAnswer, "pairs");
        assertViolationPathStartsWith(questionRequest, "options");
    }

    static Stream<Arguments> teacherBios() {
        String required = "Teacher bio is required";
        String length = "Teacher bio must be between 20 and 1000 characters";
        return Stream.of(
                arguments(null, List.of(required)),
                arguments("   ", List.of(required)),
                arguments("I teach Mandarin.", List.of(length)),
                // The bio is trimmed before validation, so padding cannot reach the 20-character minimum.
                arguments("  " + "a".repeat(19) + "  ", List.of(length)),
                arguments("a".repeat(20), List.of()),
                arguments("a".repeat(1000), List.of()),
                arguments("a".repeat(1001), List.of(length)));
    }

    /** The teacher sign-up and the profile update share the bio rule: required, 20 to 1000 characters. */
    @ParameterizedTest
    @MethodSource("teacherBios")
    void teacherRegistrationAndProfileUpdateRequireABioOf20To1000Characters(String bio, List<String> violations) {
        assertThat(bioViolations(new RegisterTeacherRequest(
                "Teacher One", "teacher@example.com", "Password123!", "Password123!", bio)))
                .isEqualTo(violations);
        assertThat(bioViolations(new UpdateTeacherProfileRequest("Teacher One", bio)))
                .isEqualTo(violations);
    }

    /** "Starts in the future" is checked by the service in the class's own zone, not by the record. */
    @Test
    void liveSessionRequestLeavesTheFutureStartToTheService() {
        ClassRequests.LiveSession pastStart = new ClassRequests.LiveSession(
                "Session",
                1L,
                null,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1),
                "Pacific/Honolulu",
                null,
                "https://example.com/class",
                "ZOOM");

        assertThat(VALIDATOR.validate(pastStart)).isEmpty();
    }

    private static List<String> bioViolations(Object request) {
        return VALIDATOR.validate(request).stream()
                .filter(violation -> "bio".equals(violation.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessage)
                .toList();
    }

    private static void assertViolationPathsContain(Object request, String... paths) {
        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(paths);
    }

    private static void assertViolationPathStartsWith(Object request, String prefix) {
        assertThat(VALIDATOR.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.startsWith(prefix));
    }

    private static String ideographicWrapped(String value) {
        return IDEOGRAPHIC_SPACE + value + IDEOGRAPHIC_SPACE;
    }

    private static <T> List<T> nullElementList() {
        List<T> values = new ArrayList<>();
        values.add(null);
        return values;
    }
}
