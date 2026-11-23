package com.example.aichinese.entity.enums;

/** How a support request became solved. */
public enum SupportClosedBy {

    /** The requester marked it solved: only requests from before learners' support pages were removed have it. */
    REQUESTER,

    SUPPORT,

    /** Nobody replied within 7 days of the last support reply. */
    AUTO
}
