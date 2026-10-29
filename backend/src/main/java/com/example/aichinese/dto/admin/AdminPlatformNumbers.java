package com.example.aichinese.dto.admin;

import java.time.LocalDate;

/** The platform in numbers, shared by the admin dashboard and analytics. */
public record AdminPlatformNumbers(
        UserCounts users,
        /** Published classes and lessons, and every class and lesson in any status. */
        long publishedContent,
        long totalContent,
        long aiRequests,
        long successfulAiRequests,
        long auditEvents) {

    /** Accounts per role (deleted accounts are not counted). */
    public record UserCounts(long total, long students, long teachers, long admins) {
    }

    /** New accounts on one day. */
    public record DayCount(LocalDate date, long count) {
    }
}
