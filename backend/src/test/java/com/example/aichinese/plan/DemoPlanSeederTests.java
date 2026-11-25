package com.example.aichinese.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.aichinese.config.seed.DemoPersonas;
import com.example.aichinese.config.seed.DemoPlanSeeder;
import com.example.aichinese.dto.plan.HomeView;
import com.example.aichinese.dto.plan.PlanViews;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanSetupState;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.classes.StudentClassService;
import com.example.aichinese.service.plan.LearningPlanService;
import com.example.aichinese.service.plan.StudentHomeService;
import com.example.aichinese.support.DemoProfileTest;

/** The demo plans read through the real services, as Home and My learning plan show them. */
@DemoProfileTest
class DemoPlanSeederTests {

    @Autowired
    private DemoPlanSeeder demoPlanSeeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private LearningPlanRepository planRepository;

    @Autowired
    private LearningPlanService planService;

    @Autowired
    private StudentHomeService homeService;

    @Autowired
    private StudentClassService studentClasses;

    @Test
    void amiraIsInWeekTwoOfHerTravelPlanWithHerClassAndAnOptionalOne() {
        SecurityUserDetails amira = signedIn(DemoPersonas.STUDENT);

        PlanViews.Plan plan = planService.current(amira);
        assertThat(plan.aiAssisted()).isTrue();
        assertThat(plan.goal().title()).isEqualTo("Travel to Shanghai");
        // Started 8 days ago, the goal date 70 days after the start: calendar week 2 of 10.
        assertThat(plan.weeks().current()).isEqualTo(2);
        assertThat(plan.weeks().total()).isEqualTo(10);
        assertThat(plan.level().placed().name()).isEqualTo("BEGINNER_1");
        assertThat(plan.level().target().name()).isEqualTo("BEGINNER_2");
        assertThat(plan.pace().key()).isEqualTo(PlanPace.HOURS_1_TO_2);
        assertThat(plan.pace().minMinutesPerDay()).isEqualTo(60);
        assertThat(plan.pace().maxMinutesPerDay()).isEqualTo(120);
        List<String> steps = plan.steps().stream()
                .map(step -> step.kind() == PlanStepKind.CLASS ? "C:" + step.title() : "U" + step.unitNumber())
                .toList();
        assertThat(steps).containsExactly("U1", "C:Mandarin for Campus Life", "U2", "U3", "C:Travel Mandarin", "U4",
                "C:Shopping and Prices", "U5");
        assertThat(plan.steps().get(0).status()).isEqualTo(PlanViews.StepStatus.IN_PROGRESS);
        PlanViews.Step campusLife = plan.steps().get(1);
        assertThat(campusLife.status()).isEqualTo(PlanViews.StepStatus.IN_MY_CLASSES);
        assertThat(campusLife.nextLesson()).isNotNull();
        assertThat(campusLife.nextLesson().kind()).isEqualTo(LearnLessonKind.LESSON);
        assertThat(plan.steps().get(4).status()).isEqualTo(PlanViews.StepStatus.OPTIONAL);
        var membership = studentClasses.catalogueMembership(amira);
        // The archived Foundations class still counts. Travel and Shopping are plan picks she has not joined.
        assertThat(membership.joinedCount()).isEqualTo(2);
        assertThat(membership.plannedClassIds()).containsExactly(campusLife.refId(),
                plan.steps().get(4).refId(), plan.steps().get(6).refId());
        assertThat(plan.goal().deadline()).isEqualTo(plan.today().minusDays(8).plusDays(70));

        HomeView home = homeService.home(amira);
        assertThat(home.email().confirmed()).isTrue();
        assertThat(home.setupState()).isEqualTo(PlanSetupState.COMPLETED);
        assertThat(home.next().title()).isEqualTo("Initials 1 · b p m f and more");
        assertThat(home.next().phrase()).isNotNull();
        assertThat(home.next().phrase().hanzi()).isEqualTo("爸");
        assertThat(home.plan().optionalClass()).isNotNull();
        assertThat(home.plan().optionalClass().title()).isEqualTo("Travel Mandarin");
        assertThat(home.plan().week()).isEqualTo(2);
        assertThat(home.plan().totalWeeks()).isEqualTo(10);
        assertThat(home.plan().milestoneDate()).isNull();

        long versions = planRepository.count();
        demoPlanSeeder.run();
        assertThat(planRepository.count()).isEqualTo(versions);
    }

    @Test
    void chenHaoHasARuleBasedPlanAndZhangYiSetItUpLater() {
        PlanViews.Plan chen = planService.current(signedIn(DemoPersonas.CHEN_HAO));
        assertThat(chen.aiAssisted()).isFalse();
        assertThat(chen.goal().title()).isNull();
        assertThat(chen.steps()).isNotEmpty();

        User zhang = userRepository.findByEmailWithRoles(DemoPersonas.ZHANG_YI.email()).orElseThrow();
        assertThat(studentProfileRepository.findByUserId(zhang.getId()).orElseThrow().getPlanSetupState())
                .isEqualTo(PlanSetupState.SKIPPED);
        assertThat(planService.current(new SecurityUserDetails(zhang))).isNull();
    }

    private SecurityUserDetails signedIn(DemoPersonas.Persona persona) {
        return new SecurityUserDetails(userRepository.findByEmailWithRoles(persona.email()).orElseThrow());
    }
}
