package com.example.aichinese.dto.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Current and historical audit log actions map to the category shown on the audit log page and CSV export. */
class AuditCategoryTests {

    @ParameterizedTest
    @CsvSource({
            "ADMIN_CLASS_APPROVE, CONTENT",
            "ADMIN_CLASS_DETAILS_REQUEST_CHANGES, CONTENT",
            "ADMIN_LESSON_REVISION_APPROVE, CONTENT",
            "ADMIN_LESSON_ARCHIVE, CONTENT",
            "ADMIN_LESSON_EDIT, CONTENT",
            "ADMIN_CLASS_EDIT, CONTENT",
            "ADMIN_CLASS_UNIT_ADD, CONTENT",
            "ADMIN_CLASS_UNIT_RENAME, CONTENT",
            "TEACHER_CLASS_CREATE, CONTENT",
            "LEARN_UNIT_PUBLISH, CONTENT",
            "LEVEL_CHECK_QUESTION_UPDATE, CONTENT",
            "LEVEL_CHECK_QUESTION_PUBLISH, CONTENT",
            "LEVEL_CHECK_QUESTION_UNPUBLISH, CONTENT",
            "PRACTICE_SET_ARCHIVE, CONTENT",
            "TEACHER_LIVE_SESSION_SCHEDULE, LEARNING",
            "ADMIN_TEACHER_APPROVE, ACCOUNT",
            "ADMIN_USER_DEACTIVATE, ACCOUNT",
            "ADMIN_INVITE_RESTORED, ACCOUNT",
            "PASSWORD_CHANGED, ACCOUNT",
            "LOGOUT, ACCOUNT",
            "AI_RESPONSE_REPORT_REVIEW, AI",
            "AI_RESPONSE_REPORT_RESOLVED, AI",
            "AI_RESPONSE_REPORT_DISMISSED, AI",
            "EMAIL_CHANGE_CANCELLED, ACCOUNT",
            "ADMIN_PLATFORM_SETTINGS_UPDATED, AI",
            "SUPPORT_REPLY_SENT, SUPPORT",
            "SOMETHING_ELSE, SYSTEM"
    })
    void actionsMapToTheirCategory(String actionType, AuditCategory expected) {
        assertThat(AuditCategory.of(actionType)).isEqualTo(expected);
    }
}
