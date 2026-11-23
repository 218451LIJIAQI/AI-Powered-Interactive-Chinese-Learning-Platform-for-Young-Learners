package com.example.aichinese.dto.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.ContentStatus;

/** Admin analytics for the last {@code days} days (7 or 30); user, content and AI totals are all time. */
public record AdminAnalyticsResponse(
        int days,
        LocalDate from,
        LocalDate to,
        Numbers numbers,
        long newRegistrations,
        List<DayCount> registrations,
        Learning learning,
        /** Classes and lessons per status. */
        Map<ContentStatus, Long> contentByStatus,
        Map<AiRequestStatus, Long> aiRequestsByStatus) {

    /** The platform in numbers: accounts, published classes and lessons, AI requests. */
    public record Numbers(UserCounts users, long publishedContent, long aiRequests) {
    }

    /** Accounts per role: learners (students) and admins (deleted accounts are not counted). */
    public record UserCounts(long total, long students, long admins) {
    }

    /** New accounts on one day. */
    public record DayCount(LocalDate date, long count) {
    }

    /**
     * Learning in the period: students with any learning (a class lesson or quiz, a Learn path lesson or a practice
     * set), lessons first completed, the quiz average (null without attempts) and the students whose quiz average is
     * below 60%.
     */
    public record Learning(
            long activeStudents,
            long totalStudents,
            long lessonsCompleted,
            Integer quizAverage,
            long needsSupport) {
    }
}
