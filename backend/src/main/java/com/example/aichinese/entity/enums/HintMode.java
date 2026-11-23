package com.example.aichinese.entity.enums;

/** When a student is offered hints, chosen on the Account page. */
public enum HintMode {

    /** Offered after the student has been stuck for a while (how long depends on the level). */
    AUTOMATIC,

    /** A "Show hint" bar from the start of every question; nothing appears by itself. */
    ON_REQUEST,

    OFF
}
