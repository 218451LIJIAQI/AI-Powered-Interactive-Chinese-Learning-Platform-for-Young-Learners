package com.example.aichinese.config.seed;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearnUnit;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.PlanFocus;
import com.example.aichinese.entity.enums.PlanGoal;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanSelfLevel;
import com.example.aichinese.entity.enums.PlanSetupState;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearnUnitRepository;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.service.plan.LearningPlanService;
import com.example.aichinese.service.plan.PlanComposer;

import lombok.RequiredArgsConstructor;

/**
 * Demo learning plans as in the prototype. Amira built "Travel to Shanghai" with SparkBot 8 days ago, the goal date 70
 * days after that (10 calendar weeks, now week 2 of 10; 1–2 hours a day, HSK 1 with a head start towards HSK 2, focus
 * listening and speaking): Unit 1 (in progress), her class Mandarin for Campus Life, Units 2 and 3, the optional Travel
 * Mandarin, Unit 4, the optional Shopping and Prices and Unit 5, the first of HSK 2. Chen Hao's plan was built from
 * rules while SparkBot was unavailable; Zhang Yi and Nur Aisyah set the plan up later. The usage-log rows of the two
 * SparkBot requests are written with the rest of the log in {@link DemoAiSeeder}.
 */
@Component
@Order(6)
@Profile("demo & !prod & !production")
@RequiredArgsConstructor
public class DemoPlanSeeder implements CommandLineRunner {

    /** Amira's goal date, in days after the plan's first day: 70 days, 10 calendar weeks. */
    private static final int AMIRA_GOAL_DAYS = 70;

    private final StudentProfileRepository studentProfileRepository;
    private final LearningPlanRepository planRepository;
    private final LearnUnitRepository unitRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final LearningPlanService planService;
    private final DemoClock clock;

    /** Amira built her plan with SparkBot 8 days ago at 20:15 (platform clock); DemoAiSeeder logs the request then. */
    static LocalDateTime amiraBuiltAt(DemoClock clock) {
        return clock.daysAgo(8, 20, 15);
    }

    /** Chen Hao's plan was built from rules, SparkBot being unavailable, 3 days ago at 18:40 (logged likewise). */
    static LocalDateTime chenHaoBuiltAt(DemoClock clock) {
        return clock.daysAgo(3, 18, 40);
    }

    @Override
    @Transactional
    public void run(String... args) {
        Optional<StudentProfile> amira = student(DemoPersonas.STUDENT);
        if (amira.isEmpty() || planRepository.findFirstByStudentIdAndActiveTrueOrderByVersionDesc(amira.get().getId()).isPresent()) {
            return;
        }
        seedAmira(amira.get());
        student(DemoPersonas.CHEN_HAO).ifPresent(this::seedChenHao);
        student(DemoPersonas.ZHANG_YI).ifPresent(this::setUpLater);
        student(DemoPersonas.NUR_AISYAH).ifPresent(this::setUpLater);
    }

