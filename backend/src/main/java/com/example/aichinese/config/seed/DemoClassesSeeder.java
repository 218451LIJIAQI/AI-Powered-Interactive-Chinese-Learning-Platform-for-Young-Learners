package com.example.aichinese.config.seed;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.config.seed.DemoClassContent.LessonSpec;
import com.example.aichinese.config.seed.DemoClassContent.Question;
import com.example.aichinese.config.seed.DemoClassContent.UnitSpec;
import com.example.aichinese.config.seed.DemoClassContent.Word;
import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.entity.ClassActivityCompletion;
import com.example.aichinese.entity.ClassEnrolment;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.LearningCategory;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonKeyPhrase;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.PackageItem;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.VideoProgress;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.enums.ClassActivityType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.RecordedVideoSourceType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.classes.ClassContentSupport;
import com.example.aichinese.service.classes.TracingCharacters;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Demo classes as in the prototype, made by the MandarinSpark team: the 8 published classes of the catalogue
 * ("Mandarin for Campus Life" with Amira, Chen Hao and Zhang Yi working through it and their quiz attempts), "Mandarin
 * Foundations", which Amira finished before an admin archived it, and the admins' two draft classes. Every published
 * lesson is complete: a video, key phrases, the 3 activities and a 5-question quiz. A database that already has classes
 * is left as it is. Dates are relative to today.
 */
@Component
@Order(2)
@Profile("demo & !prod & !production")
@RequiredArgsConstructor
public class DemoClassesSeeder implements CommandLineRunner {

    /** The quiz player plays a link, and the demo ships no recordings: its listening questions use a speech service. */
    private static final String SPEECH_LINK = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=zh-CN&q=";

    private final UserRepository users;
    private final StudentProfileRepository students;
    private final LearningCategoryRepository topics;
    private final CoursePackageRepository classes;
    private final EntityManager entityManager;
    private final DemoClock clock;

    /** A seeded lesson with its parts, so progress can be written against them. */
    private record Seeded(Lesson lesson, Flashcard flashcards, TracingExercise tracing, MatchingGame matching, Quiz quiz,
            List<SeededQuestion> questions, RecordedLessonVideo video) {
        List<LessonActivity> activities() {
            return List.of(flashcards, tracing, matching);
        }
    }

    private record SeededQuestion(QuizQuestion question, List<QuizOption> options) {
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (classes.count() > 0) {
            return;
        }
        User author = users.findAllByRoleName(RoleName.ADMIN).stream().findFirst().orElse(null);

        // The class Amira works through: published 8 weeks ago.
        CoursePackage campusLife = createClass("Mandarin for Campus Life",
                "Campus scenarios for exchange students: meeting classmates, the canteen and finding your way.",
                "Study or exchange", 3, ContentStatus.PUBLISHED, 57);
        List<Seeded> campusLessons = createUnits(campusLife, DemoClassContent.CAMPUS_LIFE, 56, author);
        Seeded meeting = campusLessons.get(0);
        Seeded rooms = campusLessons.get(1);
        Seeded universityLife = campusLessons.get(2);
        Seeded orderingFood = campusLessons.get(3);

        publishedClass("Chinese Through Film",
                "Short scenes filmed for this class: everyday lines, small talk and quick reactions.",
                "Personal interest", 5, DemoClassContent.THROUGH_FILM, 40, author);
        CoursePackage everyday = publishedClass("Everyday Conversations",
                "Meet people, chat at a hawker stall and get around town.",
                "Daily life", 3, DemoClassContent.EVERYDAY_CONVERSATIONS, 30, author);
        publishedClass("Travel Mandarin",
                "Airport, hotel, metro and restaurant scenes, plus signs and menus.",
                "Travel", 3, DemoClassContent.TRAVEL, 16, author);
        CoursePackage shopping = publishedClass("Shopping and Prices",
                "Ask how much, bargain at the market and pay in shops, using numbers and measure words from Unit 4.",
                "Travel", 4, DemoClassContent.SHOPPING_AND_PRICES, 14, author);
        publishedClass("Study Abroad Survival",
                "Registration day, your dorm, the library and asking classmates for help.",
                "Study or exchange", 3, DemoClassContent.STUDY_ABROAD, 18, author);
        publishedClass("Basic Work Mandarin",
                "Introduce yourself to colleagues, make small talk and handle simple calls and messages.",
                "Work and career", 3, DemoClassContent.BASIC_WORK, 22, author);
        publishedClass("Serving Customers",
                "Greet customers, give prices and answer simple questions at the counter.",
                "Work and career", 4, DemoClassContent.SERVING_CUSTOMERS, 26, author);

