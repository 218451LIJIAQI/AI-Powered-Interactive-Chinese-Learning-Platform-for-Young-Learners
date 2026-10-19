package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.admin.ApproveTeacherRequest;
import com.example.aichinese.dto.admin.RejectTeacherRequest;
import com.example.aichinese.dto.admin.activity.AdminDragDropActivityRequest;
import com.example.aichinese.dto.admin.activity.AdminFlashcardRequest;
import com.example.aichinese.dto.admin.activity.AdminStoryActivityRequest;
import com.example.aichinese.dto.admin.activity.ActivityType;
import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiSentencePracticeRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.AiVocabularyHelperRequest;
import com.example.aichinese.dto.ai.CreateAiResponseReportRequest;
import com.example.aichinese.dto.ai.ReviewAiResponseReportRequest;
import com.example.aichinese.dto.communication.CreateTeacherFeedbackRequest;
import com.example.aichinese.dto.content.ContentApprovalRequest;
import com.example.aichinese.dto.content.CreateLessonRequest;
import com.example.aichinese.dto.learning.CreateLiveClassRequest;
import com.example.aichinese.dto.learning.CreateQuizRequest;
import com.example.aichinese.dto.learning.CreateQuizQuestionRequest;
import com.example.aichinese.dto.learning.QuizOptionRequest;
import com.example.aichinese.dto.learning.QuizSubmissionAnswerRequest;
import com.example.aichinese.dto.learning.RecordedLessonVideoRequest;
import com.example.aichinese.dto.learning.SubmitQuizAttemptRequest;
import com.example.aichinese.dto.learning.UpdateLiveClassStatusRequest;
import com.example.aichinese.dto.learning.UpdateLiveClassRequest;
import com.example.aichinese.dto.learning.UpdateQuizQuestionRequest;
import com.example.aichinese.dto.learning.UpdateQuizRequest;
import com.example.aichinese.dto.system.AdminPlatformSettingsUpdateRequest;
import com.example.aichinese.dto.teacher.UpdateTeacherProfileRequest;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LearningType;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Pins the Jakarta Validation annotations declared on every public
 * request DTO. Acts as a contract guard so a future refactor that
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
        CreateLessonRequest request = new CreateLessonRequest(
                "Lesson",
                "Description",
                -1L,
                0L,
                -3L,
                LearningType.RECORDED,
                10,
                "Content",
                null);

        assertViolationPathsContain(request, "moduleId", "levelId", "categoryId");
    }

    @Test
    void aiRequestIdsMustBePositive() {
        AiChatRequest chatRequest = new AiChatRequest("你好", "en", -1L, null, 0L);
        CreateAiResponseReportRequest reportRequest = new CreateAiResponseReportRequest(
                -1L,
                0L,
                -3L,
                "CHAT_TUTOR",
                "INCORRECT_CONTENT",
                null,
                null,
                "Response snapshot");

        assertViolationPathsContain(chatRequest, "sessionId", "relatedLessonId");
        assertViolationPathsContain(reportRequest, "aiUsageLogId", "chatSessionId", "chatMessageId");
    }

    @Test
    void feedbackShortFieldsAreBounded() {
        CreateTeacherFeedbackRequest feedbackRequest = new CreateTeacherFeedbackRequest(
                0L,
                -1L,
                -2L,
                -3L,
                "Z".repeat(31),
                "Useful feedback message");

        assertViolationPathsContain(feedbackRequest, "studentId", "lessonId", "quizAttemptId", "practiceSubmissionId", "feedbackType");
    }

    @Test
    void learningRequestsRejectUnsafeLinksAndOversizedAnswers() {
        UpdateLiveClassRequest liveClassRequest = new UpdateLiveClassRequest(
                "Class",
                "Description",
                -1L,
                -2L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(1),
                "X".repeat(65),
                UNSAFE_MEETING_LINK,
                "M".repeat(81),
                "S".repeat(31),
                10);
        QuizSubmissionAnswerRequest answerRequest = new QuizSubmissionAnswerRequest(0L, "A".repeat(2001));

        assertViolationPathsContain(
                liveClassRequest,
                "lessonId",
                "packageId",
                "timeZone",
                "meetingLink",
                "meetingPlatform",
                "status");
        assertViolationPathsContain(answerRequest, "questionId", "answerText");
    }

    @Test
    void quizAggregateRequestsRejectUnboundedNestedCollections() {
        List<QuizSubmissionAnswerRequest> answers = IntStream.rangeClosed(1, TOO_MANY_ITEMS)
                .mapToObj(index -> new QuizSubmissionAnswerRequest((long) index, "answer"))
                .toList();
        List<QuizOptionRequest> options = IntStream.rangeClosed(1, TOO_MANY_ITEMS)
                .mapToObj(index -> new QuizOptionRequest("Option " + index, index == 1, index))
                .toList();

        SubmitQuizAttemptRequest attemptRequest = new SubmitQuizAttemptRequest(answers);
        CreateQuizQuestionRequest createQuestionRequest = new CreateQuizQuestionRequest(
                MULTIPLE_CHOICE,
                QUESTION_PROMPT,
                null,
                5,
                0,
                options,
                null,
                null);
        UpdateQuizQuestionRequest updateQuestionRequest = new UpdateQuizQuestionRequest(
                MULTIPLE_CHOICE,
                QUESTION_PROMPT,
                null,
                5,
                0,
                options,
                null,
                null);

        assertViolationPathsContain(attemptRequest, "answers");
        assertViolationPathsContain(createQuestionRequest, "options");
        assertViolationPathsContain(updateQuestionRequest, "options");
    }

    @Test
    void dtoConstructorsNormalizeBoundaryTextBeforeValidation() {
        CreateLessonRequest lessonRequest = new CreateLessonRequest(
                ideographicWrapped("Lesson title"),
                IDEOGRAPHIC_SPACE,
                null,
                1L,
                1L,
                LearningType.RECORDED,
                10,
                "  Lesson body  ",
                IDEOGRAPHIC_SPACE);
        ContentApprovalRequest approvalRequest = new ContentApprovalRequest(ideographicWrapped("Needs review"));
        CreateTeacherFeedbackRequest feedbackRequest = new CreateTeacherFeedbackRequest(
                1L,
                null,
                null,
                null,
                ideographicWrapped("quiz"),
                ideographicWrapped("Thoughtful feedback"));
        ApproveTeacherRequest approveTeacherRequest = new ApproveTeacherRequest(ideographicWrapped("Ready"));
        RejectTeacherRequest rejectTeacherRequest = new RejectTeacherRequest(ideographicWrapped("Missing certificate"));
        AiChatRequest chatRequest = new AiChatRequest(
                ideographicWrapped("你好"),
                ideographicWrapped("zh"),
                null,
                ideographicWrapped("Lesson 1"),
                null);
        AiSentencePracticeRequest sentencePracticeRequest = new AiSentencePracticeRequest(
                ideographicWrapped("我喜欢中文"),
                ideographicWrapped("beginner"),
                ideographicWrapped("en"));
        AiStoryGeneratorRequest storyGeneratorRequest = new AiStoryGeneratorRequest(
                ideographicWrapped("food"),
                ideographicWrapped("HSK 1"),
                ideographicWrapped("en"),
                ideographicWrapped("short"));
        AiVocabularyHelperRequest vocabularyHelperRequest = new AiVocabularyHelperRequest(
                ideographicWrapped("apple"),
                ideographicWrapped("en"),
                ideographicWrapped("zh"),
                ideographicWrapped("beginner"),
                ideographicWrapped("ms"));
        CreateAiResponseReportRequest aiReportRequest = new CreateAiResponseReportRequest(
                null,
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
        CreateLiveClassRequest createLiveClassRequest = new CreateLiveClassRequest(
                ideographicWrapped("Tone practice"),
                IDEOGRAPHIC_SPACE,
                null,
                null,
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(1),
                ideographicWrapped("Asia/Singapore"),
                ideographicWrapped("https://example.com/class"),
                ideographicWrapped("zoom"),
                IDEOGRAPHIC_SPACE,
                10);
        UpdateLiveClassStatusRequest liveClassStatusRequest =
                new UpdateLiveClassStatusRequest(ideographicWrapped("ONGOING"));
        CreateQuizRequest createQuizRequest = new CreateQuizRequest(ideographicWrapped("Quiz 1"), IDEOGRAPHIC_SPACE, 0, null);
        UpdateQuizRequest updateQuizRequest = new UpdateQuizRequest(
                ideographicWrapped("Quiz 2"),
                ideographicWrapped("Updated"),
                5,
                null);
        QuizOptionRequest optionRequest = new QuizOptionRequest(ideographicWrapped("Option A"), true, 0);
        QuizSubmissionAnswerRequest quizAnswerRequest =
                new QuizSubmissionAnswerRequest(1L, ideographicWrapped("Answer A"));
        RecordedLessonVideoRequest recordedVideoRequest = new RecordedLessonVideoRequest(
                ideographicWrapped("Video 1"),
                IDEOGRAPHIC_SPACE,
                null,
                ideographicWrapped("EXTERNAL_URL"),
                ideographicWrapped("https://example.com/video"),
                null,
                0);
        AdminPlatformSettingsUpdateRequest settingsRequest = new AdminPlatformSettingsUpdateRequest(
                ideographicWrapped("AI can make mistakes"),
                true,
                true);

        assertThat(lessonRequest.title()).isEqualTo("Lesson title");
        assertThat(lessonRequest.description()).isNull();
        assertThat(lessonRequest.basicContent()).isEqualTo("Lesson body");
        assertThat(lessonRequest.coverImageUrl()).isNull();
        assertThat(approvalRequest.note()).isEqualTo("Needs review");
        assertThat(feedbackRequest.feedbackType()).isEqualTo("quiz");
        assertThat(feedbackRequest.message()).isEqualTo("Thoughtful feedback");
        assertThat(approveTeacherRequest.note()).isEqualTo("Ready");
        assertThat(rejectTeacherRequest.reason()).isEqualTo("Missing certificate");
        assertThat(chatRequest.message()).isEqualTo("你好");
        assertThat(chatRequest.languagePreference()).isEqualTo("zh");
        assertThat(chatRequest.context()).isEqualTo("Lesson 1");
        assertThat(sentencePracticeRequest.sentence()).isEqualTo("我喜欢中文");
        assertThat(sentencePracticeRequest.learnerLevel()).isEqualTo("beginner");
        assertThat(storyGeneratorRequest.topic()).isEqualTo("food");
        assertThat(storyGeneratorRequest.lengthPreference()).isEqualTo("short");
        assertThat(vocabularyHelperRequest.word()).isEqualTo("apple");
        assertThat(vocabularyHelperRequest.outputLanguage()).isEqualTo("zh");
        assertThat(aiReportRequest.featureKey()).isEqualTo("CHAT_TUTOR");
        assertThat(aiReportRequest.promptSnapshot()).isEqualTo("Prompt");
        assertThat(reportReviewRequest.status()).isEqualTo("RESOLVED");
        assertThat(reportReviewRequest.reviewerNote()).isEqualTo("Handled");
        assertThat(createLiveClassRequest.title()).isEqualTo("Tone practice");
        assertThat(createLiveClassRequest.description()).isNull();
        assertThat(createLiveClassRequest.timeZone()).isEqualTo("Asia/Singapore");
        assertThat(createLiveClassRequest.meetingLink()).isEqualTo("https://example.com/class");
        assertThat(createLiveClassRequest.status()).isNull();
        assertThat(liveClassStatusRequest.status()).isEqualTo("ONGOING");
        assertThat(createQuizRequest.title()).isEqualTo("Quiz 1");
        assertThat(createQuizRequest.description()).isNull();
        assertThat(updateQuizRequest.title()).isEqualTo("Quiz 2");
        assertThat(updateQuizRequest.description()).isEqualTo("Updated");
        assertThat(optionRequest.optionText()).isEqualTo("Option A");
        assertThat(quizAnswerRequest.answerText()).isEqualTo("Answer A");
        assertThat(recordedVideoRequest.title()).isEqualTo("Video 1");
        assertThat(recordedVideoRequest.description()).isNull();
        assertThat(recordedVideoRequest.sourceType()).isEqualTo("EXTERNAL_URL");
        assertThat(recordedVideoRequest.externalVideoUrl()).isEqualTo("https://example.com/video");
        assertThat(settingsRequest.aiDisclaimerText()).isEqualTo("AI can make mistakes");
        assertThat(ActivityType.parseLenient(ideographicWrapped("matching-game"))).contains(ActivityType.MATCHING_GAME);
    }

    @Test
    void listBackedRequestDtosAreDefensivelyCopiedAndImmutable() {
        List<String> tags = mutableList(ideographicWrapped("tone"), "pinyin");
        AdminFlashcardRequest flashcardRequest = new AdminFlashcardRequest(
                IDEOGRAPHIC_SPACE,
                "  你  ",
                "  you  ",
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                1,
                ActivityDifficulty.EASY,
                "  greetings  ",
                tags,
                10,
                30,
                null);
        tags.add("late mutation");

        assertThat(flashcardRequest.title()).isNull();
        assertThat(flashcardRequest.frontText()).isEqualTo("你");
        assertThat(flashcardRequest.backText()).isEqualTo("you");
        assertThat(flashcardRequest.topic()).isEqualTo("greetings");
        assertThat(flashcardRequest.tags()).containsExactly("tone", "pinyin");
        assertListIsImmutable(flashcardRequest.tags());

        List<String> questionTypes = mutableList(spaced(MULTIPLE_CHOICE));
        AiQuizGeneratorRequest quizGeneratorRequest = new AiQuizGeneratorRequest(
                ideographicWrapped("tones"),
                ideographicWrapped("beginner"),
                null,
                3,
                questionTypes,
                " en ");
        questionTypes.add("TRUE_FALSE");

        assertThat(quizGeneratorRequest.topic()).isEqualTo("tones");
        assertThat(quizGeneratorRequest.level()).isEqualTo("beginner");
        assertThat(quizGeneratorRequest.questionTypes()).containsExactly(MULTIPLE_CHOICE);
        assertListIsImmutable(quizGeneratorRequest.questionTypes());

        List<String> teachingCategories = mutableList("  HSK 1  ", "  ");
        UpdateTeacherProfileRequest teacherProfileRequest = new UpdateTeacherProfileRequest(
                "  Teacher Example  ",
                "  ",
                IDEOGRAPHIC_SPACE,
                teachingCategories);
        teachingCategories.add("late mutation");

        assertThat(teacherProfileRequest.fullName()).isEqualTo("Teacher Example");
        assertThat(teacherProfileRequest.bio()).isNull();
        assertThat(teacherProfileRequest.shortIntroduction()).isNull();
        assertThat(teacherProfileRequest.teachingCategories()).containsExactly("HSK 1", "");
        assertViolationPathStartsWith(teacherProfileRequest, "teachingCategories");
        assertListIsImmutable(teacherProfileRequest.teachingCategories());
    }

    @Test
    void aggregateRequestsRejectNullNestedElementsThroughBeanValidation() {
        SubmitQuizAttemptRequest attemptRequest = new SubmitQuizAttemptRequest(nullElementList());
        CreateQuizQuestionRequest createQuestionRequest = new CreateQuizQuestionRequest(
                MULTIPLE_CHOICE,
                QUESTION_PROMPT,
                null,
                5,
                0,
                nullElementList(),
                null,
                null);
        UpdateQuizQuestionRequest updateQuestionRequest = new UpdateQuizQuestionRequest(
                MULTIPLE_CHOICE,
                QUESTION_PROMPT,
                null,
                5,
                0,
                nullElementList(),
                null,
                null);
        AdminDragDropActivityRequest dragDropRequest = new AdminDragDropActivityRequest(
                "Drag",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                nullElementList(),
                null);
        AdminStoryActivityRequest storyRequest = new AdminStoryActivityRequest(
                "Story",
                null,
                "故事",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                nullElementList(),
                null);

        assertViolationPathStartsWith(attemptRequest, "answers");
        assertViolationPathStartsWith(createQuestionRequest, "options");
        assertViolationPathStartsWith(updateQuestionRequest, "options");
        assertViolationPathStartsWith(dragDropRequest, "items");
        assertViolationPathStartsWith(storyRequest, "questions");
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

    private static void assertListIsImmutable(List<String> values) {
        assertThatThrownBy(() -> values.add("blocked"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static List<String> mutableList(String... values) {
        return new ArrayList<>(List.of(values));
    }

    private static String ideographicWrapped(String value) {
        return IDEOGRAPHIC_SPACE + value + IDEOGRAPHIC_SPACE;
    }

    private static String spaced(String value) {
        return "  " + value + "  ";
    }

    private static <T> List<T> nullElementList() {
        List<T> values = new ArrayList<>();
        values.add(null);
        return values;
    }
}
