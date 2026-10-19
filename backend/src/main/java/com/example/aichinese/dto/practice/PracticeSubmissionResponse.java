package com.example.aichinese.dto.practice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A pronunciation recording or handwriting image kept for teacher review (D7a). The AI feedback is
 * limited AI-assisted practice feedback, never a score ({@code aiNotice}). {@code fileUrl} is the
 * role-specific authorised stream path (student, teacher or admin endpoint).
 */
@JsonPropertyOrder({
        "id",
        "submissionType",
        "studentId",
        "studentName",
        "lessonId",
        "lessonTitle",
        "targetText",
        "aiFeedbackStatus",
        "aiFeedbackText",
        "aiNotice",
        "reviewStatus",
        "submittedAt",
        "reviewedAt",
        "fileName",
        "contentType",
        "fileSize",
        "fileUrl",
        "teacherFeedback"
})
public record PracticeSubmissionResponse(
        Long id,
        String submissionType,
        Long studentId,
        String studentName,
        Long lessonId,
        String lessonTitle,
        String targetText,
        String aiFeedbackStatus,
        String aiFeedbackText,
        String aiNotice,
        String reviewStatus,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        String fileName,
        String contentType,
        Long fileSize,
        String fileUrl,
        List<PracticeSubmissionFeedbackResponse> teacherFeedback) {

    public PracticeSubmissionResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(submissionType, "submissionType must not be null");
        Objects.requireNonNull(aiFeedbackStatus, "aiFeedbackStatus must not be null");
        Objects.requireNonNull(reviewStatus, "reviewStatus must not be null");
        teacherFeedback = teacherFeedback == null ? List.of() : List.copyOf(teacherFeedback);
    }

    /** One piece of teacher feedback linked to the submission. */
    @JsonPropertyOrder({ "id", "teacherName", "feedbackType", "message", "createdAt" })
    public record PracticeSubmissionFeedbackResponse(
            Long id,
            String teacherName,
            String feedbackType,
            String message,
            LocalDateTime createdAt) {

        public PracticeSubmissionFeedbackResponse {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(message, "message must not be null");
        }
    }
}
