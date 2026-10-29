package com.example.aichinese.entity.enums;

/** How a support request became solved. */
public enum SupportClosedBy {

    REQUESTER,

    SUPPORT,

    /** Nobody replied within 7 days of the last support reply. */
    AUTO
}
