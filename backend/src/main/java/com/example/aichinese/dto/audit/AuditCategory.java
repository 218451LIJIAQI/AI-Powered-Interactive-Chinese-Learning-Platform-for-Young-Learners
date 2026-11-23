package com.example.aichinese.dto.audit;

/**
 * The area an audit action belongs to, derived from its action type. The audit log keeps the entries of the teacher era
 * (TEACHER_*, ADMIN_TEACHER_*): they stay in their areas.
 */
public enum AuditCategory {

    ACCOUNT("ADMIN_USER", "ADMIN_TEACHER", "ADMIN_INVITE", "TEACHER_APPLICATION", "TEACHER_VERIFICATION",
            "PASSWORD_", "LOGIN", "LOGOUT", "PROFILE_", "ACCOUNT_", "EMAIL_"),
    CONTENT("ADMIN_CLASS_", "ADMIN_LESSON_", "TEACHER_CLASS_", "LEARN_", "LEVEL_CHECK_", "PRACTICE_SET_"),
    AI("AI_", "ADMIN_PLATFORM_SETTINGS"),
    SUPPORT("SUPPORT_"),
    LEARNING("TEACHER_LIVE_SESSION"),
    SYSTEM;

    private static final AuditCategory[] CATEGORIES = values();
    private final String[] actionPrefixes;

    AuditCategory(String... actionPrefixes) {
        this.actionPrefixes = actionPrefixes;
    }

    public static AuditCategory of(String action) {
        if (action == null) {
            return SYSTEM;
        }
        for (AuditCategory category : CATEGORIES) {
            for (String prefix : category.actionPrefixes) {
                if (action.startsWith(prefix)) {
                    return category;
                }
            }
        }
        return SYSTEM;
    }
}
