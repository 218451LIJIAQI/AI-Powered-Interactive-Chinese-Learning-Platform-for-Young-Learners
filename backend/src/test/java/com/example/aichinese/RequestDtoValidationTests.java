package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.admin.ApproveTeacherRequest;
import com.example.aichinese.dto.admin.RejectTeacherRequest;
import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiLearningRecommendationRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiSentencePracticeRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.AiVocabularyHelperRequest;
import com.example.aichinese.dto.admin.activity.AdminDragDropActivityRequest;
import com.example.aichinese.dto.admin.activity.ActivityType;
import com.example.aichinese.dto.admin.activity.AdminFlashcardRequest;
import com.example.aichinese.dto.admin.activity.AdminStoryActivityRequest;
import com.example.aichinese.dto.commerce.CheckoutSessionRequest;
import com.example.aichinese.dto.commerce.CheckoutSessionRequest.CheckoutSource;
import com.example.aichinese.dto.ai.CreateAiResponseReportRequest;
import com.example.aichinese.dto.ai.ReviewAiResponseReportRequest;
import com.example.aichinese.dto.commerce.CartItemRequest;
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
import com.example.aichinese.dto.learning.UpdateQuizQuestionRequest;
import com.example.aichinese.dto.learning.UpdateLiveClassRequest;
import com.example.aichinese.dto.learning.UpdateQuizRequest;
import com.example.aichinese.dto.system.AdminPlatformSettingsUpdateRequest;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.AgeBand;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LearningType;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Pins the Jakarta Validation annotations declared on every public
 * request DTO. Acts as a contract guard so a future refactor that
 * loosens a {@code @NotBlank} / {@code @Size} / {@code @Email} /
 * {@code @Min} constraint trips an explicit unit failure rather than
 * silently widening the API attack surface.
 */
class RequestDtoValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void contentRequestIdsMustBePositive() {
        CreateLessonRequest request = new CreateLessonRequest(
                "Lesson",
                "Description",
                -1L,
                0L,
                -3L,
                LearningType.RECORDED,
                AccessType.PAID,
                BigDecimal.TEN,
                10,
                "Content",
                null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("moduleId", "levelId", "categoryId");
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

        assertThat(validator.validate(chatRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("sessionId", "relatedLessonId");
        assertThat(validator.validate(reportRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("aiUsageLogId", "chatSessionId", "chatMessageId");
    }

    @Test
    void commerceAndFeedbackShortFieldsAreBounded() {
        CartItemRequest cartItemRequest = new CartItemRequest("X".repeat(31), -1L, "Y".repeat(101));
        CreateTeacherFeedbackRequest feedbackRequest = new CreateTeacherFeedbackRequest(
                0L,
                -1L,
                -2L,
                "Z".repeat(31),
                "Useful feedback message");

        assertThat(validator.validate(cartItemRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("itemType", "itemId", "featureKey");
        assertThat(validator.validate(feedbackRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("studentId", "lessonId", "quizAttemptId", "feedbackType");
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
                "javascript:alert(1)",
                "M".repeat(81),
                "S".repeat(31),
                10);
        QuizSubmissionAnswerRequest answerRequest = new QuizSubmissionAnswerRequest(0L, "A".repeat(2001));

        assertThat(validator.validate(liveClassRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("lessonId", "packageId", "meetingLink", "meetingPlatform", "status");
        assertThat(validator.validate(answerRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("questionId", "answerText");
    }

    @Test
    void quizAggregateRequestsRejectUnboundedChildCollections() {
        List<QuizSubmissionAnswerRequest> answers = IntStream.rangeClosed(1, 201)
                .mapToObj(index -> new QuizSubmissionAnswerRequest((long) index, "answer"))
                .toList();
        List<QuizOptionRequest> options = IntStream.rangeClosed(1, 201)
                .mapToObj(index -> new QuizOptionRequest("Option " + index, index == 1, index))
                .toList();

        SubmitQuizAttemptRequest attemptRequest = new SubmitQuizAttemptRequest(answers);
        CreateQuizQuestionRequest createQuestionRequest = new CreateQuizQuestionRequest(
                "MULTIPLE_CHOICE",
                "Choose the correct answer.",
                null,
                5,
                0,
                options,
                null);
        UpdateQuizQuestionRequest updateQuestionRequest = new UpdateQuizQuestionRequest(
                "MULTIPLE_CHOICE",
                "Choose the correct answer.",
                null,
                5,
                0,
                options,
                null);

        assertThat(validator.validate(attemptRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("answers");
        assertThat(validator.validate(createQuestionRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("options");
        assertThat(validator.validate(updateQuestionRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("options");
    }

    @Test
    void dtoConstructorsNormalizeBoundaryTextBeforeValidation() {
        CreateLessonRequest lessonRequest = new CreateLessonRequest(
                "\u3000Lesson title\u3000",
                "\u3000",
                null,
                1L,
                1L,
                LearningType.RECORDED,
                AccessType.FREE,
                BigDecimal.ZERO,
                10,
                "  Lesson body  ",
                "\u3000");
        ContentApprovalRequest approvalRequest = new ContentApprovalRequest("\u3000Needs review\u3000");
        CreateTeacherFeedbackRequest feedbackRequest = new CreateTeacherFeedbackRequest(
                1L,
                null,
                null,
                "\u3000quiz\u3000",
                "\u3000Thoughtful feedback\u3000");
        ApproveTeacherRequest approveTeacherRequest = new ApproveTeacherRequest("\u3000Ready\u3000");
        RejectTeacherRequest rejectTeacherRequest = new RejectTeacherRequest("\u3000Missing certificate\u3000");
        AiChatRequest chatRequest = new AiChatRequest("\u3000你好\u3000", "\u3000zh\u3000", null, "\u3000Lesson 1\u3000", null);
        AiSentencePracticeRequest sentencePracticeRequest = new AiSentencePracticeRequest(
                "\u3000我喜欢中文\u3000",
                "\u3000beginner\u3000",
                "\u3000en\u3000");
        AiStoryGeneratorRequest storyGeneratorRequest = new AiStoryGeneratorRequest(
                "\u3000food\u3000",
                "\u3000HSK 1\u3000",
                "\u3000en\u3000",
                "\u3000short\u3000");
        AiVocabularyHelperRequest vocabularyHelperRequest = new AiVocabularyHelperRequest(
                "\u3000apple\u3000",
                "\u3000en\u3000",
                "\u3000zh\u3000",
                "\u3000beginner\u3000",
                "\u3000ms\u3000");
        CreateAiResponseReportRequest aiReportRequest = new CreateAiResponseReportRequest(
                null,
                null,
                null,
                "\u3000CHAT_TUTOR\u3000",
                "\u3000OTHER\u3000",
                "\u3000Description\u3000",
                "\u3000Prompt\u3000",
                "\u3000Response\u3000");
        ReviewAiResponseReportRequest reportReviewRequest = new ReviewAiResponseReportRequest(
                "\u3000RESOLVED\u3000",
                "\u3000Handled\u3000");
        CreateLiveClassRequest createLiveClassRequest = new CreateLiveClassRequest(
                "\u3000Tone practice\u3000",
                "\u3000",
                null,
                null,
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(1),
                "\u3000https://example.com/class\u3000",
                "\u3000zoom\u3000",
                "\u3000",
                10);
        UpdateLiveClassStatusRequest liveClassStatusRequest = new UpdateLiveClassStatusRequest("\u3000ONGOING\u3000");
        CreateQuizRequest createQuizRequest = new CreateQuizRequest("\u3000Quiz 1\u3000", "\u3000", 0);
        UpdateQuizRequest updateQuizRequest = new UpdateQuizRequest("\u3000Quiz 2\u3000", "\u3000Updated\u3000", 5);
        QuizOptionRequest optionRequest = new QuizOptionRequest("\u3000Option A\u3000", true, 0);
        QuizSubmissionAnswerRequest quizAnswerRequest = new QuizSubmissionAnswerRequest(1L, "\u3000Answer A\u3000");
        RecordedLessonVideoRequest recordedVideoRequest = new RecordedLessonVideoRequest(
                "\u3000Video 1\u3000",
                "\u3000",
                null,
                "\u3000EXTERNAL_URL\u3000",
                "\u3000https://example.com/video\u3000",
                null,
                0);
        AdminPlatformSettingsUpdateRequest settingsRequest = new AdminPlatformSettingsUpdateRequest(
                "\u3000AI can make mistakes\u3000",
                true,
                true,
                false,
                true,
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
        assertThat(ActivityType.parseLenient("\u3000matching-game\u3000")).contains(ActivityType.MATCHING_GAME);
    }

    @Test
    void listBackedRequestDtosAreDefensivelyCopiedAndImmutable() {
        List<String> tags = new ArrayList<>(List.of("\u3000tone\u3000", "pinyin"));
        AdminFlashcardRequest flashcardRequest = new AdminFlashcardRequest(
                "\u3000",
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
                AgeBand.LOWER_PRIMARY,
                ActivityDifficulty.EASY,
                "  greetings  ",
                tags,
                10,
                30);
        tags.add("late mutation");

        assertThat(flashcardRequest.title()).isNull();
        assertThat(flashcardRequest.frontText()).isEqualTo("你");
        assertThat(flashcardRequest.backText()).isEqualTo("you");
        assertThat(flashcardRequest.topic()).isEqualTo("greetings");
        assertThat(flashcardRequest.tags()).containsExactly("tone", "pinyin");
        assertThatThrownBy(() -> flashcardRequest.tags().add("blocked"))
                .isInstanceOf(UnsupportedOperationException.class);

        List<String> questionTypes = new ArrayList<>(List.of("MULTIPLE_CHOICE"));
        AiQuizGeneratorRequest quizGeneratorRequest = new AiQuizGeneratorRequest(
                "\u3000tones\u3000",
                "\u3000beginner\u3000",
                null,
                3,
                questionTypes,
                null,
                " en ");
        questionTypes.add("TRUE_FALSE");

        assertThat(quizGeneratorRequest.topic()).isEqualTo("tones");
        assertThat(quizGeneratorRequest.level()).isEqualTo("beginner");
        assertThat(quizGeneratorRequest.questionTypes()).containsExactly("MULTIPLE_CHOICE");
        assertThatThrownBy(() -> quizGeneratorRequest.questionTypes().add("FILL_BLANK"))
                .isInstanceOf(UnsupportedOperationException.class);

        List<String> recentQuizResults = new ArrayList<>(List.of("Quiz 1: 80%"));
        AiLearningRecommendationRequest recommendationRequest = new AiLearningRecommendationRequest(
                1L,
                " HSK 1 ",
                recentQuizResults,
                null,
                null,
                null,
                " zh ");
        recentQuizResults.add("Quiz 2: 90%");

        assertThat(recommendationRequest.currentLevel()).isEqualTo("HSK 1");
        assertThat(recommendationRequest.languagePreference()).isEqualTo("zh");
        assertThat(recommendationRequest.recentQuizResults()).containsExactly("Quiz 1: 80%");
    }

    @Test
    void aggregateRequestsRejectNullChildElementsThroughBeanValidation() {
        SubmitQuizAttemptRequest attemptRequest = new SubmitQuizAttemptRequest(nullElementList());
        CreateQuizQuestionRequest createQuestionRequest = new CreateQuizQuestionRequest(
                "MULTIPLE_CHOICE",
                "Choose the correct answer.",
                null,
                5,
                0,
                nullElementList(),
                null);
        UpdateQuizQuestionRequest updateQuestionRequest = new UpdateQuizQuestionRequest(
                "MULTIPLE_CHOICE",
                "Choose the correct answer.",
                null,
                5,
                0,
                nullElementList(),
                null);
        CheckoutSessionRequest checkoutRequest = new CheckoutSessionRequest(
                nullElementList(),
                CheckoutSource.CART);
        AdminDragDropActivityRequest dragDropRequest = new AdminDragDropActivityRequest(
                "Drag",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                nullElementList());
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
                null,
                nullElementList());

        assertThat(validator.validate(attemptRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.startsWith("answers"));
        assertThat(validator.validate(createQuestionRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.startsWith("options"));
        assertThat(validator.validate(updateQuestionRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.startsWith("options"));
        assertThat(validator.validate(checkoutRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.startsWith("items"));
        assertThat(validator.validate(dragDropRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.startsWith("items"));
        assertThat(validator.validate(storyRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.startsWith("questions"));
    }

    private static <T> List<T> nullElementList() {
        List<T> values = new ArrayList<>();
        values.add(null);
        return values;
    }
}
