package com.example.aichinese.dto.learn;

/**
 * A finished review: mistakes answered and cleared now, mistakes still waiting, and the review XP (+10 once a day for
 * a review that clears at least one).
 */
public record MistakeReviewCompleteResponse(
        int answered,
        int cleared,
        long left,
        XpResult xp) {
}
