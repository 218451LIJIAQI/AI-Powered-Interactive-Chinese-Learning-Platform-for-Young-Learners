package com.example.aichinese.entity.enums;

/**
 * "Time I can give each day" (setup step 3): the four bands of daily self-study time in Shen &amp; Wu (2026), Frontiers
 * in Psychology 17:1761904. HOURS_1_TO_2, the most common band (38.9%), is the setup's default. The band is shown as it
 * is ("1–2 hours a day"); the daily goal is an Account setting of its own and does not follow it.
 */
public enum PlanPace {

    UNDER_30_MIN(0, 30),

    MINUTES_30_TO_60(30, 60),

    HOURS_1_TO_2(60, 120),

    /** Open-ended: no maximum. */
    HOURS_2_PLUS(120, null);

    private final int minMinutesPerDay;
    private final Integer maxMinutesPerDay;

    PlanPace(int minMinutesPerDay, Integer maxMinutesPerDay) {
        this.minMinutesPerDay = minMinutesPerDay;
        this.maxMinutesPerDay = maxMinutesPerDay;
    }

    public int minMinutesPerDay() {
        return minMinutesPerDay;
    }

    /** Null for "2+ hours". */
    public Integer maxMinutesPerDay() {
        return maxMinutesPerDay;
    }

    /**
     * The minutes a week the plan's weeks are laid out with: an internal, conservative estimate, never shown as an
     * option. Each band counts at its lower bound (15 minutes for "Under 30 min", whose lower bound is 0), every day of
     * the week, since the learner gives this time each day.
     */
    public int planningMinutesPerWeek() {
        return Math.max(15, minMinutesPerDay) * 7;
    }
}
