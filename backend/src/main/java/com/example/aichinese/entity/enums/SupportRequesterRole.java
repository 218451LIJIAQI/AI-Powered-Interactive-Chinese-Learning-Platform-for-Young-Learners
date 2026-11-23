package com.example.aichinese.entity.enums;

/**
 * Who sent a support request: a signed-in learner (STUDENT), or a visitor through the Help page form. TEACHER marks the
 * requests teacher accounts sent before teachers were removed (2026-11); none is written any more.
 */
public enum SupportRequesterRole {

    STUDENT,

    TEACHER,

    VISITOR
}
