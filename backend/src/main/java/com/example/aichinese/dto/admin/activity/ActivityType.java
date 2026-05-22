package com.example.aichinese.dto.admin.activity;

/**
 * Discriminator used by cross-type admin endpoints (listing, status updates)
 * to identify which of the six platform-managed learning activity tables a
 * record belongs to.
 */
public enum ActivityType {
    FLASHCARD,
    MATCHING_GAME,
    LISTENING_EXERCISE,
    TRACING_EXERCISE,
    DRAG_DROP_ACTIVITY,
    STORY_ACTIVITY
}
