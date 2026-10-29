package com.example.aichinese.config.seed;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.PlanFocus;
import com.example.aichinese.entity.enums.PlanGoal;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanSelfLevel;
import com.example.aichinese.entity.enums.PlanSetupState;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearnUnitRepository;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.plan.LearningPlanService;
import com.example.aichinese.service.plan.PlanComposer;

/**
 * Demo learning plans as in the prototype. Amira built "Travel to Shanghai" with SparkBot a week ago (week 2 of 8,
 * Regular pace, Beginner 1 with a head start, focus listening and conversation): Unit 1 (in progress), her class
 * Mandarin for Beginners, Unit 2, the optional Everyday Conversations and Unit 3. Chen Hao's plan was built from
 * rules while SparkBot was unavailable; Zhang Yi and Nur Aisyah set the plan up later. Every seeded account counts as
 * having a confirmed e-mail address.
 */
@Component
@Order(6)
@Profile("demo & !prod & !production")
public class DemoPlanSeeder implements CommandLineRunner {

    private static final String DEMO_PROVIDER = "openai-compatible";
    private static final String DEMO_MODEL = "gpt-4o-mini";

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LearningPlanRepository planRepository;
    private final LearnUnitRepository unitRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final AiUsageLogRepository usageLogRepository;
    private final LearningPlanService planService;
    private final LearnClock clock;

