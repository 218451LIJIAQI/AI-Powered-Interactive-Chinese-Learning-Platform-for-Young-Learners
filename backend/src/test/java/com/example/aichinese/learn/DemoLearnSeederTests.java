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
import com.example.aichinese.dto.learn.SkillProgressResponse;
import com.example.aichinese.dto.learn.TodayResponse;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.MistakeSource;
import com.example.aichinese.repository.LearnLessonCompletionRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.learn.LearnPathService;
import com.example.aichinese.service.learn.MistakeService;
import com.example.aichinese.service.learn.ProgressOverviewService;
import com.example.aichinese.service.learn.SkillProgressService;
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

    @Autowired
    private SkillProgressService skillProgressService;

    @Test
    void amiraIsInUnitOneWithASevenDayStreakAndSixMistakesWaiting() throws Exception {
        StudentProfile amira = student(DemoPersonas.STUDENT);

        LearnPathResponse path = learnPathService.path(amira);
        assertThat(path.completedLessons()).isEqualTo(2);
        assertThat(path.nextLesson().title()).isEqualTo("Initials 1 · b p m f and more");
        assertThat(path.nextLesson().position()).isEqualTo(3);
        assertThat(path.nextLesson().unitLessonCount()).isEqualTo(8);
        // The 51 lessons of the path less Units 7 and 8, which are drafts students do not see.
        assertThat(path.totalLessons()).isEqualTo(40);

        TodayResponse today = xpService.today(amira);
        assertThat(today.dailyGoalXp()).isEqualTo(30);
        assertThat(today.todayXp()).isEqualTo(20);
        assertThat(today.goalReached()).isFalse();
        assertThat(today.currentStreak()).isEqualTo(7);
        assertThat(today.setsDoneToday()).isEqualTo(2);
        assertThat(today.week()).filteredOn(TodayResponse.Day::goalReached).hasSize(6);
        assertThat(mistakeService.waiting(amira)).isEqualTo(6);
        assertThat(mistakeService.waiting(amira, MistakeSource.LEARN)).isEqualTo(2);
        assertThat(mistakeService.waiting(amira, MistakeSource.PRACTICE)).isEqualTo(4);

        ProgressOverviewResponse progress = progressOverviewService.overview(amira, null);
        assertThat(progress.totalXp()).isEqualTo(250);
        assertThat(progress.level()).isEqualTo(3);
        assertThat(progress.badges()).filteredOn(ProgressOverviewResponse.BadgeView::earned).hasSize(5);
        assertThat(progress.sources().learn() + progress.sources().practice() + progress.sources().reviewsAndGoals())
                .isEqualTo(progress.totalXp());

        long completions = completionRepository.countByStudentId(amira.getId());
        demoLearnSeeder.run();
        assertThat(completionRepository.countByStudentId(amira.getId())).isEqualTo(completions);
        assertThat(xpService.today(student(DemoPersonas.NUR_AISYAH)).todayXp()).isZero();
    }

    @Test
    void amiraHasASpreadOfSkillsWithListeningTheWeakest() {
        SkillProgressResponse skills = skillProgressService.skills(student(DemoPersonas.STUDENT));
        assertThat(skills.skills()).extracting(SkillProgressResponse.SkillRow::skill).containsExactly(
                SkillProgressResponse.Skill.VOCABULARY, SkillProgressResponse.Skill.LISTENING,
                SkillProgressResponse.Skill.CHARACTERS, SkillProgressResponse.Skill.SENTENCES);
        // 7 of 20, 2 of 11, 2 of 9 and 4 of 19 items, each with some gained in the last 7 days.
        assertThat(skills.skills()).extracting(SkillProgressResponse.SkillRow::percent).containsExactly(35, 18, 22, 21);
        assertThat(skills.skills()).extracting(SkillProgressResponse.SkillRow::weeklyChange).containsExactly(10, 9, 11, 5);
        assertThat(skills.weakest()).isEqualTo(SkillProgressResponse.Skill.LISTENING);

        SkillProgressResponse nobody = skillProgressService.skills(student(DemoPersonas.NUR_AISYAH));
        assertThat(nobody.weakest()).isNull();
        assertThat(nobody.skills()).allSatisfy(row -> {
            assertThat(row.percent()).isZero();
            assertThat(row.weeklyChange()).isZero();
        });
    }

    private StudentProfile student(DemoPersonas.Persona persona) {
        Long userId = userRepository.findByEmailWithRoles(persona.email()).orElseThrow().getId();
        return studentProfileRepository.findByUserId(userId).orElseThrow();
    }
}
