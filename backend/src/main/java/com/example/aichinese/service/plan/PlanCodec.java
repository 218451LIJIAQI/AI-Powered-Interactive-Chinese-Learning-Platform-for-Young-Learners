package com.example.aichinese.service.plan;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.example.aichinese.entity.LearningPlan;
import com.example.aichinese.entity.LearningPlanStep;
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

    /** The constants {@link #names} wrote; a name the enum no longer has is skipped. */
    static <E extends Enum<E>> List<E> enums(String csv, Class<E> type) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::strip)
                .flatMap(name -> Arrays.stream(type.getEnumConstants()).filter(constant -> constant.name().equals(name)))
                .distinct()
                .toList();
    }

    static String lines(List<String> values) {
        List<String> clean = values.stream().filter(Objects::nonNull).map(String::strip).filter(text -> !text.isEmpty())
                .map(text -> text.replace('\n', ' ')).toList();
        return clean.isEmpty() ? null : String.join("\n", clean);
    }

    static List<String> lines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("\\R")).map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    static List<PlanGoal> picks(LearningPlan plan) {
        return enums(plan.getGoalPicks(), PlanGoal.class);
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
        return new PlanComposer.Goal(plan.getReason(), plan.getReasonText(), picks(plan), plan.getGoalText(),
                plan.getDeadline(), plan.getPace(), focus(plan));
    }
}
