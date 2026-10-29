package com.example.aichinese.entity.enums;

/**
 * State of a pending change to an approved class or lesson. Students keep the approved version while a revision is
 * a draft, in review (SUBMITTED) or sent back with changes requested (REJECTED); approval applies it.
 */
public enum ClassRevisionStatus {

    DRAFT,

    SUBMITTED,

    REJECTED;
}