        // The first-steps class Amira finished; an admin archived it later (it stays open to her, read-only). Saved as
        // it is now: a class changed after its first save would show as updated when the demo was seeded (BaseEntity
        // stamps every update), not when it was archived.
        CoursePackage foundations = newClass("Mandarin Foundations",
                "Your first steps in Mandarin: the four tones, greetings, numbers, family and food.",
                "Daily life", 1, ContentStatus.PUBLISHED, 60);
        archive(foundations, 16);
        entityManager.persist(foundations);
        List<Seeded> foundationLessons = createUnits(foundations, DemoClassContent.MANDARIN_FOUNDATIONS, 59, author);

        // The admins' drafts: learners can't see them yet.
        CoursePackage culture = createClass("Chinese Culture and Festivals",
                "Festivals, food and customs of the Chinese calendar, with the words you need to join in.",
                "Personal interest", 3, ContentStatus.DRAFT, 1);
        LearningModule festivalStories = createUnit(culture, "Festival Stories", 1);
        createLesson(festivalStories, DemoClassContent.LANTERN_FESTIVAL, 1, ContentStatus.DRAFT, 1, author);
        createLesson(festivalStories, DemoClassContent.FESTIVAL_GREETINGS, 2, ContentStatus.DRAFT, 0, author);
        CoursePackage studyGroup = createClass("Study Group Mandarin",
                "Study together in Mandarin: library visits, group work and exam week.",
                "Study or exchange", 3, ContentStatus.DRAFT, 2);
        createLesson(createUnit(studyGroup, "Around Campus", 1), DemoClassContent.AT_THE_LIBRARY, 1,
                ContentStatus.DRAFT, 1, author);

        StudentProfile amira = student(DemoPersonas.STUDENT);
        StudentProfile chen = student(DemoPersonas.CHEN_HAO);
        StudentProfile zhang = student(DemoPersonas.ZHANG_YI);
        StudentProfile nur = student(DemoPersonas.NUR_AISYAH);
        join(amira, foundations, 40);
        join(amira, campusLife, 28);
        join(chen, campusLife, 30);
        join(zhang, campusLife, 21);
        join(nur, shopping, 12);
        join(nur, everyday, 10);

        // Amira finished Mandarin Foundations before it was archived (Greetings: 100 %).
        int daysAgo = 38;
        for (Seeded lesson : foundationLessons) {
            LessonHistory history = new LessonHistory(amira, lesson);
            history.completeAllActivities(daysAgo);
            history.watch(true, daysAgo);
            history.attempt(daysAgo, 19, 0, true, true, true, true, lesson != foundationLessons.getFirst());
            history.saveProgress();
            daysAgo -= 4;
        }

        // Amira finished Room Numbers and Ordering Food. Meeting Classmates still has one activity left.
        LessonHistory amiraRooms = new LessonHistory(amira, rooms);
        amiraRooms.completeAllActivities(7);
        amiraRooms.watch(true, 7);
        amiraRooms.attempt(6, 18, 0, true, true, true, true, true);
        amiraRooms.saveProgress();

        LessonHistory amiraFood = new LessonHistory(amira, orderingFood);
        amiraFood.completeAllActivities(4);
        amiraFood.watch(true, 4);
        amiraFood.attempt(3, 20, 0, true, false, true, true, false);
        amiraFood.saveProgress();

