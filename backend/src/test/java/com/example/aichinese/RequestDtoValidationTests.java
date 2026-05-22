package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.CreateAiResponseReportRequest;
import com.example.aichinese.dto.commerce.CartItemRequest;
import com.example.aichinese.dto.communication.CreateTeacherFeedbackRequest;
import com.example.aichinese.dto.content.CreateLessonRequest;
import com.example.aichinese.dto.learning.QuizSubmissionAnswerRequest;
import com.example.aichinese.dto.learning.UpdateLiveClassRequest;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.LearningType;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

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
}
