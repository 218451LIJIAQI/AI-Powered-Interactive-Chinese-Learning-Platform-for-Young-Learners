package com.example.aichinese.config.seed;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.Badge;
import com.example.aichinese.entity.DailyStreak;
import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LearnLessonCompletion;
import com.example.aichinese.entity.LearnQuestion;
import com.example.aichinese.entity.PointsTransaction;
import com.example.aichinese.entity.PracticeCompletion;
import com.example.aichinese.entity.StudentBadge;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearnQuestionType;
import com.example.aichinese.entity.enums.MistakeSource;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.BadgeRepository;
import com.example.aichinese.repository.LearnLessonCompletionRepository;
import com.example.aichinese.repository.LearnLessonRepository;
import com.example.aichinese.repository.LearnQuestionRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.service.RewardBadge;
import com.example.aichinese.service.RewardRules;
import com.example.aichinese.service.learn.GradableItem;
import com.example.aichinese.service.learn.LearnPathService;
import com.example.aichinese.service.learn.MistakeService;
import com.example.aichinese.service.learn.PracticeCatalog;
import com.example.aichinese.service.learn.PracticeSetDirectory;
import com.example.aichinese.service.learn.PracticeService;
import com.example.aichinese.service.learn.XpService;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Demo learning histories that match the prototype: Amira is in Unit 1 with two lessons done (next: Initials 1), 279 XP (Rank 3),
 * a 7-day streak ending yesterday, 20 of 30 XP today, six mistakes waiting (two from Learn, four from Practice), five
 * badges and a spread of skills with Listening the weakest. Chen Hao and Zhang Yi have shorter histories; Nur Aisyah
 * has not started. Dates are relative to today.
 *
 * <p>The histories follow the reward rules: a day is a daily-goal day (+5 XP, and a streak day) exactly when its XP
 * reaches the goal of that day, which {@link History#save()} works out from the XP written; every account's goal was
 * the default 20 XP, and Amira raised hers to 30 today. A set pays its XP the first time every item is right (a
 * flashcard set the first time it is reviewed), a review that clears a mistake +10 once a day, and each badge is dated
 * on the day its rule was met.
 */
@Component
@Order(5)
@Profile("demo & !prod & !production")
@RequiredArgsConstructor
public class DemoLearnSeeder implements CommandLineRunner {

    private static final String FOUR_TONES = "The four tones · 声调";
    private static final String SIMPLE_FINALS = "Simple finals · a o e i u ü";
    private static final String INITIALS_1 = "Initials 1 · b p m f and more";

    private final StudentProfileRepository studentProfileRepository;
    private final LearnLessonRepository lessonRepository;
    private final LearnQuestionRepository questionRepository;
    private final LearnLessonCompletionRepository lessonCompletionRepository;
    private final MistakeService mistakeService;
    private final BadgeRepository badgeRepository;
    private final PracticeCatalog practiceCatalog;
    private final PracticeSetDirectory practiceSets;
    private final EntityManager entityManager;
    private final DemoClock clock;

    @Override
    @Transactional
    public void run(String... args) {
        Optional<StudentProfile> amira = student(DemoPersonas.STUDENT);
        if (amira.isEmpty() || lessonCompletionRepository.countByStudentId(amira.get().getId()) > 0) {
            return;
        }
        seedAmira(amira.get());
        student(DemoPersonas.CHEN_HAO).ifPresent(this::seedChenHao);
        student(DemoPersonas.ZHANG_YI).ifPresent(this::seedZhangYi);
    }

    private void seedAmira(StudentProfile student) {
        History history = new History(student);
        // Her class quiz in Mandarin Foundations (DemoClassesSeeder) scored 80 % or more.
        history.badge(RewardBadge.QUIZ_STAR, 38);
        // A few sets before the streak, one a day, each short of the 20 XP goal.
        history.practice(PracticeType.FLASHCARDS, "Campus Words", 12, 6, 6);
        history.badge(RewardBadge.FIRST_STEPS, 12);
        history.practice(PracticeType.TRACING, "Write 中文", 10, 1, 1);
        history.practice(PracticeType.DRAG_DROP, "Places on Campus", 9, 4, 4);

        // The 7-day streak: 20 XP or more each day, the most she practised.
        history.lesson(FOUR_TONES, 7, 86);
        history.practice(PracticeType.MATCHING, "Numbers Match", 7, 4, 4);
        // 66 XP by then, and her fifth lesson or set.
        history.badge(RewardBadge.XP_STARTER, 7);
        history.badge(RewardBadge.LESSON_FINISHER, 7);

        history.practice(PracticeType.DRAG_DROP, "Order a Drink", 6, 4, 4);
        history.practice(PracticeType.FLASHCARDS, "Numbers and Prices", 6, 6, 6);
        history.practice(PracticeType.TRACING, "Write 日月", 6, 1, 1);

        history.lesson(SIMPLE_FINALS, 5, 80);
        history.practice(PracticeType.DRAG_DROP, "Ask the Way", 5, 4, 4);
        history.badge(RewardBadge.STREAK_EXPLORER, 5);

        // 你好 and 谢谢 still being learned: the review pays the set's XP all the same.
        history.practice(PracticeType.FLASHCARDS, "Greetings", 4, 4, 6);
        history.stillLearning("Greetings", 0, 4);
        history.stillLearning("Greetings", 1, 4);
        history.practice(PracticeType.MATCHING, "Colours", 4, 4, 4);

        history.practice(PracticeType.DRAG_DROP, "Tell the Time", 3, 4, 4);
        history.review(3);

        // 4 of 5: one phrase heard wrong, so no XP for the set yet. Rank 3 (200 XP) that day.
        history.practice(PracticeType.LISTENING, "Asking the Way", 2, 4, 5);
        history.practiceMistake(PracticeType.LISTENING, "Asking the Way", 2, 2);
        history.practice(PracticeType.TRACING, "Write 你好", 2, 1, 1);
        history.practice(PracticeType.MATCHING, "Places on Campus", 2, 4, 4);

        history.practice(PracticeType.MATCHING, "Family Words", 1, 4, 4);
        // 菜单 still being learned.
        history.practice(PracticeType.FLASHCARDS, "Food and Drink", 1, 5, 6);
        history.stillLearning("Food and Drink", 5, 1);

        history.practice(PracticeType.TRACING, "Write 学", 0, 1, 1);
        history.practice(PracticeType.FLASHCARDS, "Family", 0, 6, 6);

        // Click what you hear: pà, answered bà. The other Learn mistake is from the lesson before it.
        history.learnMistake(INITIALS_1, LearnQuestionType.LISTEN_CHOOSE, 1);
        history.learnMistake(SIMPLE_FINALS, LearnQuestionType.PINYIN, 2);
        // Raised this morning: today's 20 XP do not reach it yet.
        student.setDailyGoalXp(30);
        history.save();
    }

    private void seedChenHao(StudentProfile student) {
        History history = new History(student);
        // His class quiz on Meeting Classmates (DemoClassesSeeder) scored 80 %.
        history.badge(RewardBadge.QUIZ_STAR, 12);
        history.lesson(FOUR_TONES, 3, 71);
        history.badge(RewardBadge.FIRST_STEPS, 3);
        history.lesson(SIMPLE_FINALS, 2, 86);
        history.practice(PracticeType.FLASHCARDS, "Campus Words", 2, 6, 6);
        history.lesson(INITIALS_1, 1, 100);
        // 4 of 5: the wrong answer was cleared in a review the same day (not counted as a finished set).
        history.practice(PracticeType.LISTENING, "Numbers You Hear", 1, 4, 5);
        history.review(1);
        history.badge(RewardBadge.XP_STARTER, 1);
        history.save();
    }
    private void seedZhangYi(StudentProfile student) {
        History history = new History(student);
        history.lesson(FOUR_TONES, 14, 85);
        history.practice(PracticeType.FLASHCARDS, "Campus Words", 14, 6, 6);
        history.badge(RewardBadge.FIRST_STEPS, 14);
        history.lesson(SIMPLE_FINALS, 13, 90);
        history.practice(PracticeType.FLASHCARDS, "Family", 13, 6, 6);
        history.badge(RewardBadge.XP_STARTER, 13);
        history.lesson(INITIALS_1, 12, 80);
        history.practice(PracticeType.MATCHING, "Numbers Match", 12, 4, 4);
        history.badge(RewardBadge.STREAK_EXPLORER, 12);
        history.badge(RewardBadge.LESSON_FINISHER, 12);
        // Then one lesson a day, short of the goal: the 3-day streak ended.
        history.lesson("Initials 2 · zh ch sh and more", 11, 75);
        history.lesson("Compound finals · ai ei ao ou", 10, 88);
        history.lesson("Nasal finals · an en ang eng", 9, 84);
        history.lesson("Unit 1 review", 8, 82);
        history.badge(RewardBadge.UNIT_CHAMPION, 8);
        history.save();
    }

    private Optional<StudentProfile> student(DemoPersonas.Persona persona) {
        return studentProfileRepository.findByUserEmail(persona.email());
    }

    /** One student's back-dated learning history; {@link #save()} writes the goal days, the streak and the XP total. */
    private final class History {

        private final StudentProfile student;
        private final List<LearnLesson> path = lessonRepository.findPathInOrder();
        private final Map<PracticeType, List<PracticeActivityContent>> publishedSets = new EnumMap<>(PracticeType.class);
        /** The daily goal the account had while the history was made (before today). */
        private final int goalBeforeToday;
        /** XP earned per day (days ago), the daily-goal bonus aside; the oldest day first. */
        private final Map<Integer, Integer> earnedByDay = new TreeMap<>(Comparator.reverseOrder());
        private final Set<RewardBadge> awardedBadges = EnumSet.noneOf(RewardBadge.class);
        private final Set<Long> completedLessonIds = new HashSet<>();
        private final Set<String> finishedPracticeSets = new HashSet<>();
        private int finishedCount;
        private int totalXp;

        History(StudentProfile student) {
            this.student = student;
            this.goalBeforeToday = student.getDailyGoalXp();
        }

        void lesson(String title, int daysAgo, int accuracy) {
            LearnLesson lesson = lesson(title);
            LocalDateTime completedAt = clock.onDayAgo(daysAgo, 9);
            LearnLessonCompletion completion = new LearnLessonCompletion();
            completion.setStudent(student);
            completion.setLesson(lesson);
            completion.setAttempts(1);
            completion.setBestAccuracy(accuracy);
            completion.setFirstCompletedAt(completedAt);
            completion.setLastCompletedAt(completedAt);
            completion.setCreatedAt(completedAt);
            lessonCompletionRepository.save(completion);
            if (lesson.getLessonKind().countsAsLesson() && completedLessonIds.add(lesson.getId())) {
                finishedCount++;
            }
            xp(XpService.SOURCE_LEARN_LESSON, lesson.getId(), lesson.getXpReward(), lesson.getTitle(), daysAgo);
        }

        void practice(PracticeType type, String title, int daysAgo, int correct, int items) {
            PracticeActivityContent set = set(type, title);
            PracticeCompletion completion = new PracticeCompletion();
            completion.setStudent(student);
            completion.setPracticeType(type);
            completion.setActivityId(set.getId());
            completion.setCorrectCount(correct);
            completion.setItemCount(items);
            completion.setCreatedAt(clock.onDayAgo(daysAgo, 12));
            entityManager.persist(completion);
            // A flashcard set is a review: its first one pays the XP whatever the ratings.
            if (items > 0 && (correct == items || type == PracticeType.FLASHCARDS)) {
                if (finishedPracticeSets.add(type.name() + ":" + set.getId())) {
                    finishedCount++;
                    xp(type.referenceType(), set.getId(), set.getXpReward(), set.getTitle(), daysAgo);
                }
            }
        }

        /** A Mistakes review that cleared at least one mistake: +10 XP, once that day. */
        void review(int daysAgo) {
            xp(XpService.SOURCE_MISTAKES_REVIEW, clock.today().minusDays(daysAgo).toEpochDay(),
                    RewardRules.MISTAKES_REVIEW_XP, "Mistakes review", daysAgo);
        }

        void xp(String source, Long sourceId, int xp, String title, int daysAgo) {
            write(source, sourceId, xp, title, daysAgo);
            earnedByDay.merge(daysAgo, xp, Integer::sum);
        }

        private void write(String source, Long sourceId, int xp, String title, int daysAgo) {
            PointsTransaction transaction = new PointsTransaction();
            transaction.setStudent(student);
            transaction.setSourceType(source);
            transaction.setSourceId(sourceId);
            transaction.setPointsDelta(xp);
            transaction.setReason(title);
            transaction.setCreatedAt(clock.onDayAgo(daysAgo, 18));
            entityManager.persist(transaction);
            totalXp += xp;
        }

        void badge(RewardBadge rewardBadge, int daysAgo) {
            Badge badge = badgeRepository.findByName(rewardBadge.badgeName()).orElseThrow();
            StudentBadge earned = new StudentBadge();
            earned.setStudent(student);
            earned.setBadge(badge);
            earned.setAwardedAt(clock.onDayAgo(daysAgo, 19));
            entityManager.persist(earned);
            awardedBadges.add(rewardBadge);
        }

        /** A wrong answer kept from a Learn lesson: the question of that type, answered with another option. */
        void learnMistake(String lessonTitle, LearnQuestionType type, int daysAgo) {
            LearnLesson lesson = lesson(lessonTitle);
            LearnQuestion question = questionRepository.findByLessonIdOrderByPositionAscIdAsc(lesson.getId()).stream()
                    .filter(candidate -> candidate.getQuestionKind() == type)
                    .findFirst()
                    .orElseThrow();
            GradableItem item = LearnPathService.gradable(question);
            mistake(MistakeSource.LEARN, LearnPathService.mistakeRef(question), lesson.getTitle(), item, daysAgo);
        }

        /** A wrong answer kept from a Practice set. */
        void practiceMistake(PracticeType type, String title, int itemIndex, int daysAgo) {
            PracticeActivityContent set = set(type, title);
            PracticeCatalog.Item item = practiceCatalog.content(type, set).items().get(itemIndex);
            mistake(MistakeSource.PRACTICE, PracticeService.mistakeRef(type, set, item), set.getTitle(), item.gradable(), daysAgo);
        }

        /** A flashcard marked "Still learning", kept as Flashcards keeps it and dated back. */
        void stillLearning(String title, int cardIndex, int daysAgo) {
            PracticeActivityContent set = set(PracticeType.FLASHCARDS, title);
            PracticeCatalog.SetContent content = practiceCatalog.content(PracticeType.FLASHCARDS, set);
            PracticeCatalog.Item card = content.items().get(cardIndex);
            mistakeService.recordStillLearning(student, PracticeService.mistakeRef(PracticeType.FLASHCARDS, set, card),
                    set.getTitle(), PracticeCatalog.flashcardQuestion(content, card))
                    .setLastWrongAt(clock.onDayAgo(daysAgo, 12));
        }

        /** Recorded as Learn and Practice record a wrong answer, then dated back (the row is flushed with the seeder). */
        private void mistake(MistakeSource source, String itemRef, String title, GradableItem item, int daysAgo) {
            mistakeService.record(student, source, itemRef, title, item, firstWrongOption(item), false)
                    .setLastWrongAt(clock.onDayAgo(daysAgo, 10));
        }

        private String firstWrongOption(GradableItem item) {
            return item.options().stream()
                    .map(option -> option.text())
                    .filter(text -> !item.isCorrect(text))
                    .findFirst()
                    .orElseThrow();
        }

        /**
         * Writes the daily-goal days and the streak the XP makes, as {@link XpService} does when it is earned: +5 XP on
         * each day whose XP reaches the goal of that day (the goal the account had when the history began, and the
         * student's goal now for today), then the XP total.
         */
        void save() {
            LocalDate today = clock.today();
            LocalDate lastGoalDay = null;
            int streak = 0;
            int best = 0;
            for (Map.Entry<Integer, Integer> day : earnedByDay.entrySet()) {
                int goal = day.getKey() == 0 ? student.getDailyGoalXp() : goalBeforeToday;
                if (day.getValue() < goal) {
                    continue;
                }
                LocalDate date = today.minusDays(day.getKey());
                write(XpService.SOURCE_DAILY_GOAL, date.toEpochDay(), RewardRules.DAILY_GOAL_BONUS_XP,
                        "Daily goal reached", day.getKey());
                streak = RewardRules.nextStreak(lastGoalDay, streak, date);
                best = Math.max(best, streak);
                lastGoalDay = date;
            }
            if (lastGoalDay != null) {
                DailyStreak stored = new DailyStreak();
                stored.setStudent(student);
                stored.setCurrentStreak(streak);
                stored.setBestStreak(best);
                stored.setLastLearningDate(lastGoalDay);
                entityManager.persist(stored);
            }
            student.setTotalPoints(totalXp);
            verifyBadges(best);
        }

        /** Guard against seeding an unearned badge that disagrees with the real {@link RewardRules}. */
        private void verifyBadges(int bestStreak) {
            boolean completedUnit = completedAnyWholeUnit();
            for (RewardBadge badge : awardedBadges) {
                boolean eligible = switch (badge) {
                    case FIRST_STEPS -> finishedCount >= 1;
                    case LESSON_FINISHER -> finishedCount >= RewardRules.LESSON_FINISHER_COUNT;
                    case UNIT_CHAMPION -> completedUnit;
                    case STREAK_EXPLORER -> bestStreak >= RewardRules.STREAK_EXPLORER_DAYS;
                    case TEN_DAY_STREAK -> bestStreak >= RewardRules.TEN_DAY_STREAK_DAYS;
                    case XP_STARTER -> totalXp >= RewardRules.XP_STARTER_THRESHOLD;
                    case XP_300 -> totalXp >= RewardRules.XP_300_THRESHOLD;
                    case QUIZ_STAR -> true; // Earned from DemoClassesSeeder class quiz attempts.
                };
                if (!eligible) {
                    throw new IllegalStateException("Seeded badge " + badge + " does not meet RewardRules for student "
                            + student.getId());
                }
            }
        }

        /** Matches LearnPathService's UnitState.DONE: every mandatory stop (lesson + review) in a unit is completed. */
        private boolean completedAnyWholeUnit() {
            Map<Long, List<LearnLesson>> mandatoryByUnit = new LinkedHashMap<>();
            for (LearnLesson lesson : path) {
                if (lesson.getLessonKind().countsAsLesson()) {
                    mandatoryByUnit.computeIfAbsent(lesson.getUnit().getId(), ignored -> new ArrayList<>()).add(lesson);
                }
            }
            return mandatoryByUnit.values().stream()
                    .anyMatch(unitLessons -> !unitLessons.isEmpty()
                            && unitLessons.stream().allMatch(lesson -> completedLessonIds.contains(lesson.getId())));
        }

        private LearnLesson lesson(String title) {
            return path.stream().filter(lesson -> lesson.getTitle().equals(title)).findFirst().orElseThrow();
        }

        private PracticeActivityContent set(PracticeType type, String title) {
            return publishedSets.computeIfAbsent(type, kind -> practiceSets.sets(kind, ContentStatus.PUBLISHED))
                    .stream()
                    .filter(set -> Objects.equals(set.getTitle(), title))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