        LessonHistory amiraMeeting = new LessonHistory(amira, meeting);
        amiraMeeting.completeActivities(1, List.of(meeting.flashcards(), meeting.tracing()));
        amiraMeeting.watch(false, 1);
        amiraMeeting.attempt(2, 19, 30, true, true, true, false, false);
        amiraMeeting.attempt(0, 11, 45, true, true, true, true, false);
        amiraMeeting.saveProgress();

        // Chen Hao finished three lessons and started University Life.
        LessonHistory chenMeeting = new LessonHistory(chen, meeting);
        chenMeeting.completeAllActivities(12);
        chenMeeting.attempt(12, 21, 0, true, true, true, true, false);
        chenMeeting.saveProgress();

        LessonHistory chenRooms = new LessonHistory(chen, rooms);
        chenRooms.completeAllActivities(10);
        chenRooms.attempt(10, 21, 0, true, true, false, true, false);
        chenRooms.saveProgress();

        LessonHistory chenFood = new LessonHistory(chen, orderingFood);
        chenFood.completeAllActivities(5);
        chenFood.attempt(5, 20, 0, true, false, true, true, false);
        chenFood.saveProgress();

        LessonHistory chenUniversity = new LessonHistory(chen, universityLife);
        chenUniversity.watch(true, 1);
        chenUniversity.attempt(1, 22, 0, true, true, true, true, false);
        chenUniversity.saveProgress();

        // Zhang Yi finished the lesson on the last activity, after two quiz attempts.
        LessonHistory zhangMeeting = new LessonHistory(zhang, meeting);
        zhangMeeting.completeAllActivities(9);
        zhangMeeting.attempt(10, 15, 0, true, false, true, false, false);
        zhangMeeting.attempt(9, 16, 0, true, false, true, false, true);
        zhangMeeting.saveProgress();
    }

    // Classes, units and lessons

    private CoursePackage publishedClass(String title, String description, String topic, int bestAfterUnit,
            List<UnitSpec> units, int daysAgo, User author) {
        CoursePackage coursePackage = createClass(title, description, topic, bestAfterUnit, ContentStatus.PUBLISHED, daysAgo);
        createUnits(coursePackage, units, daysAgo - 1, author);
        return coursePackage;
    }

    private CoursePackage createClass(String title, String description, String topic, int bestAfterUnit,
            ContentStatus status, int daysAgo) {
        return persist(newClass(title, description, topic, bestAfterUnit, status, daysAgo));
    }

    private CoursePackage newClass(String title, String description, String topic, int bestAfterUnit,
            ContentStatus status, int daysAgo) {
        CoursePackage coursePackage = new CoursePackage();
        coursePackage.setTitle(title);
        coursePackage.setDescription(description);
        coursePackage.setCategory(topic(topic));
        coursePackage.setBestAfterUnit(bestAfterUnit);
        coursePackage.setContentStatus(status);
        coursePackage.setCreatedAt(clock.daysAgo(daysAgo, 9, 0));
        coursePackage.setUpdatedAt(clock.daysAgo(Math.max(0, daysAgo - 3), 9, 0));
        return coursePackage;
    }

    private void archive(CoursePackage coursePackage, int daysAgo) {
        coursePackage.setStatusBeforeArchive(coursePackage.getContentStatus());
        coursePackage.setContentStatus(ContentStatus.ARCHIVED);
        coursePackage.setArchivedAt(clock.daysAgo(daysAgo, 10, 0));
        coursePackage.setUpdatedAt(coursePackage.getArchivedAt());
    }

    /** The published units and lessons of a class; the lessons are returned in class order. */
    private List<Seeded> createUnits(CoursePackage coursePackage, List<UnitSpec> specs, int daysAgo, User author) {
        List<Seeded> lessons = new ArrayList<>();
        int unitPosition = 1;
        for (UnitSpec spec : specs) {
            LearningModule unit = createUnit(coursePackage, spec.title(), unitPosition++);
            int lessonPosition = 1;
            for (LessonSpec lessonSpec : spec.lessons()) {
                lessons.add(createLesson(unit, lessonSpec, lessonPosition++, ContentStatus.PUBLISHED, daysAgo, author));
            }
        }
        return lessons;
    }

