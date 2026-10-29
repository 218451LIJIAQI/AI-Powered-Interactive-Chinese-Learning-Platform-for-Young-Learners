package com.example.aichinese.config.seed;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
import com.example.aichinese.entity.LearningMistake;
import com.example.aichinese.entity.PointsTransaction;
import com.example.aichinese.entity.PracticeCompletion;
import com.example.aichinese.entity.StudentBadge;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.AnswerKind;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearnQuestionType;
import com.example.aichinese.entity.enums.MistakeSource;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.BadgeRepository;
import com.example.aichinese.repository.DailyStreakRepository;
import com.example.aichinese.repository.LearnLessonCompletionRepository;
import com.example.aichinese.repository.LearnLessonRepository;
import com.example.aichinese.repository.LearnQuestionRepository;
import com.example.aichinese.repository.LearningMistakeRepository;
import com.example.aichinese.repository.PointsTransactionRepository;
import com.example.aichinese.repository.PracticeCompletionRepository;
import com.example.aichinese.repository.StudentBadgeRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.RewardBadge;
import com.example.aichinese.service.learn.GradableItem;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.learn.LearnPathService;
import com.example.aichinese.service.learn.PracticeCatalog;
import com.example.aichinese.service.learn.PracticeService;
import com.example.aichinese.service.learn.XpService;

/**
 * Demo learning histories that match the prototype: Amira is in Unit 1 with two lessons done, about 240 XP, a 7-day
 * streak ending yesterday, 20 of 30 XP today, six mistakes waiting (four from Learn, two from Practice) and five
 * badges. Chen Hao and Zhang Yi have shorter histories; Nur Aisyah has not started. Dates are relative to today.
 */
