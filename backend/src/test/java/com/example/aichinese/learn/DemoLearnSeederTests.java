package com.example.aichinese.learn;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.aichinese.config.seed.DemoLearnSeeder;
import com.example.aichinese.config.seed.DemoPersonas;
import com.example.aichinese.dto.learn.LearnPathResponse;
import com.example.aichinese.dto.learn.MistakeListResponse;
import com.example.aichinese.dto.learn.ProgressOverviewResponse;
import com.example.aichinese.dto.learn.SkillProgressResponse;
import com.example.aichinese.dto.learn.TodayResponse;
import com.example.aichinese.dto.learn.UnitState;
import com.example.aichinese.entity.PointsTransaction;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.repository.LearnLessonCompletionRepository;
import com.example.aichinese.repository.PointsTransactionRepository;
import com.example.aichinese.repository.PracticeCompletionRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.service.RewardBadge;
import com.example.aichinese.service.RewardRules;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.learn.LearnPathService;
import com.example.aichinese.service.learn.MistakeService;
import com.example.aichinese.service.learn.ProgressOverviewService;
import com.example.aichinese.service.learn.SkillProgressService;
import com.example.aichinese.service.learn.XpService;

/** The demo histories read through the real services, as the student pages show them. */
@SpringBootTest
@ActiveProfiles({"demo", "demo-test"})
class DemoLearnSeederTests {

    @Autowired
    private DemoLearnSeeder demoLearnSeeder;

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

    @Autowired
    private PointsTransactionRepository pointsTransactionRepository;

    @Autowired
    private PracticeCompletionRepository practiceCompletionRepository;

    @Autowired
    private LearnClock clock;

    @Test
    void amiraIsInUnitOneWithASevenDayStreakAndSixMistakesWaiting() {
        StudentProfile amira = student(DemoPersonas.STUDENT);

        LearnPathResponse path = learnPathService.path(amira);
        assertThat(path.completedLessons()).isEqualTo(2);
        assertThat(path.nextLesson().title()).isEqualTo("Initials 1 · b p m f and more");
        // Unit 1's 7 lessons: its bonus chest is no lesson.
        assertThat(path.levels().get(0).units().get(0).lessonCount()).isEqualTo(7);
        // The 45 lessons of the path less Units 7 and 8, which are drafts students do not see (25 + 5 + 5).
        assertThat(path.levels().stream().flatMap(level -> level.units().stream())
                .mapToInt(LearnPathResponse.Unit::lessonCount).sum()).isEqualTo(35);

        TodayResponse today = xpService.today(amira);
        assertThat(today.dailyGoalXp()).isEqualTo(30);
        assertThat(today.todayXp()).isEqualTo(20);
        assertThat(today.goalReached()).isFalse();
        assertThat(xpService.streak(amira).current()).isEqualTo(7);
        assertThat(today.week()).filteredOn(TodayResponse.Day::goalReached).hasSize(6);
        MistakeListResponse mistakes = mistakeService.list(amira, null, 20);
        assertThat(mistakes.all()).isEqualTo(6);
        assertThat(mistakes.learn()).isEqualTo(2);
        assertThat(mistakes.practice()).isEqualTo(4);

        ProgressOverviewResponse progress = progressOverviewService.overview(amira, null);
        assertThat(progress.totalXp()).isEqualTo(279);
        assertThat(progress.level()).isEqualTo(3);
        assertThat(progress.badges()).filteredOn(ProgressOverviewResponse.BadgeView::earned).hasSize(5);

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
        // Each skill gained some in the last 7 days (the streak days are the days she practised most); the tracing sets
        // of this week (日月, 你好, 学) lift Characters most, while the listening sets she finished with a mistake keep
        // Listening the weakest.
        assertThat(skills.skills()).extracting(SkillProgressResponse.SkillRow::percent).containsExactly(44, 18, 44, 21);
        assertThat(skills.skills()).extracting(SkillProgressResponse.SkillRow::weeklyChange).containsExactly(31, 9, 33, 16);
        assertThat(skills.weakest()).isEqualTo(SkillProgressResponse.Skill.LISTENING);

        SkillProgressResponse nobody = skillProgressService.skills(student(DemoPersonas.NUR_AISYAH));
        assertThat(nobody.weakest()).isNull();
        assertThat(nobody.skills()).allSatisfy(row -> {
            assertThat(row.percent()).isZero();
            assertThat(row.weeklyChange()).isZero();
        });
    }