    private LearningModule createUnit(CoursePackage coursePackage, String title, int position) {
        LearningModule unit = new LearningModule();
        unit.setTitle(title);
        unit.setContentStatus(ContentStatus.PUBLISHED);
        entityManager.persist(unit);
        PackageItem item = new PackageItem();
        item.setCoursePackage(coursePackage);
        item.setModule(unit);
        item.setSortOrder(position);
        entityManager.persist(item);
        return unit;
    }

    private Seeded createLesson(LearningModule unit, LessonSpec spec, int position, ContentStatus status, int daysAgo,
            User author) {
        Lesson lesson = new Lesson();
        lesson.setModule(unit);
        lesson.setTitle(spec.title());
        lesson.setSummary(spec.summary());
        lesson.setEstimatedDurationMinutes(spec.minutes());
        lesson.setUnitPosition(position);
        lesson.setContentStatus(status);
        lesson.setCreatedAt(clock.daysAgo(daysAgo, 10, position));
        lesson.setUpdatedAt(clock.daysAgo(Math.max(0, daysAgo - position), 10, position));
        entityManager.persist(lesson);
        int order = 1;
        for (Word word : spec.phrases()) {
            LessonKeyPhrase phrase = new LessonKeyPhrase();
            phrase.setLesson(lesson);
            phrase.setChinese(word.chinese());
            phrase.setPinyin(word.pinyin());
            phrase.setMeaning(word.meaning());
            phrase.setSortOrder(order++);
            entityManager.persist(phrase);
        }
        if (spec.isDraft()) {
            // A lesson an admin has only just started: its key phrases and nothing else yet.
            return new Seeded(lesson, null, null, null, null, List.of(), null);
        }
        // The parts share the lesson's status: those of a draft lesson go live when it is published.
        RecordedLessonVideo video = video(lesson, spec.video(), spec.videoSeconds());
        Flashcard flashcards = flashcards(lesson, spec.flashcards(), status, author);
        TracingExercise tracing = tracing(lesson, spec.trace(), status, author);
        MatchingGame matching = matching(lesson, spec.matching(), spec.pairs(), status, author);
        Quiz quiz = quiz(lesson, spec.title(), status);
        List<SeededQuestion> questions = new ArrayList<>();
        int questionOrder = 1;
        for (Question question : DemoClassContent.quiz(spec)) {
            questions.add(saveQuestion(quiz, question, questionOrder++));
        }
        return new Seeded(lesson, flashcards, tracing, matching, quiz, questions, video);
    }

    private RecordedLessonVideo video(Lesson lesson, String title, int seconds) {
        RecordedLessonVideo video = new RecordedLessonVideo();
        video.setLesson(lesson);
        video.setTitle(title);
        video.setSourceType(RecordedVideoSourceType.EXTERNAL_URL);
        video.setExternalVideoUrl("https://www.youtube.com/results?search_query="
                + URLEncoder.encode("learn mandarin " + title, StandardCharsets.UTF_8));
        video.setDurationSeconds(seconds);
        return persist(video);
    }

    private Flashcard flashcards(Lesson lesson, String title, ContentStatus status, User author) {
        Flashcard card = new Flashcard();
        card.setTitle(title);
        card.setCreatedByUser(author);
        card.setLearningStage(LearningStage.GUIDED);
        card.setContentStatus(status);
        card.setLesson(lesson);
        card.setLessonPosition(ClassActivityType.FLASHCARDS.lessonPosition());
        return persist(card);
    }

    private TracingExercise tracing(Lesson lesson, List<Word> characters, ContentStatus status, User author) {
        TracingExercise tracing = new TracingExercise();
        tracing.setTitle("Trace " + String.join(" and ", characters.stream().map(Word::chinese).toList()));
        tracing.setCreatedByUser(author);
        tracing.setLearningStage(LearningStage.GUIDED);
        tracing.setContentStatus(status);
        tracing.setLesson(lesson);
        tracing.setLessonPosition(ClassActivityType.TRACING.lessonPosition());
        TracingCharacters.write(tracing, characters.stream()
                .map(word -> new ClassRequests.TraceCharacter(word.chinese(), word.pinyin(), word.meaning()))
                .toList());
        return persist(tracing);
    }

