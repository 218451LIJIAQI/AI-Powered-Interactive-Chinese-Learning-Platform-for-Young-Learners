package com.example.aichinese.entity.enums;

import java.util.Optional;

/**
 * Publishing state of classes, units, lessons, quizzes and practice activities (FRCONT06). An admin publishes content
 * directly; learners see only PUBLISHED content.
 *
 * <pre>
 * create --> DRAFT --publish--> PUBLISHED --archive--> ARCHIVED --restore--> status before archive
 * </pre>
 *
 * A published lesson keeps its content while an admin edits a working copy (a DRAFT lesson revision); publishing
 * applies the copy.
 */
public enum ContentStatus {

    DRAFT,

    PUBLISHED,

    ARCHIVED;

    public static Optional<ContentStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(ContentStatus.class, value);
    }
}
