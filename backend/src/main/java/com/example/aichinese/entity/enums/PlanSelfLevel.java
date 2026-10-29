package com.example.aichinese.entity.enums;

/** How much Mandarin the student says they know (setup step 4); it only sets where the level check starts. */
public enum PlanSelfLevel {

    NEW_TO_MANDARIN,

    A_LITTLE,

    THE_BASICS,

    NOT_SURE;

    /** The level the adaptive level check starts at. */
    public MandarinLevel checkStartLevel() {
        return switch (this) {
            case NEW_TO_MANDARIN -> MandarinLevel.NEW_TO_MANDARIN;
            case THE_BASICS -> MandarinLevel.BEGINNER_2;
            default -> MandarinLevel.BEGINNER_1;
        };
    }
}
