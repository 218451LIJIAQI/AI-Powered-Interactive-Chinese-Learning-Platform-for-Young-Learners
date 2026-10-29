package com.example.aichinese.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.aichinese.config.seed.DemoPersonas;
import com.example.aichinese.config.seed.DemoPlanSeeder;
import com.example.aichinese.dto.plan.HomeView;
import com.example.aichinese.dto.plan.PlanViews;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.PlanSetupState;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.plan.LearningPlanService;
import com.example.aichinese.service.plan.StudentHomeService;

/** The demo plans read through the real services, as Home and My learning plan show them. */
@SpringBootTest
@ActiveProfiles("demo")
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

    @Test
    void amiraIsInWeekTwoOfHerTravelPlanWithHerClassAndAnOptionalOne() throws Exception {
        SecurityUserDetails amira = signedIn(DemoPersonas.STUDENT);

        PlanViews.Plan plan = planService.current(amira);
        assertThat(plan.aiAssisted()).isTrue();
        assertThat(plan.goal().title()).isEqualTo("Travel to Shanghai");
        assertThat(plan.weeks().current()).isEqualTo(2);
        assertThat(plan.weeks().total()).isEqualTo(8);
        assertThat(plan.level().placed().name()).isEqualTo("BEGINNER_1");
        assertThat(plan.level().headStart()).isTrue();
        assertThat(plan.pace().dailyGoalXp()).isEqualTo(30);
        List<String> steps = plan.steps().stream()
                .map(step -> step.kind() == PlanStepKind.CLASS ? "C:" + step.title() : "U" + step.unitNumber())
                .toList();
        assertThat(steps).containsExactly(
                "U1", "C:Mandarin for Beginners", "U2", "C:Everyday Conversations", "U3");
        assertThat(plan.steps().get(0).status()).isEqualTo(PlanViews.StepStatus.IN_PROGRESS);
        assertThat(plan.steps().get(1).status()).isEqualTo(PlanViews.StepStatus.IN_MY_CLASSES);
        assertThat(plan.steps().get(3).status()).isEqualTo(PlanViews.StepStatus.OPTIONAL);
        assertThat(plan.goal().deadline()).isAfter(LocalDate.now().plusWeeks(6));

        HomeView home = homeService.home(amira);
        assertThat(home.email().confirmed()).isTrue();
        assertThat(home.setupState()).isEqualTo(PlanSetupState.COMPLETED);
        assertThat(home.next().title()).isEqualTo("Introduce yourself");
        assertThat(home.next().phrase()).isNotNull();
        assertThat(home.plan().classSteps()).hasSize(2);

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
