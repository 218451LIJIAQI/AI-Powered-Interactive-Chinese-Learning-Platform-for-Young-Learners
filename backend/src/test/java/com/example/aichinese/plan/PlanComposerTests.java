package com.example.aichinese.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.learn.LearnPathResponse;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.PlanFocus;
import com.example.aichinese.entity.enums.PlanGoal;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanSelfLevel;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.service.learn.LearnPathModel.StopState;
import com.example.aichinese.service.learn.LearnPathModel.UnitState;
import com.example.aichinese.service.plan.PlanAdvisor;
import com.example.aichinese.service.plan.PlanCatalogue;
import com.example.aichinese.service.plan.PlanComposer;

/** The rules that decide a plan, and how SparkBot's reply is trusted, without a database. */
class PlanComposerTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 18);

    private static final PlanCatalogue.Unit HELLO = unit(11L, 1, 1, "Say hello", "你好", UnitState.ACTIVE,
            List.of("Hello and goodbye", "Introduce yourself", "Numbers 1–10"), 2);
    private static final PlanCatalogue.Unit FOOD = unit(12L, 2, 1, "Food and drink", "吃喝", UnitState.LOCKED,
            List.of("At the canteen", "Ordering drinks", "Paying"), 0);
    private static final PlanCatalogue.Unit AROUND = unit(13L, 3, 1, "Getting around", "出行", UnitState.LOCKED,
            List.of("Asking the way", "Taking the LRT"), 0);
    private static final PlanCatalogue.Unit SHOPPING = unit(15L, 5, 2, "Shopping", "购物", UnitState.LOCKED,
            List.of("Clothes and colours", "At the night market"), 0);
    private static final List<PlanCatalogue.Unit> UNITS = List.of(HELLO, FOOD, AROUND, SHOPPING);

    private static final PlanCatalogue.ClassOption JOINED = option(21L, "Mandarin for Beginners",
            "Greetings and numbers for adult beginners", "Speaking", true);
    private static final PlanCatalogue.ClassOption CHATS = option(22L, "Everyday Conversations",
            "Real-life Mandarin for food stalls and getting around", "Listening", false);
    private static final PlanCatalogue.ClassOption UNRELATED = option(23L, "Chinese Characters 101",
            "Strokes and radicals", "Characters", false);
    private static final PlanCatalogue.Catalogue CATALOGUE = new PlanCatalogue.Catalogue(UNITS,
            List.of(JOINED, CHATS, UNRELATED), null);

    private static final PlanComposer.Placement BRAND_NEW = new PlanComposer.Placement(PlanSelfLevel.NEW_TO_MANDARIN,
            MandarinLevel.NEW_TO_MANDARIN, false, List.of(), List.of(), 0, false);
    private static final PlanComposer.Placement HEAD_START = new PlanComposer.Placement(PlanSelfLevel.A_LITTLE,
            MandarinLevel.BEGINNER_1, true, List.of(LevelCheckSkill.VOCABULARY), List.of(LevelCheckSkill.LISTENING), 4, true);
    private static final PlanComposer.Placement STRONG = new PlanComposer.Placement(PlanSelfLevel.THE_BASICS,
            MandarinLevel.BEGINNER_2, true, List.of(LevelCheckSkill.SENTENCES), List.of(), 5, true);

    @Test
    void goalsMatchWordStartsOnly() {
        assertThat(PlanComposer.matches(PlanGoal.UNDERSTAND_PRICES, "Numbers 1–10")).isTrue();
        assertThat(PlanComposer.matches(PlanGoal.ASK_DIRECTIONS, "Asking the way")).isTrue();
        assertThat(PlanComposer.matches(PlanGoal.ORDER_FOOD, "seafood")).isFalse();
        assertThat(PlanComposer.matches(PlanGoal.NOT_SURE, "anything")).isFalse();
    }

    @Test
    void noPicksUseTheReasonDefaults() {
        PlanComposer.Goal goal = goal(List.of(PlanGoal.NOT_SURE), null, PlanPace.REGULAR, List.of());
        assertThat(goal.effectiveGoals()).isEqualTo(PlanReason.TRAVEL.defaultGoals()).hasSize(3);
    }

    @Test
    void theLearnStepsRunFromTheCurrentUnitToTheFurthestGoalInPathOrder() {
        PlanComposer.Goal goal = goal(List.of(PlanGoal.ASK_DIRECTIONS, PlanGoal.INTRODUCE_MYSELF), null,
                PlanPace.REGULAR, List.of());
        PlanComposer.Composed plan = compose(goal, BRAND_NEW, Optional.empty());
        assertThat(learnIds(plan)).containsExactly(11L, 12L, 13L);
        assertThat(plan.targetLevel()).isEqualTo(MandarinLevel.BEGINNER_1);
        assertThat(plan.steps()).allMatch(step -> step.weekFrom() <= step.weekTo());
    }

    @Test
    void aHeadStartReviewsUnitOneAndAStrongResultReachesBeginnerTwo() {
        PlanComposer.Goal goal = goal(List.of(PlanGoal.ORDER_FOOD), null, PlanPace.REGULAR, List.of());
        PlanComposer.Composed headStart = compose(goal, HEAD_START, Optional.empty());
        assertThat(headStart.steps().get(0).quickReview()).isTrue();
        assertThat(headStart.steps().stream().filter(step -> step.refId().equals(12L)).findFirst().orElseThrow()
                .quickReview()).isFalse();

        PlanComposer.Composed strong = compose(goal, STRONG, Optional.empty());
        assertThat(learnIds(strong)).containsExactly(11L, 12L, 13L, 15L);
        assertThat(strong.steps().stream().filter(step -> step.kind() == PlanStepKind.LEARN_UNIT && !step.refId().equals(15L)))
                .allMatch(PlanComposer.Step::quickReview);
        assertThat(strong.targetLevel()).isEqualTo(MandarinLevel.BEGINNER_2);
    }

    @Test
    void joinedClassesAndTheBestOptionalClassBackUpTheUnitTheyMatch() {
        PlanComposer.Goal goal = goal(List.of(PlanGoal.ORDER_FOOD, PlanGoal.SHORT_CHATS), null, PlanPace.REGULAR,
                List.of(PlanFocus.LISTENING));
        PlanComposer.Composed plan = compose(goal, BRAND_NEW, Optional.empty());
        List<Long> order = plan.steps().stream().map(PlanComposer.Step::refId).toList();
        assertThat(order).contains(21L, 22L).doesNotContain(23L);
        // Everyday Conversations matches food, so it follows the food unit; classes are optional.
        assertThat(order.indexOf(22L)).isEqualTo(order.indexOf(12L) + 1);
        assertThat(plan.steps().stream().filter(step -> step.kind() == PlanStepKind.CLASS))
                .allMatch(PlanComposer.Step::optional);
    }

    @Test
    void aDeadlineShorterThanThePathMakesAFirstMilestone() {
        PlanComposer.Goal relaxed = goal(List.of(PlanGoal.ASK_DIRECTIONS), TODAY.plusMonths(6), PlanPace.REGULAR, List.of());
        PlanComposer.Composed six = compose(relaxed, BRAND_NEW, Optional.empty());
        assertThat(six.ambitious()).isFalse();
        assertThat(six.totalWeeks()).isGreaterThan(six.contentWeeks());

        PlanComposer.Goal rushed = goal(List.of(PlanGoal.ASK_DIRECTIONS), TODAY.plusDays(7), PlanPace.LIGHT, List.of());
        PlanComposer.Composed week = compose(rushed, BRAND_NEW, Optional.empty());
        assertThat(week.ambitious()).isTrue();
        assertThat(week.milestoneWeek()).isEqualTo(1);
        assertThat(week.totalWeeks()).isEqualTo(week.contentWeeks());
    }

    @Test
    void sparkBotOrdersOnlyKnownStepsAndCannotDropALearnUnit() {
        PlanComposer.Goal goal = goal(List.of(PlanGoal.ORDER_FOOD, PlanGoal.SHORT_CHATS), null, PlanPace.REGULAR, List.of());
        PlanComposer.Candidates candidates = PlanComposer.candidates(goal, BRAND_NEW, CATALOGUE);
        PlanComposer.AiDraft draft = PlanAdvisor.parse(Map.of(
                "goalTitle", "Eat and chat in KL",
                "summary", "You want to order food and chat.",
                "targetLevel", "HSK_9",
                "steps", List.of(
                        Map.of("id", "C22", "reason", "Live practice for chats."),
                        Map.of("id", "U12", "reason", "Food first."),
                        Map.of("id", "U999", "reason", "Made up."),
                        Map.of("id", "C777", "reason", "Not in the catalogue."),
                        Map.of("id", "U12", "reason", "Twice.")),
                "outcomes", List.of("Order food", "  ", "Chat a little"),
                "unrealistic", false,
                "milestone", "Ignored when realistic."), candidates);

        assertThat(draft.targetLevel()).isNull();
        assertThat(draft.steps()).extracting(PlanComposer.DraftStep::refId).containsExactly(22L, 12L);
        assertThat(draft.outcomes()).containsExactly("Order food", "Chat a little");
        assertThat(draft.milestone()).isNull();

        PlanComposer.Composed plan = PlanComposer.compose(goal, BRAND_NEW, candidates, Optional.of(draft), TODAY);
        // Unit 1 is still there, first; a class SparkBot put before every unit follows the first one.
        assertThat(plan.steps().get(0).refId()).isEqualTo(11L);
        assertThat(plan.steps()).extracting(PlanComposer.Step::refId).contains(11L, 12L, 22L);
        assertThat(plan.steps().stream().filter(step -> step.refId().equals(12L)).findFirst().orElseThrow().reason())
                .isEqualTo("Food first.");
        assertThat(plan.steps().stream().filter(step -> step.refId().equals(11L)).findFirst().orElseThrow().reason())
                .isNull();
    }

    @Test
    void anUnrealisticGoalSparkBotFlagsGetsAMilestoneEvenWithoutADeadline() {
        PlanComposer.Goal goal = goal(List.of(PlanGoal.SHORT_CHATS), null, PlanPace.INTENSE, List.of());
        PlanComposer.Candidates candidates = PlanComposer.candidates(goal, BRAND_NEW, CATALOGUE);
        PlanComposer.AiDraft draft = PlanAdvisor.parse(Map.of("summary", "Fluent in a week.", "steps", List.of(),
                "unrealistic", true, "milestone", "First, greet people and introduce yourself."), candidates);
        PlanComposer.Composed plan = PlanComposer.compose(goal, BRAND_NEW, candidates, Optional.of(draft), TODAY);
        assertThat(plan.ambitious()).isTrue();
        assertThat(plan.milestoneWeek()).isNotNull();
        assertThat(draft.milestone()).isEqualTo("First, greet people and introduce yourself.");
    }

    @Test
    void coverageCountsALearnUnitOrAClass() {
        List<PlanComposer.Coverage> coverage = PlanComposer.coverage(
                List.of(PlanGoal.ORDER_FOOD, PlanGoal.SHORT_CHATS, PlanGoal.TALK_ABOUT_WORK),
                List.of(new PlanComposer.StepGoals(PlanStepKind.LEARN_UNIT, 12L, "Food and drink",
                                java.util.Set.of(PlanGoal.ORDER_FOOD)),
                        new PlanComposer.StepGoals(PlanStepKind.CLASS, 22L, "Everyday Conversations",
                                java.util.Set.of(PlanGoal.SHORT_CHATS))));
        // Ordering food: a Learn unit; short chats: a class; talking about work: nothing yet.
        assertThat(coverage).extracting(PlanComposer.Coverage::learn).containsExactly(true, false, false);
        assertThat(coverage).extracting(PlanComposer.Coverage::classId).containsExactly(null, 22L, null);
        assertThat(coverage.get(1).classTitle()).isEqualTo("Everyday Conversations");
    }

    // ===== Fixtures =====

    private static PlanComposer.Composed compose(
            PlanComposer.Goal goal, PlanComposer.Placement placement, Optional<PlanComposer.AiDraft> draft) {
        return PlanComposer.compose(goal, placement, PlanComposer.candidates(goal, placement, CATALOGUE), draft, TODAY);
    }

    private static List<Long> learnIds(PlanComposer.Composed plan) {
        return plan.steps().stream().filter(step -> step.kind() == PlanStepKind.LEARN_UNIT)
                .map(PlanComposer.Step::refId).toList();
    }

    private static PlanComposer.Goal goal(List<PlanGoal> picks, LocalDate deadline, PlanPace pace, List<PlanFocus> focus) {
        return new PlanComposer.Goal(PlanReason.TRAVEL, null, picks, null, deadline, pace, focus);
    }

    private static PlanCatalogue.Unit unit(
            Long id, int number, int level, String title, String titleZh, UnitState state, List<String> lessons, int done) {
        List<LearnPathResponse.Stop> stops = new java.util.ArrayList<>();
        for (int index = 0; index < lessons.size(); index++) {
            stops.add(new LearnPathResponse.Stop(id * 100 + index, index + 1, lessons.get(index), LearnLessonKind.LESSON,
                    4, 10, index < done ? StopState.DONE : StopState.LOCKED, false, null));
        }
        String text = (title + " " + titleZh + " " + String.join(" ", lessons)).toLowerCase(java.util.Locale.ROOT);
        return new PlanCatalogue.Unit(id, number, level, title, titleZh, level, "hand", state, lessons.size(), done,
                (lessons.size() - done) * 4, stops, text);
    }

    private static PlanCatalogue.ClassOption option(Long id, String title, String description, String topic, boolean joined) {
        String text = (title + " " + description + " " + topic).toLowerCase(java.util.Locale.ROOT);
        return new PlanCatalogue.ClassOption(id, title, description, "Beginner 1", topic, "Teacher", joined, text);
    }
}
