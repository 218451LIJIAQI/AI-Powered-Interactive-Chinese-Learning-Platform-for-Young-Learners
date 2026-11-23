package com.example.aichinese.service.plan;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.example.aichinese.entity.LearningPlan;
import com.example.aichinese.entity.LearningPlanStep;
import com.example.aichinese.entity.enums.EnumLookups;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.PlanFocus;
import com.example.aichinese.entity.enums.PlanGoal;

/** How a plan's lists are stored: enum names comma-separated, SparkBot's lists one item per line. */
final class PlanCodec {

    private PlanCodec() {
    }

    static String names(Collection<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).distinct().collect(Collectors.joining(","));
    }

    /**
     * The constants {@link #names} wrote, in order. A name retired since the plan was saved (a focus or goal no longer
     * offered) is passed over, so an older plan still opens.
     */
    private static <E extends Enum<E>> List<E> enums(String csv, Class<E> type) {
        return Arrays.stream(csv.split(","))
                .flatMap(name -> EnumLookups.tryFrom(type, name).stream())
                .toList();
    }

    /** SparkBot's texts are single lines already ({@link PlanAdvisor#parse}); an empty list is stored as null. */
    static String lines(List<String> values) {
        return values.isEmpty() ? null : String.join("\n", values);
    }

    static List<String> lines(String text) {
        return text == null ? List.of() : List.of(text.split("\n"));
    }

    static List<PlanFocus> focus(LearningPlan plan) {
        return enums(plan.getFocusSkills(), PlanFocus.class);
    }

    static List<LevelCheckSkill> skills(String csv) {
        return enums(csv, LevelCheckSkill.class);
    }

    static List<PlanGoal> goals(LearningPlanStep step) {
        return enums(step.getGoalPicks(), PlanGoal.class);
    }

    static PlanComposer.Goal goal(LearningPlan plan) {
        return new PlanComposer.Goal(plan.getReason(), plan.getReasonText(), enums(plan.getGoalPicks(), PlanGoal.class),
                plan.getGoalText(), plan.getDeadline(), plan.getPace(), focus(plan));
    }
}
