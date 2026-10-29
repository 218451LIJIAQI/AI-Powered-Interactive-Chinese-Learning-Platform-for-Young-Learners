package com.example.aichinese.dto.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Every action the backend writes to the audit log lands in the category the audit log page filters by. */
class AuditCategoryTests {

    @ParameterizedTest
    @CsvSource({
            "ADMIN_CLASS_APPROVE, CONTENT",
            "ADMIN_CLASS_DETAILS_REQUEST_CHANGES, CONTENT",
            "ADMIN_LESSON_REVISION_APPROVE, CONTENT",
            "ADMIN_LESSON_ARCHIVE, CONTENT",
            "TEACHER_CLASS_CREATE, CONTENT",
            "LEARN_UNIT_PUBLISH, CONTENT",
            "LEVEL_CHECK_QUESTION_UPDATE, CONTENT",
            "PRACTICE_SET_ARCHIVE, CONTENT",
            "CONTENT_SUBMIT, CONTENT",
            "TEACHER_LIVE_SESSION_SCHEDULE, LEARNING",
            "TEACHER_FEEDBACK, LEARNING",
            "ADMIN_TEACHER_APPROVE, ACCOUNT",
            "ADMIN_USER_DEACTIVATE, ACCOUNT",
            "PASSWORD_CHANGED, ACCOUNT",
            "LOGOUT, ACCOUNT",
            "AI_RESPONSE_REPORT_REVIEW, AI",
            "ADMIN_PLATFORM_SETTINGS_UPDATED, AI",
            "SUPPORT_REPLY_SENT, SUPPORT",
            "SOMETHING_ELSE, SYSTEM"
    })
    void actionsMapToTheirCategory(String actionType, AuditCategory expected) {
        assertThat(AuditCategory.of(actionType)).isEqualTo(expected);
    }
}