    public DemoPlanSeeder(
            UserRepository userRepository,
            StudentProfileRepository studentProfileRepository,
            LearningPlanRepository planRepository,
            LearnUnitRepository unitRepository,
            CoursePackageRepository coursePackageRepository,
            AiUsageLogRepository usageLogRepository,
            LearningPlanService planService,
            LearnClock clock) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.planRepository = planRepository;
        this.unitRepository = unitRepository;
        this.coursePackageRepository = coursePackageRepository;
        this.usageLogRepository = usageLogRepository;
        this.planService = planService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Optional<StudentProfile> amira = student(DemoPersonas.STUDENT);
        if (amira.isEmpty() || planRepository.findFirstByStudentIdAndActiveTrueOrderByVersionDesc(amira.get().getId()).isPresent()) {
            return;
        }
        confirmSeededAddresses();
        LocalDate today = clock.today();
        seedAmira(amira.get(), today);
        student(DemoPersonas.CHEN_HAO).ifPresent(chen -> seedChenHao(chen, today));
        student(DemoPersonas.ZHANG_YI).ifPresent(this::setUpLater);
        student(DemoPersonas.NUR_AISYAH).ifPresent(this::setUpLater);
    }

    private void confirmSeededAddresses() {
        userRepository.findAll().stream()
                .filter(user -> user.getEmailVerifiedAt() == null)
                .forEach(user -> {
                    user.setEmailVerifiedAt(user.getCreatedAt());
                    userRepository.save(user);
                });
    }

    private void seedAmira(StudentProfile student, LocalDate today) {
        LocalDateTime builtAt = today.minusDays(7).atTime(LocalTime.of(20, 15));
        PlanComposer.Goal goal = new PlanComposer.Goal(PlanReason.TRAVEL, null,
                List.of(PlanGoal.INTRODUCE_MYSELF, PlanGoal.ORDER_FOOD, PlanGoal.ASK_DIRECTIONS, PlanGoal.GET_AROUND,
                        PlanGoal.SHORT_CHATS),
                "I'm going to Shanghai with friends. I want to order food, ask for directions and chat a little.",
                builtAt.toLocalDate().plusWeeks(8).minusDays(1), PlanPace.REGULAR,
                List.of(PlanFocus.LISTENING, PlanFocus.CONVERSATION));
        PlanComposer.Placement placement = new PlanComposer.Placement(PlanSelfLevel.A_LITTLE, MandarinLevel.BEGINNER_1,
                true, List.of(LevelCheckSkill.VOCABULARY, LevelCheckSkill.CHARACTERS),
                List.of(LevelCheckSkill.LISTENING, LevelCheckSkill.PINYIN), 3, true);
        List<PlanComposer.DraftStep> steps = new ArrayList<>();
        unit(1).ifPresent(id -> steps.add(new PlanComposer.DraftStep(PlanStepKind.LEARN_UNIT, id,
                "You know the greetings already, so finish numbers and polite phrases.")));
        classId("Mandarin for Beginners").ifPresent(id -> steps.add(new PlanComposer.DraftStep(PlanStepKind.CLASS, id,
                "You already have this class. Its Greetings and Numbers lessons back up Unit 1.")));
        unit(2).ifPresent(id -> steps.add(new PlanComposer.DraftStep(PlanStepKind.LEARN_UNIT, id,
                "Ordering food is the first thing you'll need in Shanghai.")));
        classId("Everyday Conversations").ifPresent(id -> steps.add(new PlanComposer.DraftStep(PlanStepKind.CLASS, id,
                "Live practice for short chats with people you meet. You can also Talk with Xiaomei.")));
        unit(3).ifPresent(id -> steps.add(new PlanComposer.DraftStep(PlanStepKind.LEARN_UNIT, id,
                "Asking for directions and taking the metro on your trip.")));
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
        log(student.getUser(), AiRequestStatus.SUCCESS, builtAt, null);
    }

    private void seedChenHao(StudentProfile student, LocalDate today) {
        LocalDateTime builtAt = today.minusDays(3).atTime(LocalTime.of(18, 40));
        PlanComposer.Goal goal = new PlanComposer.Goal(PlanReason.EVERYDAY, null,
                List.of(PlanGoal.ORDER_FOOD, PlanGoal.GO_SHOPPING, PlanGoal.UNDERSTAND_PRICES), null, null,
                PlanPace.LIGHT, List.of(PlanFocus.VOCABULARY));
        PlanComposer.Placement placement = new PlanComposer.Placement(PlanSelfLevel.NEW_TO_MANDARIN,
                MandarinLevel.NEW_TO_MANDARIN, false, List.of(), List.of(), 0, false);
        planService.seed(student, goal, placement, Optional.empty(), builtAt);
        log(student.getUser(), AiRequestStatus.UNAVAILABLE, builtAt, "AI provider is disabled in the current backend configuration.");
    }

    private void setUpLater(StudentProfile student) {
        StudentProfile fresh = studentProfileRepository.findById(student.getId()).orElseThrow();
        fresh.setPlanSetupState(PlanSetupState.SKIPPED);
        studentProfileRepository.save(fresh);
    }

    private void log(User user, AiRequestStatus status, LocalDateTime when, String error) {
        AiUsageLog entry = new AiUsageLog();
        entry.setUser(user);
        entry.setUserRole("STUDENT");
        entry.setFeatureKey(AiFeatureKey.LEARNING_PLAN);
        entry.setLanguagePreference("en");
        entry.setStatus(status.name());
        entry.setProviderName(DEMO_PROVIDER);
        entry.setModelName(DEMO_MODEL);
        entry.setErrorSummary(error);
        entry.setRelatedEntityType("LEARNING_PLAN");
        entry.setCreatedAt(when);
        entry.setUpdatedAt(when);
        usageLogRepository.save(entry);
    }

    private Optional<Long> unit(int position) {
        return unitRepository.findAll().stream()
                .filter(unit -> unit.getPosition() == position)
                .map(unit -> unit.getId())
                .findFirst();
    }

    private Optional<Long> classId(String title) {
        return coursePackageRepository.findAll().stream()
                .filter(coursePackage -> title.equals(coursePackage.getTitle()))
                .map(coursePackage -> coursePackage.getId())
                .findFirst();
    }

    private Optional<StudentProfile> student(DemoPersonas.Persona persona) {
        return userRepository.findByEmailWithRoles(persona.email())
                .flatMap(user -> studentProfileRepository.findByUserId(user.getId()));
    }
}