@Component
@Order(5)
@Profile("demo & !prod & !production")
public class DemoLearnSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LearnLessonRepository lessonRepository;
    private final LearnQuestionRepository questionRepository;
    private final LearnLessonCompletionRepository lessonCompletionRepository;
    private final PracticeCompletionRepository practiceCompletionRepository;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final LearningMistakeRepository mistakeRepository;
    private final DailyStreakRepository dailyStreakRepository;
    private final BadgeRepository badgeRepository;
    private final StudentBadgeRepository studentBadgeRepository;
    private final PracticeCatalog practiceCatalog;
    private final LearnClock clock;

    public DemoLearnSeeder(
            UserRepository userRepository,
            StudentProfileRepository studentProfileRepository,
            LearnLessonRepository lessonRepository,
            LearnQuestionRepository questionRepository,
            LearnLessonCompletionRepository lessonCompletionRepository,
            PracticeCompletionRepository practiceCompletionRepository,
            PointsTransactionRepository pointsTransactionRepository,
            LearningMistakeRepository mistakeRepository,
            DailyStreakRepository dailyStreakRepository,
            BadgeRepository badgeRepository,
            StudentBadgeRepository studentBadgeRepository,
            PracticeCatalog practiceCatalog,
            LearnClock clock) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.lessonCompletionRepository = lessonCompletionRepository;
        this.practiceCompletionRepository = practiceCompletionRepository;
        this.pointsTransactionRepository = pointsTransactionRepository;
        this.mistakeRepository = mistakeRepository;
        this.dailyStreakRepository = dailyStreakRepository;
        this.badgeRepository = badgeRepository;
        this.studentBadgeRepository = studentBadgeRepository;
        this.practiceCatalog = practiceCatalog;
        this.clock = clock;
    }

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
        History history = new History(student, 30);
        history.lesson("Hello and goodbye", 7, 86);
        history.practice(PracticeType.FLASHCARDS, "Campus Words", 7, 6, 6);
        history.goal(7);
        history.badge(RewardBadge.FIRST_STEPS, 7);

        history.practice(PracticeType.QUIZZES, "Greetings Quiz", 6, 6, 6);
        history.xp(XpService.SOURCE_QUIZ_STREAK, history.setId(PracticeType.QUIZZES, "Greetings Quiz"), 5,
                "Greetings Quiz", 6);
        history.goal(6);
        history.badge(RewardBadge.QUIZ_STAR, 6);
        history.badge(RewardBadge.XP_STARTER, 6);

        history.lesson("Polite phrases", 5, 80);
        history.practice(PracticeType.MATCHING, "Numbers Match", 5, 4, 4);
        history.goal(5);
        history.badge(RewardBadge.STREAK_EXPLORER, 5);

        history.practice(PracticeType.LISTENING, "At the Café", 4, 5, 5);
        history.review(4);
        history.goal(4);
        history.badge(RewardBadge.LESSON_FINISHER, 4);

        history.practice(PracticeType.DRAG_DROP, "Places on Campus", 3, 4, 4);
        history.practice(PracticeType.FLASHCARDS, "Numbers and Prices", 3, 6, 6);
        history.goal(3);

        history.practice(PracticeType.STORY, "A Day at the Library", 2, 2, 2);
        history.practice(PracticeType.LISTENING, "Numbers You Hear", 2, 5, 5);
        history.goal(2);

        history.practice(PracticeType.TRACING, "Write 中文", 1, 1, 1);
        history.practice(PracticeType.MATCHING, "Family Words", 1, 4, 4);
        history.practice(PracticeType.QUIZZES, "Food Quiz", 1, 4, 6);
        history.review(1);
        history.goal(1);

        history.practice(PracticeType.FLASHCARDS, "Food and Drink", 0, 6, 6);
        history.practice(PracticeType.TRACING, "Write 学", 0, 1, 1);
        history.streak(7, 7, 1);

        history.learnMistake("Hello and goodbye", LearnQuestionType.MEANING, 0, 0);
        history.learnMistake("Hello and goodbye", LearnQuestionType.LISTEN_CHOOSE, 1, 0);
        history.learnMistake("Polite phrases", LearnQuestionType.PINYIN, 0, 1);
        history.learnMistake("Polite phrases", LearnQuestionType.HANZI, 1, 1);
        history.practiceMistake(PracticeType.QUIZZES, "Food Quiz", 0, 1);
        history.practiceMistake(PracticeType.QUIZZES, "Food Quiz", 5, 1);
        history.save();
    }

    private void seedChenHao(StudentProfile student) {
        History history = new History(student, 20);
        history.lesson("Hello and goodbye", 3, 71);
        history.badge(RewardBadge.FIRST_STEPS, 3);
        history.lesson("Polite phrases", 2, 86);
        history.practice(PracticeType.FLASHCARDS, "Campus Words", 2, 6, 6);
        history.goal(2);
        history.lesson("Introduce yourself", 1, 100);
        history.practice(PracticeType.QUIZZES, "Numbers Quiz", 1, 5, 6);
        history.goal(1);
        history.badge(RewardBadge.XP_STARTER, 1);
        history.streak(2, 2, 1);
        history.save();
    }

    private void seedZhangYi(StudentProfile student) {
        History history = new History(student, 20);
        history.lesson("Hello and goodbye", 14, 85);
        history.practice(PracticeType.FLASHCARDS, "Campus Words", 14, 6, 6);
        history.goal(14);
        history.lesson("Polite phrases", 13, 90);
        history.practice(PracticeType.FLASHCARDS, "Family", 13, 6, 6);
        history.goal(13);
        history.lesson("Introduce yourself", 12, 80);
        history.practice(PracticeType.MATCHING, "Numbers Match", 12, 4, 4);
        history.goal(12);
        history.lesson("Numbers 1–10", 11, 75);
        history.lesson("Family", 10, 88);
        history.lesson("Unit 1 review", 9, 82);
        history.badge(RewardBadge.FIRST_STEPS, 14);
        history.badge(RewardBadge.XP_STARTER, 12);
        history.badge(RewardBadge.STREAK_EXPLORER, 12);
        history.badge(RewardBadge.LESSON_FINISHER, 11);
        history.badge(RewardBadge.UNIT_CHAMPION, 9);
        history.streak(3, 3, 12);
        history.save();
    }

    private Optional<StudentProfile> student(DemoPersonas.Persona persona) {
        return userRepository.findByEmailWithRoles(persona.email())
                .flatMap(user -> studentProfileRepository.findByUserId(user.getId()));
    }

    /** One student's back-dated learning history; {@link #save()} writes the XP total and the daily goal. */
    private final class History {

        private final StudentProfile student;
        private final int dailyGoal;
        private final List<LearnLesson> path = lessonRepository.findPathInOrder();
        private int totalXp;

        History(StudentProfile student, int dailyGoal) {
            this.student = student;
            this.dailyGoal = dailyGoal;
        }

        void lesson(String title, int daysAgo, int accuracy) {
            LearnLesson lesson = lesson(title);
            LearnLessonCompletion completion = new LearnLessonCompletion();
            completion.setStudent(student);
            completion.setLesson(lesson);
            completion.setAttempts(1);
            completion.setBestAccuracy(accuracy);
            completion.setXpAwarded(lesson.getXpReward());
            completion.setFirstCompletedAt(at(daysAgo, 9));
            completion.setLastCompletedAt(at(daysAgo, 9));
            completion.setCreatedAt(at(daysAgo, 9));
            lessonCompletionRepository.save(completion);
            xp(XpService.SOURCE_LEARN_LESSON, lesson.getId(), lesson.getXpReward(), lesson.getTitle(), daysAgo);
        }

        void practice(PracticeType type, String title, int daysAgo, int correct, int items) {
            PracticeActivityContent set = set(type, title);
            boolean allCorrect = correct == items;
            int xp = allCorrect && set.getXpReward() != null ? set.getXpReward() : 0;
            PracticeCompletion completion = new PracticeCompletion();
            completion.setStudent(student);
            completion.setPracticeType(type);
            completion.setActivityId(set.getId());
            completion.setCorrectCount(correct);
            completion.setItemCount(items);
            completion.setXpAwarded(xp);
            completion.setCreatedAt(at(daysAgo, 12));
            practiceCompletionRepository.save(completion);
            if (allCorrect) {
                xp(XpService.practiceSource(type), set.getId(), xp, set.getTitle(), daysAgo);
            }
        }

        void review(int daysAgo) {
            xp(XpService.SOURCE_MISTAKES_REVIEW, clock.today().minusDays(daysAgo).toEpochDay(), 10, "Mistakes review",
                    daysAgo);
        }

        void goal(int daysAgo) {
            xp(XpService.SOURCE_DAILY_GOAL, clock.today().minusDays(daysAgo).toEpochDay(), 5, "Daily goal reached",
                    daysAgo);
        }

        void xp(String source, Long sourceId, int xp, String title, int daysAgo) {
            PointsTransaction transaction = new PointsTransaction();
            transaction.setStudent(student);
            transaction.setSourceType(source);
            transaction.setSourceId(sourceId);
            transaction.setPointsDelta(xp);
            transaction.setReason(title);
            transaction.setCreatedAt(at(daysAgo, 18));
            pointsTransactionRepository.save(transaction);
            totalXp += xp;
        }

        void badge(RewardBadge rewardBadge, int daysAgo) {
            Badge badge = badgeRepository.findByName(rewardBadge.badgeName()).orElseThrow();
            StudentBadge earned = new StudentBadge();
            earned.setStudent(student);
            earned.setBadge(badge);
            earned.setAwardedAt(at(daysAgo, 19));
            studentBadgeRepository.save(earned);
        }

        void streak(int current, int best, int lastDaysAgo) {
            DailyStreak streak = new DailyStreak();
            streak.setStudent(student);
            streak.setCurrentStreak(current);
            streak.setBestStreak(best);
            streak.setLastLearningDate(clock.today().minusDays(lastDaysAgo));
            dailyStreakRepository.save(streak);
        }

        /** A wrong answer kept from a Learn lesson: the question of that type, answered with another option. */
        void learnMistake(String lessonTitle, LearnQuestionType type, int wrongOption, int daysAgo) {
            LearnLesson lesson = lesson(lessonTitle);
            LearnQuestion question = questionRepository.findByLessonIdOrderByPositionAsc(lesson.getId()).stream()
                    .filter(candidate -> candidate.getQuestionKind() == type)
                    .findFirst()
                    .orElseThrow();
            GradableItem item = LearnPathService.gradable(question);
            mistake(MistakeSource.LEARN, LearnPathService.mistakeRef(question), lesson.getTitle(), item,
                    wrongAnswer(item, wrongOption), daysAgo);
        }

        void practiceMistake(PracticeType type, String title, int itemIndex, int daysAgo) {
            PracticeActivityContent set = set(type, title);
            PracticeCatalog.Item item = practiceCatalog.content(type, set).items().get(itemIndex);
            mistake(MistakeSource.PRACTICE, PracticeService.mistakeRef(type, set, item), set.getTitle(),
                    item.gradable(), wrongAnswer(item.gradable(), 0), daysAgo);
        }

        private void mistake(MistakeSource source, String itemRef, String title, GradableItem item, String yourAnswer,
                int daysAgo) {
            LearningMistake mistake = new LearningMistake();
            mistake.setStudent(student);
            mistake.setMistakeSource(source);
            mistake.setItemRef(itemRef);
            mistake.setSourceTitle(title);
            mistake.setAnswerKind(item.answerKind());
            mistake.setQuestionKind(item.type());
            mistake.setPrompt(item.prompt());
            mistake.setHanzi(item.hanzi());
            mistake.setPinyin(item.pinyin());
            mistake.setMeaning(item.meaning());
            mistake.setAudioText(item.audioText());
            mistake.setChoices(item.choicesJson());
            mistake.setAnswer(item.answer());
            mistake.setExplanation(item.explanation());
            mistake.setYourAnswer(yourAnswer);
            mistake.setWrongCount(1);
            mistake.setLastWrongAt(at(daysAgo, 10));
            mistake.setCreatedAt(at(daysAgo, 10));
            mistakeRepository.save(mistake);
        }

        /** The n-th option that is not the answer. */
        private String wrongAnswer(GradableItem item, int n) {
            List<String> wrong = item.options().stream()
                    .map(option -> option.text())
                    .filter(text -> !item.isCorrect(text))
                    .toList();
            return item.answerKind() == AnswerKind.CHOICE && !wrong.isEmpty() ? wrong.get(n % wrong.size()) : null;
        }

        void save() {
            student.setTotalPoints(totalXp);
            student.setDailyGoalXp(dailyGoal);
            studentProfileRepository.save(student);
        }

        Long setId(PracticeType type, String title) {
            return set(type, title).getId();
        }

        private LearnLesson lesson(String title) {
            return path.stream().filter(lesson -> lesson.getTitle().equals(title)).findFirst().orElseThrow();
        }

        private PracticeActivityContent set(PracticeType type, String title) {
            return practiceCatalog.sets(type, ContentStatus.PUBLISHED).stream()
                    .filter(set -> Objects.equals(set.getTitle(), title))
                    .findFirst()
                    .orElseThrow();
        }

        /** A time on a past platform day (server-local, as stored). */
        private LocalDateTime at(int daysAgo, int hour) {
            LocalDate day = clock.today().minusDays(daysAgo);
            LocalDateTime time = clock.startOf(day).plusHours(hour);
            LocalDateTime now = clock.now();
            return daysAgo == 0 && time.isAfter(now) ? now.minusMinutes(5) : time;
        }
    }
}