    private void seedAmira(StudentProfile student) {
        LocalDateTime builtAt = amiraBuiltAt(clock);
        PlanComposer.Goal goal = new PlanComposer.Goal(PlanReason.TRAVEL, null,
                List.of(PlanGoal.INTRODUCE_MYSELF, PlanGoal.ORDER_FOOD, PlanGoal.ASK_DIRECTIONS, PlanGoal.GET_AROUND,
                        PlanGoal.SHORT_CHATS, PlanGoal.UNDERSTAND_PRICES, PlanGoal.GO_SHOPPING),
                "I'm going to Shanghai with friends. I want to order food, ask for directions and chat a little.",
                clock.dayOf(builtAt).plusDays(AMIRA_GOAL_DAYS), PlanPace.HOURS_1_TO_2,
                List.of(PlanFocus.LISTENING, PlanFocus.SPEAKING));
        PlanComposer.Placement placement = new PlanComposer.Placement(PlanSelfLevel.A_LITTLE, MandarinLevel.BEGINNER_1,
                true, List.of(LevelCheckSkill.VOCABULARY, LevelCheckSkill.CHARACTERS, LevelCheckSkill.PINYIN,
                        LevelCheckSkill.NUMBERS, LevelCheckSkill.SENTENCES),
                List.of(LevelCheckSkill.LISTENING, LevelCheckSkill.GRAMMAR, LevelCheckSkill.IDIOMS), 8, true);
        List<LearnUnit> units = unitRepository.findAll();
        List<CoursePackage> classes = coursePackageRepository.findAll();
        List<PlanComposer.DraftStep> steps = List.of(
                unitStep(units, 1, "You know a few words already, so a quick review of the sounds comes first."),
                classStep(classes, "Mandarin for Campus Life", "You already have this class. It puts what you learn to use in real conversations."),
                unitStep(units, 2, "First words, people and numbers: you'll use them from your first day in Shanghai."),
                unitStep(units, 3, "First sentences let you say who you are and order what you want."),
                classStep(classes, "Travel Mandarin", "Airport, hotel, metro and restaurant scenes for your trip. Best after Unit 3."),
                unitStep(units, 4, "Asking where things are and how much they cost, for getting around Shanghai."),
                classStep(classes, "Shopping and Prices", "Bargain and pay at the market. Best after Unit 4."),
                unitStep(units, 5, "Talking about what you did and when, to share your trip: your first step into HSK 2."));
        PlanComposer.AiDraft draft = new PlanComposer.AiDraft(
                "Travel to Shanghai",
                "You want enough Mandarin for your trip: order food, ask for directions, get around on the metro and"
                        + " have short, friendly chats.",
                List.of("Listening", "Numbers and prices", "Food words"),
                null,
                steps,
                List.of("Introduce yourself", "Order food and drinks", "Ask for directions and take the metro",
                        "Understand numbers and prices", "Have a short everyday chat"),
                false,
                null);
        planService.seed(student, goal, placement, Optional.of(draft), builtAt);
    }

    private void seedChenHao(StudentProfile student) {
        PlanComposer.Goal goal = new PlanComposer.Goal(PlanReason.EVERYDAY, null,
                List.of(PlanGoal.ORDER_FOOD, PlanGoal.GO_SHOPPING, PlanGoal.UNDERSTAND_PRICES), null, null,
                PlanPace.UNDER_30_MIN, List.of(PlanFocus.READING));
        PlanComposer.Placement placement = new PlanComposer.Placement(PlanSelfLevel.NEW_TO_MANDARIN,
                MandarinLevel.NEW_TO_MANDARIN, false, List.of(), List.of(), 0, false);
        planService.seed(student, goal, placement, Optional.empty(), chenHaoBuiltAt(clock));
    }

    private void setUpLater(StudentProfile student) {
        student.setPlanSetupState(PlanSetupState.SKIPPED);
    }

    private static PlanComposer.DraftStep unitStep(List<LearnUnit> units, int position, String reason) {
        Long unitId = units.stream()
                .filter(unit -> unit.getPosition() == position)
                .map(LearnUnit::getId)
                .findFirst()
                .orElseThrow();
        return new PlanComposer.DraftStep(PlanStepKind.LEARN_UNIT, unitId, reason);
    }

    private static PlanComposer.DraftStep classStep(List<CoursePackage> classes, String title, String reason) {
        Long classId = classes.stream()
                .filter(coursePackage -> title.equals(coursePackage.getTitle()))
                .map(CoursePackage::getId)
                .findFirst()
                .orElseThrow();
        return new PlanComposer.DraftStep(PlanStepKind.CLASS, classId, reason);
    }

    private Optional<StudentProfile> student(DemoPersonas.Persona persona) {
        return studentProfileRepository.findByUserEmail(persona.email());
    }
}
