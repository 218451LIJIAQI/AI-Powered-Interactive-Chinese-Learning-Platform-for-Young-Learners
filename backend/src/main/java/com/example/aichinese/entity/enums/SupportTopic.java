package com.example.aichinese.entity.enums;

/**
 * What a support request is about (the Topic field of the request forms). VERIFICATION (teacher verification) is kept
 * for requests sent before teachers were removed (2026-11); the forms no longer offer it.
 */
public enum SupportTopic {

    ACCOUNT,

    CLASSES,

    LEARNING,

    AI_HELPER,

    VERIFICATION,

    SAFETY,

    OTHER;

    /** Legacy teacher-era topics remain readable on historical requests, but new forms no longer accept them. */
    public boolean isSelectable() {
        return this != VERIFICATION;
    }
}
