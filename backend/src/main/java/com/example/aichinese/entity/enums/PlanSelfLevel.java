package com.example.aichinese.entity.enums;

/**
 * How much Mandarin the student says they know (setup step 4). Served to the setup page via
 * {@code PlanViews.Options.checkStarts} as the single source of truth; on the server it is kept with the plan, places
 * the student when no level check question is published ({@link #checkStart}), and only {@code NEW_TO_MANDARIN} may
 * skip the check.
 */
public enum PlanSelfLevel {

    NEW_TO_MANDARIN,

    A_LITTLE,

    THE_BASICS,

    NOT_SURE;

    /** The level the adaptive level check starts from for this self-estimate. */
    public MandarinLevel checkStart() {
        return switch (this) {
            case NEW_TO_MANDARIN -> MandarinLevel.NEW_TO_MANDARIN;
            case THE_BASICS -> MandarinLevel.BEGINNER_2;
            case A_LITTLE, NOT_SURE -> MandarinLevel.BEGINNER_1;
        };
    }
}
