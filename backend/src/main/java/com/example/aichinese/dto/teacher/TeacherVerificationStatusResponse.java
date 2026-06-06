package com.example.aichinese.dto.teacher;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.entity.enums.TeacherStatus;

@JsonPropertyOrder({
        "teacherStatus",
        "latestSubmittedDocuments",
        "latestReviewResult",
        "rejectionReason",
        "resubmissionAllowed"
})
public record TeacherVerificationStatusResponse(
        TeacherStatus teacherStatus,
        List<TeacherVerificationDocumentSummaryResponse> latestSubmittedDocuments,
        TeacherVerificationReviewSummaryResponse latestReviewResult,
        String rejectionReason,
        boolean resubmissionAllowed) {

    public TeacherVerificationStatusResponse {
        Objects.requireNonNull(teacherStatus, "teacherStatus must not be null");
        latestSubmittedDocuments = latestSubmittedDocuments == null
                ? List.of()
                : List.copyOf(latestSubmittedDocuments);
    }
}
