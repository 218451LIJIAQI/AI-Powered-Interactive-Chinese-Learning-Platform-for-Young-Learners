package com.example.aichinese.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.learn.LearnPathResponse;
import com.example.aichinese.dto.learn.StopState;
import com.example.aichinese.dto.learn.UnitState;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.PlanFocus;
import com.example.aichinese.entity.enums.PlanGoal;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanSelfLevel;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.service.plan.PlanAdvisor;
import com.example.aichinese.service.plan.PlanCatalogue;
import com.example.aichinese.service.plan.PlanComposer;

/** The rules that decide a plan, and how SparkBot's reply is trusted, without a database. */
class PlanComposerTests {

    /** Mon 18 May 2026. */
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 18);
    /** A lesson with its practice: the units below take 1 to 3 hours each with practice and review. */
    private static final int LESSON_MINUTES = 20;

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
        PlanComposer.Goal goal = goal(List.of(PlanGoal.NOT_SURE), null, PlanPace.HOURS_1_TO_2, List.of());
        assertThat(goal.effectiveGoals()).isEqualTo(PlanReason.TRAVEL.defaultGoals()).hasSize(3);
    }

    @Test
    void theLearnStepsRunFromTheCurrentUnitToTheFurthestGoalInPathOrder() {
        PlanComposer.Goal goal = goal(List.of(PlanGoal.ASK_DIRECTIONS, PlanGoal.INTRODUCE_MYSELF), null,
                PlanPace.HOURS_1_TO_2, List.of());
        PlanComposer.Composed plan = compose(goal, BRAND_NEW);
        assertThat(learnIds(plan)).containsExactly(11L, 12L, 13L);
        assertThat(plan.targetLevel()).isEqualTo(MandarinLevel.BEGINNER_1);
        assertThat(plan.steps()).allMatch(step -> step.weekFrom() <= step.weekTo());
    }

    @Test
    void aHeadStartReviewsUnitOneAndAStrongResultReachesBeginnerTwo() {
        PlanComposer.Goal goal = goal(List.of(PlanGoal.ORDER_FOOD), null, PlanPace.HOURS_1_TO_2, List.of());
        PlanComposer.Composed headStart = compose(goal, HEAD_START);
        assertThat(headStart.steps().get(0).quickReview()).isTrue();
        assertThat(headStart.steps().stream().filter(step -> step.refId().equals(12L)).findFirst().orElseThrow()
                .quickReview()).isFalse();

        PlanComposer.Composed strong = compose(goal, STRONG);
        assertThat(learnIds(strong)).containsExactly(11L, 12L, 13L, 15L);
        assertThat(strong.steps().stream().filter(step -> step.kind() == PlanStepKind.LEARN_UNIT && !step.refId().equals(15L)))
                .allMatch(PlanComposer.Step::quickReview);
        assertThat(strong.targetLevel()).isEqualTo(MandarinLevel.BEGINNER_2);
    }

    @Test
    void joinedClassesAndTheBestOptionalClassBackUpTheUnitTheyMatch() {
        PlanComposer.Goal goal = goal(List.of(PlanGoal.ORDER_FOOD, PlanGoal.SHORT_CHATS), null, PlanPace.HOURS_1_TO_2,
                List.of(PlanFocus.LISTENING));
        PlanComposer.Composed plan = compose(goal, BRAND_NEW);
        List<Long> order = plan.steps().stream().map(PlanComposer.Step::refId).toList();
        assertThat(order).contains(21L, 22L).doesNotContain(23L);
        // Everyday Conversations matches food, so it follows the food unit.
        assertThat(order.indexOf(22L)).isEqualTo(order.indexOf(12L) + 1);
    }

    @Test
    void aDeadlineShorterThanThePathMakesAFirstMilestone() {
        PlanComposer.Goal relaxed = goal(List.of(PlanGoal.ASK_DIRECTIONS), TODAY.plusMonths(6), PlanPace.HOURS_1_TO_2, List.of());
        PlanComposer.Composed six = compose(relaxed, BRAND_NEW);
        assertThat(six.ambitious()).isFalse();
        assertThat(six.totalWeeks()).isGreaterThan(six.contentWeeks());

        PlanComposer.Goal rushed = goal(List.of(PlanGoal.ASK_DIRECTIONS), TODAY.plusDays(7), PlanPace.UNDER_30_MIN, List.of());
        PlanComposer.Composed week = compose(rushed, BRAND_NEW);
        assertThat(week.ambitious()).isTrue();
        assertThat(week.milestoneWeek()).isEqualTo(1);
        // The plan has the weeks to its date; the Learn steps after the milestone come later.
        assertThat(week.totalWeeks()).isEqualTo(1);
        assertThat(week.contentWeeks()).isGreaterThan(1);
    }

    @Test
    void theWeeksToAGoalDateAreCalendarWeeksCountingAStartedWeekInFull() {
        assertThat(goal(List.of(), TODAY.plusDays(1), PlanPace.UNDER_30_MIN, List.of()).weeksToDeadline(TODAY)).isEqualTo(1);
        assertThat(goal(List.of(), TODAY.plusDays(7), PlanPace.UNDER_30_MIN, List.of()).weeksToDeadline(TODAY)).isEqualTo(1);
        assertThat(goal(List.of(), TODAY.plusDays(8), PlanPace.UNDER_30_MIN, List.of()).weeksToDeadline(TODAY)).isEqualTo(2);
        assertThat(goal(List.of(), null, PlanPace.UNDER_30_MIN, List.of()).weeksToDeadline(TODAY)).isNull();
        // Sat 9 May to Sat 18 Jul: 70 days, 10 weeks, and Mon 18 May is in week 2.
        LocalDate saturday = LocalDate.of(2026, 5, 9);
        PlanComposer.Goal july = goal(List.of(), LocalDate.of(2026, 7, 18), PlanPace.HOURS_1_TO_2, List.of());
        assertThat(july.weeksToDeadline(saturday)).isEqualTo(10);
        assertThat(PlanComposer.weekOf(saturday, TODAY, 10)).isEqualTo(2);
        assertThat(PlanComposer.weekOf(saturday, saturday.plusDays(6), 10)).isEqualTo(1);
        assertThat(PlanComposer.weekOf(saturday, LocalDate.of(2026, 8, 1), 10)).isEqualTo(10);
        // Mon 18 May to Sat 18 Jul: 61 days, 9 weeks.
        assertThat(july.weeksToDeadline(TODAY)).isEqualTo(9);
    }

    @Test
    void aPlanHasTheCalendarWeeksToItsDateUpToTwoYearsAway() {
        // Mon 18 May to Wed 18 Nov: 184 days, 27 weeks.
        PlanComposer.Composed november = compose(goal(List.of(PlanGoal.ORDER_FOOD), LocalDate.of(2026, 11, 18),
                PlanPace.MINUTES_30_TO_60, List.of()), BRAND_NEW);
        assertThat(november.totalWeeks()).isEqualTo(27);
        assertThat(november.ambitious()).isFalse();
        // The latest date the setup accepts: 730 days, 105 weeks.
        assertThat(compose(goal(List.of(), TODAY.plusDays(PlanComposer.MAX_GOAL_DAYS), PlanPace.HOURS_2_PLUS,
                List.of()), BRAND_NEW).totalWeeks()).isEqualTo(105);
    }

    @Test
    void aDateTwoWeeksAwayIsTooShortForThePathAndGetsAFirstMilestoneOnIt() {
        // Mon 18 May to Mon 1 Jun: 14 days, 2 weeks, while the path needs more at under 30 minutes a day.
        PlanComposer.Composed plan = compose(goal(List.of(PlanGoal.ASK_DIRECTIONS), LocalDate.of(2026, 6, 1),
                PlanPace.UNDER_30_MIN, List.of()), BRAND_NEW);
        assertThat(plan.totalWeeks()).isEqualTo(2);
        assertThat(plan.ambitious()).isTrue();
        assertThat(plan.milestoneWeek()).isEqualTo(2);
        assertThat(plan.contentWeeks()).isGreaterThan(2);
    }

    @Test
    void moreDailyTimeMakesTheLearnStepsShorter() {
        PlanComposer.Goal goal = goal(List.of(PlanGoal.ASK_DIRECTIONS), null, PlanPace.UNDER_30_MIN, List.of());
        PlanComposer.Composed under30 = compose(goal, BRAND_NEW);
        int hours = compose(goal(List.of(PlanGoal.ASK_DIRECTIONS), null, PlanPace.HOURS_2_PLUS, List.of()), BRAND_NEW)
                .contentWeeks();
        assertThat(under30.contentWeeks()).isGreaterThan(hours);
        // Without a date the plan has the Learn steps' weeks and one more for review.
        assertThat(under30.totalWeeks()).isEqualTo(under30.contentWeeks() + 1);
    }

    @Test
    void aLearnerPlacedAtALevelWorksTowardsTheNextOne() {
        PlanComposer.Goal goal = goal(List.of(PlanGoal.INTRODUCE_MYSELF), null, PlanPace.HOURS_1_TO_2, List.of());
        PlanComposer.Composed headStart = compose(goal, HEAD_START);
        assertThat(learnIds(headStart)).containsExactly(11L, 12L, 13L, 15L);
        assertThat(headStart.targetLevel()).isEqualTo(MandarinLevel.BEGINNER_2);
        // A brand-new learner works towards HSK 1 only as far as the goals need.
        PlanComposer.Composed beginner = compose(goal, BRAND_NEW);
        assertThat(learnIds(beginner)).containsExactly(11L);
        assertThat(beginner.targetLevel()).isEqualTo(MandarinLevel.BEGINNER_1);
    }

    @Test
    void aGoalDateThatIsNotAfterThePlansFirstDayIsPlannedAsNoDeadline() {
        PlanComposer.Goal tomorrow = goal(List.of(PlanGoal.ORDER_FOOD), TODAY.plusDays(1), PlanPace.UNDER_30_MIN, List.of());
        assertThat(tomorrow.startingOn(TODAY)).isSameAs(tomorrow);
        assertThat(goal(List.of(), null, PlanPace.UNDER_30_MIN, List.of()).startingOn(TODAY).deadline()).isNull();

        for (LocalDate passed : List.of(TODAY, TODAY.minusDays(30))) {
            PlanComposer.Goal stored = goal(List.of(PlanGoal.ORDER_FOOD), passed, PlanPace.UNDER_30_MIN,
                    List.of(PlanFocus.LISTENING));
            PlanComposer.Goal planned = stored.startingOn(TODAY);
            assertThat(planned.deadline()).isNull();
            assertThat(planned).usingRecursiveComparison().ignoringFields("deadline").isEqualTo(stored);
            // As with "No deadline": no milestone, and a review week after the content.
            PlanComposer.Composed plan = compose(planned, BRAND_NEW);
            assertThat(plan.ambitious()).isFalse();
            assertThat(plan.milestoneWeek()).isNull();
            assertThat(plan.totalWeeks()).isEqualTo(plan.contentWeeks() + 1);
        }
    }

    @Test
    void sparkBotOrdersOnlyKnownStepsAndCannotDropALearnUnit() {
        PlanComposer.Goal goal = goal(List.of(PlanGoal.ORDER_FOOD, PlanGoal.SHORT_CHATS), null, PlanPace.HOURS_1_TO_2, List.of());
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
                        Map.of("id", "U12", "reason", "Twice."),
                        Map.of("id", "u012", "reason", "Twice, spelled differently.")),
                "outcomes", List.of("Order food", "  ", "Chat a little"),
                "unrealistic", false,
                "milestone", "Ignored when realistic."), candidates);

        assertThat(draft.targetLevel()).isNull();
        assertThat(draft.steps()).extracting(PlanComposer.DraftStep::refId).containsExactly(22L, 12L);
        assertThat(draft.outcomes()).containsExactly("Order food", "Chat a little");
        assertThat(draft.milestone()).isNull();

        PlanComposer.Composed plan = PlanComposer.compose(goal, BRAND_NEW, candidates, Optional.of(draft), TODAY);
        // Unit 1 is still there, first; the joined class (21) is kept, and a class SparkBot put before every unit follows it.
        assertThat(plan.steps()).extracting(PlanComposer.Step::refId).containsExactly(11L, 21L, 22L, 12L);
        assertThat(plan.steps().stream().filter(step -> step.refId().equals(12L)).findFirst().orElseThrow().reason())
                .isEqualTo("Food first.");
        assertThat(plan.steps().stream().filter(step -> step.refId().equals(11L)).findFirst().orElseThrow().reason())
                .isNull();
    }

    @Test
    void anUnrealisticGoalSparkBotFlagsGetsAMilestoneEvenWithoutADeadline() {
        PlanComposer.Goal goal = goal(List.of(PlanGoal.SHORT_CHATS), null, PlanPace.HOURS_2_PLUS, List.of());
        PlanComposer.Candidates candidates = PlanComposer.candidates(goal, BRAND_NEW, CATALOGUE);
        PlanComposer.AiDraft draft = PlanAdvisor.parse(Map.of("summary", "Fluent in a week.", "steps", List.of(),
                "unrealistic", true, "milestone", "First, greet people and introduce yourself."), candidates);
        PlanComposer.Composed plan = PlanComposer.compose(goal, BRAND_NEW, candidates, Optional.of(draft), TODAY);
        assertThat(plan.ambitious()).isTrue();
        assertThat(plan.milestoneWeek()).isNotNull();
        assertThat(draft.milestone()).isEqualTo("First, greet people and introduce yourself.");
    }

    @Test
    void classAnchorsReserveJoinedSlotsCapAtThreeAndHonorAiUnitAnchoring() {
        PlanCatalogue.Catalogue richCatalogue = new PlanCatalogue.Catalogue(CATALOGUE.units(), List.of(
                option(101L, "Joined One", "Order food and travel", "Travel", null, true),
                option(102L, "Joined Two", "Short chats and greetings", "Daily life", null, true),
                option(103L, "Optional A", "Short chats with classmates and office colleagues", "Daily life", null, false),
                option(104L, "Optional B", "Short chats and media listening conversations", "Daily life", null, false)), null);
        PlanComposer.Goal goal = goal(List.of(PlanGoal.ORDER_FOOD, PlanGoal.SHORT_CHATS), null, PlanPace.HOURS_1_TO_2, List.of());
        PlanComposer.Candidates candidates = PlanComposer.candidates(goal, BRAND_NEW, richCatalogue);
        assertThat(candidates.classes()).extracting(PlanCatalogue.ClassOption::id)
                .contains(101L, 102L, 103L, 104L);
        PlanComposer.AiDraft draft = PlanAdvisor.parse(Map.of(
                "summary", "Anchored after Unit 2.",
                "steps", List.of(
                        Map.of("id", "U11", "reason", "Start with tones."),
                        Map.of("id", "U12", "reason", "Then food."),
                        Map.of("id", "C103", "reason", "Practice chatting right after Unit 2."),
                        Map.of("id", "C104", "reason", "Fourth class should be capped out."))), candidates);

        PlanComposer.Composed plan = PlanComposer.compose(goal, BRAND_NEW, candidates, Optional.of(draft), TODAY);
        List<Long> classIds = plan.steps().stream()
                .filter(step -> step.kind() == PlanStepKind.CLASS)
                .map(PlanComposer.Step::refId)
                .toList();
        assertThat(classIds).containsExactlyInAnyOrder(101L, 102L, 103L);
        assertThat(classIds).doesNotContain(104L);
        List<Long> order = plan.steps().stream().map(PlanComposer.Step::refId).toList();
        assertThat(order.indexOf(103L)).isGreaterThan(order.indexOf(12L));
    }

    @Test
    void targetLevelAcceptsInRangeAiSuggestionsAndFallsBackToCeilingWhenOutOfRange() {
        PlanComposer.Goal goal = goal(List.of(PlanGoal.ORDER_FOOD), null, PlanPace.HOURS_1_TO_2, List.of());
        PlanComposer.Placement beginnerOne = new PlanComposer.Placement(
                PlanSelfLevel.THE_BASICS, MandarinLevel.BEGINNER_1, true, List.of(), List.of(), 5, true);
        PlanComposer.Candidates candidates = PlanComposer.candidates(goal, beginnerOne, CATALOGUE);

        PlanComposer.AiDraft belowPlacement = PlanAdvisor.parse(
                Map.of("summary", "S", "targetLevel", "NEW_TO_MANDARIN", "steps", List.of()), candidates);
        PlanComposer.AiDraft inRange = PlanAdvisor.parse(
                Map.of("summary", "S", "targetLevel", "BEGINNER_1", "steps", List.of()), candidates);
        PlanComposer.AiDraft aboveCeiling = PlanAdvisor.parse(
                Map.of("summary", "S", "targetLevel", "INTERMEDIATE", "steps", List.of()), candidates);

        // Below placement and above ceiling are ignored in favour of the plan's ceiling (BEGINNER_2).
        assertThat(PlanComposer.compose(goal, beginnerOne, candidates, Optional.of(belowPlacement), TODAY).targetLevel())
                .isEqualTo(MandarinLevel.BEGINNER_2);
        assertThat(PlanComposer.compose(goal, beginnerOne, candidates, Optional.of(inRange), TODAY).targetLevel())
                .isEqualTo(MandarinLevel.BEGINNER_1);
        assertThat(PlanComposer.compose(goal, beginnerOne, candidates, Optional.of(aboveCeiling), TODAY).targetLevel())
                .isEqualTo(MandarinLevel.BEGINNER_2);
    }

    @Test
    void ruleAnchorHonorsBestAfterUnitAndFallsBackWhenAbsent() {
        // Neither class matches Unit 2 ("Food") by keyword, so only bestAfterUnit=2 anchors 201L after 12L while
        // 202L (bestAfterUnit=99, absent from the plan) falls back after Unit 1 (11L).
        PlanCatalogue.Catalogue authored = new PlanCatalogue.Catalogue(CATALOGUE.units(), List.of(
                option(201L, "Chat & Work Class", "Short chats and work office conversation", "Work", 2, false),
                option(202L, "Missing Anchor Class", "Short chats and watch movies media", "Daily life", 99, false)), null);
        PlanComposer.Goal goal = goal(
                List.of(PlanGoal.ORDER_FOOD, PlanGoal.SHORT_CHATS, PlanGoal.TALK_ABOUT_WORK),
                null, PlanPace.HOURS_1_TO_2, List.of());
        PlanComposer.Candidates candidates = PlanComposer.candidates(goal, BRAND_NEW, authored);
        PlanComposer.Composed plan = PlanComposer.compose(goal, BRAND_NEW, candidates, Optional.empty(), TODAY);

        List<Long> order = plan.steps().stream().map(PlanComposer.Step::refId).toList();
        assertThat(order).containsExactly(11L, 202L, 12L, 201L);
    }

    @Test
    void composeHandlesCatalogueWhereEveryLearnUnitIsAlreadyDone() {
        PlanCatalogue.Catalogue allDone = new PlanCatalogue.Catalogue(List.of(
                unit(11L, 1, 1, "Pinyin", "拼音", UnitState.DONE, List.of("Tones"), 1),
                unit(12L, 2, 1, "Food", "点菜", UnitState.DONE, List.of("Order"), 1)),
                CATALOGUE.classes(), null);
        PlanComposer.Goal goal = goal(List.of(PlanGoal.ORDER_FOOD), null, PlanPace.UNDER_30_MIN, List.of());
        PlanComposer.Candidates candidates = PlanComposer.candidates(goal, BRAND_NEW, allDone);

        PlanComposer.Composed plan = PlanComposer.compose(goal, BRAND_NEW, candidates, Optional.empty(), TODAY);
        assertThat(learnIds(plan)).isEmpty();
        assertThat(plan.totalWeeks()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void planAdvisorParseSanitizesWorkOnMalformedIdsNewlinesAndCaps() {
        PlanComposer.Goal goal = goal(List.of(PlanGoal.ORDER_FOOD), null, PlanPace.HOURS_1_TO_2, List.of());
        PlanComposer.Candidates candidates = PlanComposer.candidates(goal, BRAND_NEW, CATALOGUE);
        PlanComposer.AiDraft draft = PlanAdvisor.parse(Map.of(
                "goalTitle", "Line 1\nLine 2\t" + "x".repeat(80),
                "summary", "Multi\nline\r\nsummary",
                "workOn", List.of("Tones\nand rhythm", "  ", "Pinyin", "Listening", "Speaking", "Extra fifth"),
                "outcomes", List.of("One\nline", "Two", "Three", "Four", "Five", "Six", "Seven"),
                "steps", List.of(
                        Map.of("id", "", "reason", "empty"),
                        Map.of("id", "U", "reason", "no digits"),
                        Map.of("id", "Uabc", "reason", "non-numeric"),
                        Map.of("id", "X12", "reason", "bad prefix"),
                        Map.of("id", "U11", "reason", "Good\nreason"))), candidates);

        assertThat(draft.goalTitle()).doesNotContain("\n", "\r").hasSizeLessThanOrEqualTo(60);
        assertThat(draft.summary()).isEqualTo("Multi line summary");
        assertThat(draft.workOn()).containsExactly("Tones and rhythm", "Pinyin", "Listening", "Speaking");
        assertThat(draft.outcomes()).hasSize(6).first().isEqualTo("One line");
        assertThat(draft.steps()).singleElement().satisfies(step -> {
            assertThat(step.refId()).isEqualTo(11L);
            assertThat(step.reason()).isEqualTo("Good reason");
        });
    }

    // Fixtures

    private static PlanComposer.Composed compose(PlanComposer.Goal goal, PlanComposer.Placement placement) {
        return PlanComposer.compose(goal, placement, PlanComposer.candidates(goal, placement, CATALOGUE),
                Optional.empty(), TODAY);
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
                    LESSON_MINUTES, 10, index < done ? StopState.DONE : StopState.LOCKED, false, null));
        }
        String text = (title + " " + titleZh + " " + String.join(" ", lessons)).toLowerCase(java.util.Locale.ROOT);
        return new PlanCatalogue.Unit(id, number, level, title, titleZh, state, lessons.size(), done,
                (lessons.size() - done) * LESSON_MINUTES, stops, text);
    }

    private static PlanCatalogue.ClassOption option(Long id, String title, String description, String topic, boolean joined) {
        return option(id, title, description, topic, null, joined);
    }

    private static PlanCatalogue.ClassOption option(
            Long id, String title, String description, String topic, Integer bestAfterUnit, boolean joined) {
        String text = (title + " " + description + " " + topic).toLowerCase(java.util.Locale.ROOT);
        return new PlanCatalogue.ClassOption(id, title, description, topic, bestAfterUnit, joined, text);
    }
}