    private MatchingGame matching(Lesson lesson, String title, List<Word> pairs, ContentStatus status, User author) {
        MatchingGame matching = new MatchingGame();
        matching.setTitle(title);
        matching.setCreatedByUser(author);
        matching.setLearningStage(LearningStage.SCAFFOLDED);
        matching.setContentStatus(status);
        matching.setLesson(lesson);
        matching.setLessonPosition(ClassActivityType.MATCHING.lessonPosition());
        entityManager.persist(matching);
        int order = 1;
        for (Word pair : pairs) {
            MatchingGameItem item = new MatchingGameItem();
            item.setMatchingGame(matching);
            item.setLeftText(pair.chinese());
            item.setPinyin(pair.pinyin());
            item.setRightText(pair.meaning());
            item.setSortOrder(order++);
            entityManager.persist(item);
        }
        return matching;
    }

    /** The lesson quiz, with "Allow SparkBot hints" on (A3.25 shows it on for the demo lessons). */
    private Quiz quiz(Lesson lesson, String title, ContentStatus status) {
        Quiz quiz = new Quiz();
        quiz.setLesson(lesson);
        quiz.setTitle(title);
        quiz.setHintsAllowed(true);
        quiz.setContentStatus(status);
        return persist(quiz);
    }

    /** Saves a new question of the quiz with the content and options of {@code question}. */
    private SeededQuestion saveQuestion(Quiz quiz, Question question, int order) {
        QuizQuestion target = new QuizQuestion();
        target.setQuiz(quiz);
        target.setPoints(2);
        target.setSortOrder(order);
        target.setQuestionType(question.type());
        target.setQuestionText(question.text());
        target.setExplanation(question.explanation());
        target.setHint(question.hint());
        target.setCorrectAnswerText(
                question.options().stream().filter(DemoClassContent.Option::correct).findFirst().orElseThrow().text());
        target.setAudioUrl(question.spoken() == null ? null
                : SPEECH_LINK + URLEncoder.encode(question.spoken(), StandardCharsets.UTF_8));
        entityManager.persist(target);
        List<QuizOption> options = new ArrayList<>(question.options().size());
        int optionOrder = 1;
        for (DemoClassContent.Option option : question.options()) {
            QuizOption entity = new QuizOption();
            entity.setQuestion(target);
            entity.setOptionText(option.text());
            entity.setCorrectOption(option.correct());
            entity.setSortOrder(optionOrder++);
            options.add(persist(entity));
        }
        return new SeededQuestion(target, options);
    }

    // Learners' work

    private void join(StudentProfile student, CoursePackage coursePackage, int daysAgo) {
        ClassEnrolment enrolment = new ClassEnrolment();
        enrolment.setStudent(student);
        enrolment.setCoursePackage(coursePackage);
        enrolment.setJoinedAt(clock.daysAgo(daysAgo, 20, 0));
        entityManager.persist(enrolment);
    }

    /** The learner's actual visits supply the progress dates, even when the demo lists events out of order. */
    @RequiredArgsConstructor
    private final class LessonHistory {
        private final StudentProfile student;
        private final Seeded seeded;
        private final List<LocalDateTime> activityTimes = new ArrayList<>();
        private final List<LocalDateTime> submissionTimes = new ArrayList<>();
        private LocalDateTime watchedAt;

        void completeAllActivities(int daysAgo) {
            completeActivities(daysAgo, seeded.activities());
        }

