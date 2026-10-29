package com.example.aichinese.dto.teacher;

import com.example.aichinese.entity.TeacherProfile;

/**
 * Where a teacher's application stands, derived from the stored status and the submission time: documents not sent
 * yet, waiting for an admin, sent back with a reason, or approved.
 */
public enum TeacherVerificationState {

    NOT_SUBMITTED,

    IN_REVIEW,

    CHANGES_REQUESTED,

    APPROVED;

    public static TeacherVerificationState of(TeacherProfile profile) {
        return switch (profile.getTeacherStatus()) {
            case APPROVED -> APPROVED;
            case REJECTED -> CHANGES_REQUESTED;
            case PENDING -> profile.getVerificationSubmittedAt() == null ? NOT_SUBMITTED : IN_REVIEW;
        };
    }
}
