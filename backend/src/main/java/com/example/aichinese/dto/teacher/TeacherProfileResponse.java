package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.example.aichinese.dto.DtoNormalization;
import com.example.aichinese.dto.PublishedClassSummary;
import com.example.aichinese.entity.enums.TeacherVerificationDocumentType;

/** The Teaching profile page: the public profile, the verification and the classes students see the teacher on. */
public record TeacherProfileResponse(
        Long teacherId,
        String fullName,
        String email,
        String bio,
        TeacherVerificationState verificationState,
        /** When the application was last sent to the admins. */
        LocalDateTime submittedAt,
        LocalDateTime approvedAt,
        /** The latest admin decision (its reason when changes were requested); null before the first decision. */
        Decision latestDecision,
        List<Document> documents,
        List<PublishedClassSummary> publishedClasses) {

    public TeacherProfileResponse {
        Objects.requireNonNull(teacherId, "teacherId must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(verificationState, "verificationState must not be null");
        documents = DtoNormalization.immutableListOrEmpty(documents);
        publishedClasses = DtoNormalization.immutableListOrEmpty(publishedClasses);
    }

    /** How one current document stands: new since the last submission, waiting, verified or already sent. */
    public enum DocumentStatus {
        READY,
        IN_REVIEW,
        VERIFIED,
        SENT
    }

    public record Document(
            Long id,
            TeacherVerificationDocumentType documentType,
            String fileName,
            long fileSize,
            LocalDateTime uploadedAt,
            DocumentStatus status) {
    }

    public record Decision(
            TeacherVerificationState outcome,
            String reason,
            String reviewerName,
            LocalDateTime decidedAt) {
    }
}
