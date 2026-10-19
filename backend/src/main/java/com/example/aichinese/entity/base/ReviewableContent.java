package com.example.aichinese.entity.base;

import com.example.aichinese.entity.enums.ContentStatus;

/**
 * Content that goes through the teacher-submit / admin-review workflow (decision D4):
 * courses, modules, lessons, quizzes and the six practice-activity types.
 * Lombok getters and setters on the entities implement this interface.
 */
public interface ReviewableContent {

    Long getId();

    String getTitle();

    ContentStatus getContentStatus();

    void setContentStatus(ContentStatus contentStatus);

    String getRejectionReason();

    void setRejectionReason(String rejectionReason);
}
