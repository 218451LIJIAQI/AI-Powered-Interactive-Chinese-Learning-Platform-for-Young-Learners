package com.example.aichinese.dto.learn;

/**
 * A finished review: mistakes cleared now, mistakes still waiting, and the review XP (+10 once a day for a review that
 * clears at least one).
 */
public record MistakeReviewCompleteResponse(
        int cleared,
        long left,
        XpResult xp) {
}