        void completeActivities(int daysAgo, List<LessonActivity> activities) {
            int minute = 0;
            for (LessonActivity activity : activities) {
                minute += 7;
                LocalDateTime completedAt = clock.daysAgo(daysAgo, 18, minute);
                ClassActivityCompletion completion = new ClassActivityCompletion();
                completion.setStudent(student);
                completion.setLesson(seeded.lesson());
                completion.setActivityType(ClassContentSupport.typeOf(activity));
                completion.setActivityId(activity.getId());
                completion.setCompletedAt(completedAt);
                completion.setCreatedAt(completedAt);
                completion.setUpdatedAt(completedAt);
                entityManager.persist(completion);
                activityTimes.add(completedAt);
            }
        }

        void watch(boolean completed, int daysAgo) {
            watchedAt = clock.daysAgo(daysAgo, 17, 50);
            VideoProgress progress = new VideoProgress();
            progress.setStudent(student);
            progress.setLesson(seeded.lesson());
            progress.setRecordedVideo(seeded.video());
            progress.setCompleted(completed);
            progress.setLastWatchedAt(watchedAt);
            progress.setCreatedAt(watchedAt);
            progress.setUpdatedAt(watchedAt);
            entityManager.persist(progress);
        }

        /** A submitted attempt with the given questions right; each wrong answer picks the first wrong option. */
        void attempt(int daysAgo, int hour, int minute, boolean... right) {
            LocalDateTime completedAt = clock.daysAgo(daysAgo, hour, minute);
            QuizAttempt attempt = new QuizAttempt();
            attempt.setQuiz(seeded.quiz());
            attempt.setStudent(student);
            attempt.setCreatedAt(completedAt);
            attempt.setUpdatedAt(completedAt);
            attempt.setCompletedAt(completedAt);
            attempt.setSubmitted(true);
            List<QuizAttemptAnswer> answers = new ArrayList<>();
            long score = 0;
            long max = 0;
            for (int index = 0; index < seeded.questions().size(); index++) {
                SeededQuestion entry = seeded.questions().get(index);
                QuizQuestion question = entry.question();
                boolean correct = right[index];
                QuizAttemptAnswer answer = new QuizAttemptAnswer();
                answer.setQuizAttempt(attempt);
                answer.setQuestion(question);
                answer.setCorrect(correct);
                QuizOption chosen = entry.options().stream()
                        .filter(option -> option.isCorrectOption() == correct)
                        .findFirst()
                        .orElseThrow();
                answer.setSelectedOption(chosen);
                answer.setAnswerText(chosen.getOptionText());
                answer.setCreatedAt(completedAt);
                answer.setUpdatedAt(completedAt);
                answers.add(answer);
                max += question.getPoints();
                score += correct ? question.getPoints() : 0;
            }
            attempt.setScore(score);
            attempt.setMaxScore(max);
            entityManager.persist(attempt);
            answers.forEach(entityManager::persist);
            submissionTimes.add(completedAt);
        }

        void saveProgress() {
            List<LocalDateTime> visits = new ArrayList<>(activityTimes);
            visits.addAll(submissionTimes);
            if (watchedAt != null) {
                visits.add(watchedAt);
            }
            LocalDateTime lastVisit = Collections.max(visits);
            LessonProgress progress = new LessonProgress();
            progress.setStudent(student);
            progress.setLesson(seeded.lesson());
            progress.setCreatedAt(Collections.min(visits));
            progress.setUpdatedAt(lastVisit);
            progress.setLastAccessedAt(lastVisit);
            // Completion needs all the activities and the first quiz submission. Later visits keep that date.
            if (activityTimes.size() == seeded.activities().size() && !submissionTimes.isEmpty()) {
                LocalDateTime lastActivity = Collections.max(activityTimes);
                LocalDateTime firstSubmission = Collections.min(submissionTimes);
                progress.setCompleted(true);
                progress.setCompletedAt(lastActivity.isAfter(firstSubmission) ? lastActivity : firstSubmission);
            }
            entityManager.persist(progress);
        }
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private StudentProfile student(DemoPersonas.Persona persona) {
        return students.findByUserEmail(persona.email()).orElseThrow();
    }

    private LearningCategory topic(String name) {
        return topics.findByName(name).orElseThrow(() -> new IllegalStateException(name + " topic must be seeded first."));
    }
}
