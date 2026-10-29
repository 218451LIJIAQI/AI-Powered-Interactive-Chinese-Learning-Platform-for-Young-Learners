package com.example.aichinese.entity.enums;

import java.util.List;

/** The main reason a student learns Mandarin (setup step 1), with the goals the setup suggests for it. */
public enum PlanReason {

    TRAVEL(List.of(PlanGoal.INTRODUCE_MYSELF, PlanGoal.ORDER_FOOD, PlanGoal.ASK_DIRECTIONS, PlanGoal.GET_AROUND,
            PlanGoal.GO_SHOPPING, PlanGoal.UNDERSTAND_PRICES, PlanGoal.READ_SIGNS, PlanGoal.SHORT_CHATS)),

    WORK(List.of(PlanGoal.GREET_CUSTOMERS, PlanGoal.SMALL_TALK, PlanGoal.TALK_ABOUT_WORK, PlanGoal.WRITE_MESSAGES,
            PlanGoal.UNDERSTAND_PRICES, PlanGoal.TELL_TIME, PlanGoal.ORDER_FOOD)),

    STUDY(List.of(PlanGoal.INTRODUCE_MYSELF, PlanGoal.TALK_IN_CLASS, PlanGoal.READ_SIGNS, PlanGoal.WRITE_MESSAGES,
            PlanGoal.TELL_TIME, PlanGoal.SHORT_CHATS)),

    EVERYDAY(List.of(PlanGoal.INTRODUCE_MYSELF, PlanGoal.ORDER_FOOD, PlanGoal.GO_SHOPPING, PlanGoal.UNDERSTAND_PRICES,
            PlanGoal.GET_AROUND, PlanGoal.SHORT_CHATS, PlanGoal.TELL_TIME)),

    FRIENDS_FAMILY(List.of(PlanGoal.INTRODUCE_MYSELF, PlanGoal.TALK_ABOUT_FAMILY, PlanGoal.SHORT_CHATS,
            PlanGoal.SMALL_TALK, PlanGoal.TELL_TIME, PlanGoal.WRITE_MESSAGES)),

    CULTURE(List.of(PlanGoal.INTRODUCE_MYSELF, PlanGoal.ENJOY_MEDIA, PlanGoal.READ_SIGNS, PlanGoal.SHORT_CHATS,
            PlanGoal.ORDER_FOOD)),

    JUST_FOR_ME(List.of(PlanGoal.INTRODUCE_MYSELF, PlanGoal.SHORT_CHATS, PlanGoal.ORDER_FOOD, PlanGoal.READ_SIGNS,
            PlanGoal.ENJOY_MEDIA, PlanGoal.WRITE_MESSAGES)),

    /** "Something else": the student says it in their own words. */
    OTHER(List.of(PlanGoal.INTRODUCE_MYSELF, PlanGoal.SHORT_CHATS, PlanGoal.ORDER_FOOD, PlanGoal.GET_AROUND,
            PlanGoal.READ_SIGNS, PlanGoal.WRITE_MESSAGES));

    private static final int DEFAULT_GOALS = 3;

    private final List<PlanGoal> suggestedGoals;

    PlanReason(List<PlanGoal> suggestedGoals) {
        this.suggestedGoals = List.copyOf(suggestedGoals);
    }

    /** The goals the setup offers for this reason (any goal may still be picked). */
    public List<PlanGoal> suggestedGoals() {
        return suggestedGoals;
    }

    /** The sensible default when the student picked nothing (or only "Not sure yet"). */
    public List<PlanGoal> defaultGoals() {
        return suggestedGoals.subList(0, Math.min(DEFAULT_GOALS, suggestedGoals.size()));
    }
}