    /**
     * The histories follow the daily-goal rule: a day is a goal day (+5 XP and a streak day) exactly when the XP earned
     * that day reaches the goal of that day (the default 20 XP; Amira's 30 today), and the streak is the run of goal
     * days. The total is the sum of the XP written.
     */
    @Test
    void everyGoalDayHasTheXpThatReachesTheGoalAndNoOtherDayDoes() {
        LocalDate today = clock.today();
        Map<Long, List<PointsTransaction>> byStudent = pointsTransactionRepository.findAll().stream()
                .collect(Collectors.groupingBy(transaction -> transaction.getStudent().getId()));
        List<Integer> bestStreaks = new ArrayList<>();
        for (DemoPersonas.Persona persona : List.of(DemoPersonas.STUDENT, DemoPersonas.CHEN_HAO, DemoPersonas.ZHANG_YI,
                DemoPersonas.NUR_AISYAH)) {
            StudentProfile student = student(persona);
            Map<LocalDate, Integer> earned = new TreeMap<>();
            Map<LocalDate, Integer> totalByDay = new TreeMap<>();
            List<LocalDate> goalDays = new ArrayList<>();
            for (PointsTransaction transaction : byStudent.getOrDefault(student.getId(), List.of())) {
                LocalDate day = clock.dayOf(transaction.getCreatedAt());
                int points = transaction.getPointsDelta();
                totalByDay.merge(day, points, Integer::sum);
                if (XpService.SOURCE_DAILY_GOAL.equals(transaction.getSourceType())) {
                    assertThat(LocalDate.ofEpochDay(transaction.getSourceId())).as(persona.email()).isEqualTo(day);
                    goalDays.add(day);
                } else {
                    earned.merge(day, points, Integer::sum);
                }
            }
            earned.forEach((day, xp) -> {
                int goal = day.equals(today) ? student.getDailyGoalXp() : 20;
                assertThat(goalDays.contains(day)).as(persona.email() + " " + day + ": " + xp + " XP")
                        .isEqualTo(xp >= goal);
            });
            assertThat(earned.keySet()).as(persona.email()).containsAll(goalDays);
            // The XP badges are dated on the day the total reached them.
            Map<String, LocalDate> reached = new LinkedHashMap<>();
            int total = 0;
            for (Map.Entry<LocalDate, Integer> day : totalByDay.entrySet()) {
                total += day.getValue();
                if (total >= RewardRules.XP_STARTER_THRESHOLD) {
                    reached.putIfAbsent(RewardBadge.XP_STARTER.name(), day.getKey());
                }
                if (total >= RewardRules.XP_300_THRESHOLD) {
                    reached.putIfAbsent(RewardBadge.XP_300.name(), day.getKey());
                }
            }
            int best = 0;
            int run = 0;
            LocalDate last = null;
            for (LocalDate day : goalDays.stream().sorted().toList()) {
                run = last != null && last.plusDays(1).equals(day) ? run + 1 : 1;
                best = Math.max(best, run);
                last = day;
            }
            long finishedLessons = completionRepository.countByStudentIdAndLessonKinds(
                    student.getId(), List.of(LearnLessonKind.LESSON, LearnLessonKind.REVIEW));
            long finishedSets = practiceCompletionRepository.countFinishedSets(student.getId());
            long finishedTotal = finishedLessons + finishedSets;
            boolean finishedAnyUnit = learnPathService.path(student).levels().stream()
                    .flatMap(level -> level.units().stream())
                    .anyMatch(unit -> unit.state() == UnitState.DONE);
            assertThat(total).as(persona.email()).isEqualTo(student.getTotalPoints());
            for (ProgressOverviewResponse.BadgeView badge : progressOverviewService.overview(student, null).badges()) {
                if (badge.key().equals(RewardBadge.XP_STARTER.name()) || badge.key().equals(RewardBadge.XP_300.name())) {
                    assertThat(badge.earned() ? clock.dayOf(badge.earnedAt()) : null).as(persona.email() + " " + badge.key())
                            .isEqualTo(reached.get(badge.key()));
                } else if (badge.key().equals(RewardBadge.FIRST_STEPS.name())) {
                    assertThat(badge.earned()).as(persona.email() + " FIRST_STEPS").isEqualTo(finishedTotal >= 1);
                } else if (badge.key().equals(RewardBadge.LESSON_FINISHER.name())) {
                    assertThat(badge.earned()).as(persona.email() + " LESSON_FINISHER")
                            .isEqualTo(finishedTotal >= RewardRules.LESSON_FINISHER_COUNT);
                } else if (badge.key().equals(RewardBadge.UNIT_CHAMPION.name())) {
                    assertThat(badge.earned()).as(persona.email() + " UNIT_CHAMPION").isEqualTo(finishedAnyUnit);
                } else if (badge.key().equals(RewardBadge.STREAK_EXPLORER.name())) {
                    assertThat(badge.earned()).as(persona.email() + " STREAK_EXPLORER")
                            .isEqualTo(best >= RewardRules.STREAK_EXPLORER_DAYS);
                } else if (badge.key().equals(RewardBadge.TEN_DAY_STREAK.name())) {
                    assertThat(badge.earned()).as(persona.email() + " TEN_DAY_STREAK")
                            .isEqualTo(best >= RewardRules.TEN_DAY_STREAK_DAYS);
                }
            }

            XpService.StreakState streak = xpService.streak(student);
            assertThat(streak.best()).as(persona.email()).isEqualTo(best);
            assertThat(streak.current()).as(persona.email())
                    .isEqualTo(last != null && !last.isBefore(today.minusDays(1)) ? run : 0);
            bestStreaks.add(best);
        }
        // Amira's 7-day streak, Chen Hao's 2 days, Zhang Yi's 3 days two weeks ago, none for Nur Aisyah.
        assertThat(bestStreaks).containsExactly(7, 2, 3, 0);
    }

    private StudentProfile student(DemoPersonas.Persona persona) {
        return studentProfileRepository.findByUserEmail(persona.email()).orElseThrow();
    }
}
