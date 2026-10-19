package com.example.aichinese.entity.base;

import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.LearningStage;

/**
 * Common shape of the six practice-activity entities (flashcards, matching games, listening,
 * tracing, drag-and-drop and story activities): reviewable content with an author and an
 * optional learning stage.
 */
public interface PracticeActivityContent extends ReviewableContent {

    User getCreatedByUser();

    LearningStage getLearningStage();

    void setLearningStage(LearningStage learningStage);
}
