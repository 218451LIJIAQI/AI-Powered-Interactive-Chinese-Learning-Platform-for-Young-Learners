package com.example.aichinese.learn;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.aichinese.config.seed.DemoLearnSeeder;
import com.example.aichinese.config.seed.DemoPersonas;
import com.example.aichinese.dto.learn.LearnPathResponse;
import com.example.aichinese.dto.learn.ProgressOverviewResponse;
import com.example.aichinese.dto.learn.TodayResponse;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.repository.LearnLessonCompletionRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.learn.LearnPathService;
import com.example.aichinese.service.learn.MistakeService;
import com.example.aichinese.service.learn.ProgressOverviewService;
import com.example.aichinese.service.learn.XpService;

/** The demo histories read through the real services, as the student pages show them. */
@SpringBootTest
@ActiveProfiles("demo")
class DemoLearnSeederTests {

    @Autowired
    private DemoLearnSeeder demoLearnSeeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private LearnLessonCompletionRepository completionRepository;

    @Autowired
    private LearnPathService learnPathService;

    @Autowired
    private XpService xpService;

    @Autowired
    private MistakeService mistakeService;

    @Autowired
    private ProgressOverviewService progressOverviewService;

    @Test
    void amiraIsInUnitOneWithASevenDayStreakAndSixMistakesWaiting() throws Exception {
        StudentProfile amira = student(DemoPersonas.STUDENT);

        LearnPathResponse path = learnPathService.path(amira);
        assertThat(path.completedLessons()).isEqualTo(2);
        assertThat(path.nextLesson().title()).isEqualTo("Introduce yourself");

        TodayResponse today = xpService.today(amira);
        assertThat(today.dailyGoalXp()).isEqualTo(30);
        assertThat(today.todayXp()).isEqualTo(20);
        assertThat(today.goalReached()).isFalse();
        assertThat(today.currentStreak()).isEqualTo(7);
        assertThat(today.setsDoneToday()).isEqualTo(2);
        assertThat(today.week()).filteredOn(TodayResponse.Day::goalReached).hasSize(6);
        assertThat(mistakeService.waiting(amira)).isEqualTo(6);

        ProgressOverviewResponse progress = progressOverviewService.overview(amira, null);
        assertThat(progress.totalXp()).isBetween(230, 260);
        assertThat(progress.level()).isEqualTo(3);
        assertThat(progress.badges()).filteredOn(ProgressOverviewResponse.BadgeView::earned).hasSize(5);
        assertThat(progress.sources().learn() + progress.sources().practice() + progress.sources().reviewsAndGoals())
                .isEqualTo(progress.totalXp());

        long completions = completionRepository.countByStudentId(amira.getId());
        demoLearnSeeder.run();
        assertThat(completionRepository.countByStudentId(amira.getId())).isEqualTo(completions);
        assertThat(xpService.today(student(DemoPersonas.NUR_AISYAH)).todayXp()).isZero();
    }

    private StudentProfile student(DemoPersonas.Persona persona) {
        Long userId = userRepository.findByEmailWithRoles(persona.email()).orElseThrow().getId();
        return studentProfileRepository.findByUserId(userId).orElseThrow();
    }
}
