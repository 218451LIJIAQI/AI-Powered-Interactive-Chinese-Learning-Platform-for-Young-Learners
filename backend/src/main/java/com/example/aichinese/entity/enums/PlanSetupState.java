package com.example.aichinese.entity.enums;

/**
 * Where a student stands with the learning plan setup: a new student is sent to it after sign-up, may set it up
 * later (Home then offers to build the plan), or has built a plan.
 */
public enum PlanSetupState {

    NOT_STARTED,

    SKIPPED,

    COMPLETED
}
