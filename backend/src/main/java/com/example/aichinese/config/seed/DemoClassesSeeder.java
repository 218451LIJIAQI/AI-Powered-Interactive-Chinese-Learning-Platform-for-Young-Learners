package com.example.aichinese.config.seed;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.config.PlatformProperties;
import com.example.aichinese.config.seed.DemoClassContent.LessonSpec;
import com.example.aichinese.config.seed.DemoClassContent.Question;
import com.example.aichinese.config.seed.DemoClassContent.UnitSpec;
import com.example.aichinese.config.seed.DemoClassContent.Word;
import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.entity.ClassActivityCompletion;
import com.example.aichinese.entity.ClassContentEvent;
import com.example.aichinese.entity.ClassEnrolment;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.LearningCategory;
import com.example.aichinese.entity.LearningLevel;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonKeyPhrase;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.LiveClassEnrolment;
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
import com.example.aichinese.entity.TeacherFeedback;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.VideoProgress;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.enums.ClassActivityType;
import com.example.aichinese.entity.enums.ClassContentEventType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;
import com.example.aichinese.entity.enums.MeetingPlatform;
import com.example.aichinese.entity.enums.PackageItemType;
import com.example.aichinese.entity.enums.RecordedVideoSourceType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.ClassActivityCompletionRepository;
import com.example.aichinese.repository.ClassContentEventRepository;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.FlashcardRepository;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.LearningLevelRepository;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.repository.LessonKeyPhraseRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.LiveClassEnrolmentRepository;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.repository.MatchingGameRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.repository.QuizQuestionRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherFeedbackRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.TracingExerciseRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.repository.VideoProgressRepository;
import com.example.aichinese.service.classes.LessonRevisionService;
import com.example.aichinese.service.classes.TracingCharacters;

/**
 * Demo classes as in the prototype: Li Wei's "Mandarin for Beginners" (with Amira, Chen Hao and Zhang Yi working
 * through it, quiz attempts, feedback, live sessions this week and next and past ones with recordings, one lesson
 * revision in review and one with changes requested), "HSK 1 Prep", a new class in review and a draft class, and
 * the published classes of the other teachers (8 published classes in the catalogue). Dates are relative to today.
 */
@Component
@Order(2)
@Profile("demo & !prod & !production")
public class DemoClassesSeeder implements CommandLineRunner {

    private static final String CLASS_TITLE = "Mandarin for Beginners";

    private final Repos repos;
    private final LessonRevisionService revisions;
    private final PlatformProperties platformProperties;
    private LocalDateTime now;

    /** The repositories the seeder writes to, grouped to keep the constructor readable. */
    @Component
    @Profile("demo & !prod & !production")
    static class Repos {
        final UserRepository users;
        final TeacherProfileRepository teachers;
        final StudentProfileRepository students;
        final LearningLevelRepository levels;
        final LearningCategoryRepository topics;
        final CoursePackageRepository classes;
        final LearningModuleRepository units;
        final PackageItemRepository packageItems;
        final LessonRepository lessons;
        final LessonKeyPhraseRepository keyPhrases;
        final RecordedLessonVideoRepository videos;
        final FlashcardRepository flashcards;
        final TracingExerciseRepository tracing;
        final MatchingGameRepository matching;
        final MatchingGameItemRepository matchingItems;
        final QuizRepository quizzes;
        final QuizQuestionRepository questions;
        final QuizOptionRepository options;
        final QuizAttemptRepository attempts;
        final QuizAttemptAnswerRepository answers;
        final ClassEnrolmentRepository enrolments;
        final ClassActivityCompletionRepository completions;
        final VideoProgressRepository videoProgress;
        final TeacherFeedbackRepository feedback;
        final LiveClassRepository sessions;
        final LiveClassEnrolmentRepository bookings;
        final ClassContentEventRepository events;

        Repos(UserRepository users, TeacherProfileRepository teachers, StudentProfileRepository students,
                LearningLevelRepository levels, LearningCategoryRepository topics, CoursePackageRepository classes,
                LearningModuleRepository units, PackageItemRepository packageItems, LessonRepository lessons,
                LessonKeyPhraseRepository keyPhrases, RecordedLessonVideoRepository videos, FlashcardRepository flashcards,
                TracingExerciseRepository tracing, MatchingGameRepository matching, MatchingGameItemRepository matchingItems,
                QuizRepository quizzes, QuizQuestionRepository questions, QuizOptionRepository options,
                QuizAttemptRepository attempts, QuizAttemptAnswerRepository answers, ClassEnrolmentRepository enrolments,
                ClassActivityCompletionRepository completions,
                VideoProgressRepository videoProgress,
                TeacherFeedbackRepository feedback, LiveClassRepository sessions, LiveClassEnrolmentRepository bookings,
                ClassContentEventRepository events) {
            this.users = users;
            this.teachers = teachers;
            this.students = students;
            this.levels = levels;
            this.topics = topics;
            this.classes = classes;
            this.units = units;
            this.packageItems = packageItems;
            this.lessons = lessons;
            this.keyPhrases = keyPhrases;
            this.videos = videos;
            this.flashcards = flashcards;
            this.tracing = tracing;
            this.matching = matching;
            this.matchingItems = matchingItems;
            this.quizzes = quizzes;
            this.questions = questions;
            this.options = options;
            this.attempts = attempts;
            this.answers = answers;
            this.enrolments = enrolments;
            this.completions = completions;
            this.videoProgress = videoProgress;
            this.feedback = feedback;
            this.sessions = sessions;
            this.bookings = bookings;
            this.events = events;
        }
    }

    public DemoClassesSeeder(Repos repos, LessonRevisionService revisions, PlatformProperties platformProperties) {
        this.repos = repos;
        this.revisions = revisions;
        this.platformProperties = platformProperties;
    }

    /** A seeded lesson with its parts, so progress can be written against them. */
    private record Seeded(CoursePackage coursePackage, Lesson lesson, Flashcard flashcards, TracingExercise tracing,
            MatchingGame matching, Quiz quiz, List<QuizQuestion> questions, List<List<QuizOption>> options,
            RecordedLessonVideo video) {
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (repos.users.findByEmailWithRoles(DemoPersonas.TEACHER.email()).isEmpty()
                || repos.classes.findAll().stream().anyMatch(existing -> existing.getTitle().equals(CLASS_TITLE))) {
            return;
        }
        now = LocalDateTime.now(ZoneId.of(platformProperties.defaultTimeZone())).withSecond(0).withNano(0);
        User admin = repos.users.findAllByRoleName(RoleName.ADMIN).stream().findFirst().orElse(null);
        TeacherProfile liWei = teacher(DemoPersonas.TEACHER);
        LearningLevel beginner1 = level("Beginner 1");
        LearningLevel beginner2 = level("Beginner 2");

        // Li Wei's main class: published 8 weeks ago, lessons approved over time.
        CoursePackage beginners = createClass(liWei, CLASS_TITLE,
                "Short lessons for a confident start: greetings, numbers and everyday campus Mandarin.",
                "Start with Greetings, then come to Monday's live session. Bring one question in Chinese!",
                beginner1, topic("Sentences"), LearningType.HYBRID, ContentStatus.PUBLISHED, 57);
        event(beginners, null, liWei.getUser(), ClassContentEventType.CLASS_CREATED, null, 57);
        event(beginners, null, liWei.getUser(), ClassContentEventType.CLASS_SUBMITTED, "Greetings", 55);
        event(beginners, null, admin, ClassContentEventType.CLASS_APPROVED, "Greetings", 53);
        List<List<Seeded>> beginnerUnits = createUnits(beginners, liWei, DemoClassContent.MANDARIN_FOR_BEGINNERS, ContentStatus.PUBLISHED, 56);
        Seeded greetings = beginnerUnits.get(0).get(0);
        Seeded numbers = beginnerUnits.get(0).get(1);
        Seeded universityLife = beginnerUnits.get(0).get(2);
        Seeded orderingFood = beginnerUnits.get(1).get(0);
        Seeded findingPlaces = beginnerUnits.get(1).get(1);
        replaceQuestions(greetings, DemoClassContent.GREETINGS_QUIZ);
        event(beginners, greetings.lesson(), admin, ClassContentEventType.LESSON_APPROVED, null, 53);
        int daysAgo = 47;
        for (Seeded seeded : List.of(universityLife, orderingFood, findingPlaces, numbers)) {
            event(beginners, seeded.lesson(), liWei.getUser(), ClassContentEventType.LESSON_SUBMITTED, null, daysAgo + 2);
            event(beginners, seeded.lesson(), admin, ClassContentEventType.LESSON_APPROVED, null, daysAgo);
            daysAgo -= 9;
        }
        reviseUniversityLife(beginners, universityLife, liWei, admin);
        reviseOrderingFood(beginners, orderingFood, liWei);

        CoursePackage hsk = createClass(liWei, "HSK 1 Prep",
                "Every HSK 1 word with a short video, a practice set and a mini test per unit.", null,
                beginner1, topic("Vocabulary"), LearningType.RECORDED, ContentStatus.PUBLISHED, 40);
        createUnits(hsk, liWei, DemoClassContent.HSK1_PREP, ContentStatus.PUBLISHED, 39);
        event(hsk, null, liWei.getUser(), ClassContentEventType.CLASS_CREATED, null, 40);
        event(hsk, null, admin, ClassContentEventType.CLASS_APPROVED, null, 36);

        CoursePackage culture = createClass(liWei, "Chinese Culture and Festivals",
                "Festivals, food and customs of the Chinese calendar, with the words you need to join in.", null,
                beginner2, topic("Culture"), LearningType.HYBRID, ContentStatus.SUBMITTED, 2);
        culture.setSubmittedAt(at(0, 10, 0));
        LearningModule festivalStories = createUnit(culture, liWei, "Unit 1: Festival Stories", 1);
        Seeded lantern = createLesson(culture, festivalStories, liWei, DemoClassContent.LANTERN_FESTIVAL, 1, ContentStatus.SUBMITTED, 2);
        lantern.lesson().setSubmittedAt(at(0, 10, 0));
        lantern.quiz().setLearningStage(LearningStage.INDEPENDENT);
        createLesson(culture, festivalStories, liWei, DemoClassContent.FESTIVAL_GREETINGS, 2, ContentStatus.DRAFT, 1);
        event(culture, null, liWei.getUser(), ClassContentEventType.CLASS_CREATED, null, 2);
        event(culture, null, liWei.getUser(), ClassContentEventType.CLASS_SUBMITTED, "Lantern Festival", 0);
        event(culture, lantern.lesson(), liWei.getUser(), ClassContentEventType.LESSON_SUBMITTED, null, 0);

        CoursePackage campus = createClass(liWei, "Campus Mandarin",
                "Read the signs and notices you meet around campus.", null,
                beginner1, topic("Characters"), LearningType.RECORDED, ContentStatus.DRAFT, 1);
        createLesson(campus, createUnit(campus, liWei, "Unit 1: Around Campus", 1), liWei, DemoClassContent.AT_THE_LIBRARY, 1,
                ContentStatus.DRAFT, 1);
        event(campus, null, liWei.getUser(), ClassContentEventType.CLASS_CREATED, null, 1);

        TeacherProfile wong = teacher(DemoPersonas.WONG_SIEW_LING);
        TeacherProfile john = teacher(DemoPersonas.JOHN_LIM);
        TeacherProfile tan = teacher(DemoPersonas.TAN_MEI_FONG);
        CoursePackage everyday = publishedClass(wong, "Everyday Conversations",
                "Real-life Mandarin for food stalls, campus and getting around, with live practice every week.",
                "Real-life Mandarin for food stalls, campus and getting around. Come and practise with me every Friday!",
                beginner2, "Listening", LearningType.LIVE, DemoClassContent.EVERYDAY_CONVERSATIONS, 30, admin);
        CoursePackage pinyin = publishedClass(john, "Pinyin: Read and Write",
                "Tones, pinyin spelling and your first characters, step by step with short videos.", null,
                beginner1, "Pinyin", LearningType.HYBRID, DemoClassContent.PINYIN, 34, admin);
        publishedClass(john, "Chinese Characters 101",
                "Strokes, radicals and your first characters, with tracing in every lesson.", null,
                beginner1, "Characters", LearningType.RECORDED, DemoClassContent.CHARACTERS, 26, admin);
        publishedClass(wong, "Travel Mandarin",
                "Airport, hotel and taxi phrases, practised live with the teacher every Sunday.", null,
                beginner2, "Speaking", LearningType.LIVE, DemoClassContent.TRAVEL, 22, admin);
        publishedClass(tan, "Mandarin at Work",
                "Greet customers, make small talk and handle simple calls and messages.", null,
                beginner2, "Speaking", LearningType.HYBRID, DemoClassContent.AT_WORK, 18, admin);
        publishedClass(tan, "Numbers, Money and Time",
                "Count, pay and tell the time with confidence, from 一 to 一百.", null,
                beginner1, "Numbers", LearningType.RECORDED, DemoClassContent.NUMBERS_MONEY_TIME, 14, admin);

        StudentProfile amira = student(DemoPersonas.STUDENT);
        StudentProfile chen = student(DemoPersonas.CHEN_HAO);
        StudentProfile zhang = student(DemoPersonas.ZHANG_YI);
        StudentProfile nur = student(DemoPersonas.NUR_AISYAH);
        join(amira, beginners, 28);
        join(chen, beginners, 30);
        join(zhang, beginners, 21);
        join(nur, pinyin, 12);
        join(nur, everyday, 10);

        // Amira: Numbers mastered, Ordering Food completed (best 60 %), Greetings in progress (2 of 3 activities, best 80 %).
        complete(amira, numbers, 7);
        watched(amira, numbers, 100, 7);
        attempt(amira, numbers, 6, 18, 0, new boolean[] { true, true, true, true, true }, false);
        complete(amira, orderingFood, 4);
        watched(amira, orderingFood, 100, 4);
        attempt(amira, orderingFood, 3, 20, 0, new boolean[] { true, false, true, false, false }, false);
        completeSome(amira, greetings, 1, greetings.flashcards(), greetings.tracing());
        watched(amira, greetings, 40, 1);
        attempt(amira, greetings, 2, 19, 30, new boolean[] { true, true, true, false, false }, true);
        QuizAttempt amiraLatest = attempt(amira, greetings, 0, 11, 45, new boolean[] { true, true, true, true, false }, true);

        // Chen Hao: three lessons done (60 %), quiz average about 70 %.
        complete(chen, greetings, 12);
        attempt(chen, greetings, 12, 21, 0, new boolean[] { true, true, true, true, false }, false);
        complete(chen, numbers, 10);
        attempt(chen, numbers, 10, 21, 0, new boolean[] { true, true, false, true, false }, false);
        complete(chen, orderingFood, 5);
        QuizAttempt chenFood = attempt(chen, orderingFood, 5, 20, 0, new boolean[] { true, false, true, true, false }, false);
        complete(chen, universityLife, 1);
        watched(chen, universityLife, 100, 1);
        attempt(chen, universityLife, 1, 22, 0, new boolean[] { true, true, true, true, false }, false);

        // Zhang Yi: one lesson done (20 %), weak quiz scores, no activity for 9 days: needs support.
        complete(zhang, greetings, 9);
        attempt(zhang, greetings, 10, 15, 0, new boolean[] { true, false, true, false, false }, true);
        attempt(zhang, greetings, 9, 16, 0, new boolean[] { true, false, true, false, true }, true);

        feedback(liWei, amira, beginners, greetings.lesson(), amiraLatest, "ENCOURAGING",
                "Great effort on greetings. Keep practising tone 3 and listen for short reply phrases.", 0, 12, 30);
        feedback(liWei, amira, beginners, numbers.lesson(), null, "LESSON",
                "Well done mastering numbers. Try counting objects around you in Mandarin.", 6, 18, 20);
        feedback(liWei, zhang, beginners, null, null, "WEAK_AREA",
                "Tone 3 is still tricky. Try a listening set in Practice, then retake the Greetings quiz.", 8, 9, 15);
        feedback(liWei, chen, beginners, orderingFood.lesson(), chenFood, "CORRECTIVE",
                "Good job on food words. Check the measure word 杯 in question 2 before your next attempt.", 10, 16, 40);
        feedback(liWei, chen, beginners, null, null, "PROGRESS",
                "Thanks for joining every live session this month. Your listening is getting faster.", 16, 11, 0);

        seedLiveSessions(liWei, beginners, greetings, numbers, universityLife, orderingFood, findingPlaces, amira, chen, zhang);
        seedWongSessions(wong, everyday, nur);
    }

    // ----- revisions -----

    /** A revision of University Life sent back with changes requested: students keep the published lesson. */
    private void reviseUniversityLife(CoursePackage coursePackage, Seeded seeded, TeacherProfile teacher, User admin) {
        Lesson revision = revisions.workingCopy(seeded.lesson());
        revision.setContentStatus(ContentStatus.REJECTED);
        revision.setRejectionReason("Add pinyin under each new word in the key phrases and check the new matching pair.");
        revision.setSubmittedAt(at(4, 16, 0));
        repos.lessons.save(revision);
        LessonKeyPhrase added = new LessonKeyPhrase();
        added.setLesson(revision);
        added.setChinese("教学楼");
        added.setPinyin("jiàoxuélóu");
        added.setMeaning("teaching building");
        added.setSortOrder(5);
        repos.keyPhrases.save(added);
        event(coursePackage, seeded.lesson(), teacher.getUser(), ClassContentEventType.REVISION_SUBMITTED, null, 4,
                "[{\"kind\":\"KEY_PHRASES_ADDED\",\"title\":null,\"count\":1,\"total\":5}]");
        event(coursePackage, seeded.lesson(), admin, ClassContentEventType.REVISION_CHANGES_REQUESTED,
                revision.getRejectionReason(), 3);
    }

    /** A revision of Ordering Food in review: two quiz questions added (now 7). */
    private void reviseOrderingFood(CoursePackage coursePackage, Seeded seeded, TeacherProfile teacher) {
        Lesson revision = revisions.workingCopy(seeded.lesson());
        revision.setContentStatus(ContentStatus.SUBMITTED);
        revision.setSubmittedAt(at(0, 11, 0));
        repos.lessons.save(revision);
        Quiz quiz = repos.quizzes.findByLessonIdOrderByCreatedAtAscIdAsc(revision.getId()).get(0);
        List<Question> extra = List.of(
                new Question("MULTIPLE_CHOICE", "How do you ask for a cup of tea?",
                        List.of(new DemoClassContent.Option("一杯茶", true), new DemoClassContent.Option("一碗饭", false),
                                new DemoClassContent.Option("多少钱", false), new DemoClassContent.Option("好吃", false)),
                        null, "杯 is the measure word for cups: 一杯茶 is “a cup of tea”.", "Look for 杯, “cup”."),
                new Question("TRUE_FALSE", "好吃 means “delicious”.",
                        List.of(new DemoClassContent.Option("True", true), new DemoClassContent.Option("False", false)),
                        null, "好吃 (hǎochī) is “delicious”.", null));
        int order = 6;
        for (Question question : extra) {
            saveQuestion(quiz, question, order++);
        }
        quiz.setTotalPoints(14);
        repos.quizzes.save(quiz);
        event(coursePackage, seeded.lesson(), teacher.getUser(), ClassContentEventType.REVISION_SUBMITTED, null, 0,
                "[{\"kind\":\"QUESTIONS_ADDED\",\"title\":\"" + quiz.getTitle() + "\",\"count\":2,\"total\":7}]");
    }

    // ----- classes, units and lessons -----

    private CoursePackage publishedClass(TeacherProfile teacher, String title, String description, String note,
            LearningLevel level, String topic, LearningType type, List<UnitSpec> units, int daysAgo, User admin) {
        CoursePackage coursePackage = createClass(teacher, title, description, note, level, topic(topic), type, ContentStatus.PUBLISHED, daysAgo);
        createUnits(coursePackage, teacher, units, ContentStatus.PUBLISHED, daysAgo - 1);
        event(coursePackage, null, teacher.getUser(), ClassContentEventType.CLASS_CREATED, null, daysAgo);
        event(coursePackage, null, teacher.getUser(), ClassContentEventType.CLASS_SUBMITTED, null, daysAgo - 1);
        event(coursePackage, null, admin, ClassContentEventType.CLASS_APPROVED, null, daysAgo - 2);
        return coursePackage;
    }

    private CoursePackage createClass(TeacherProfile teacher, String title, String description, String note,
            LearningLevel level, LearningCategory topic, LearningType type, ContentStatus status, int daysAgo) {
        CoursePackage coursePackage = new CoursePackage();
        coursePackage.setTeacher(teacher);
        coursePackage.setTitle(title);
        coursePackage.setDescription(description);
        coursePackage.setTeacherNote(note);
        coursePackage.setLevel(level);
        coursePackage.setCategory(topic);
        coursePackage.setLearningType(type);
        coursePackage.setContentStatus(status);
        coursePackage.setCreatedAt(at(daysAgo, 9, 0));
        coursePackage.setUpdatedAt(at(Math.max(0, daysAgo - 3), 9, 0));
        return repos.classes.save(coursePackage);
    }

    private List<List<Seeded>> createUnits(CoursePackage coursePackage, TeacherProfile teacher, List<UnitSpec> specs,
            ContentStatus status, int daysAgo) {
        List<List<Seeded>> result = new ArrayList<>();
        int unitPosition = 1;
        for (UnitSpec spec : specs) {
            LearningModule unit = createUnit(coursePackage, teacher, spec.title(), unitPosition++);
            List<Seeded> lessons = new ArrayList<>();
            int lessonPosition = 1;
            for (LessonSpec lessonSpec : spec.lessons()) {
                lessons.add(createLesson(coursePackage, unit, teacher, lessonSpec, lessonPosition++, status, daysAgo));
            }
            result.add(lessons);
        }
        return result;
    }

    private LearningModule createUnit(CoursePackage coursePackage, TeacherProfile teacher, String title, int position) {
        LearningModule unit = new LearningModule();
        unit.setTeacher(teacher);
        unit.setTitle(title);
        unit.setLevel(coursePackage.getLevel());
        unit.setCategory(coursePackage.getCategory());
        unit.setContentStatus(ContentStatus.PUBLISHED);
        unit = repos.units.save(unit);
        PackageItem item = new PackageItem();
        item.setCoursePackage(coursePackage);
        item.setItemType(PackageItemType.MODULE);
        item.setItemId(unit.getId());
        item.setSortOrder(position);
        repos.packageItems.save(item);
        return unit;
    }

    private Seeded createLesson(CoursePackage coursePackage, LearningModule unit, TeacherProfile teacher, LessonSpec spec,
            int position, ContentStatus status, int daysAgo) {
        Lesson lesson = new Lesson();
        lesson.setTeacher(teacher);
        lesson.setModule(unit);
        lesson.setLevel(coursePackage.getLevel());
        lesson.setCategory(coursePackage.getCategory());
        lesson.setTitle(spec.title());
        lesson.setSummary(spec.summary());
        lesson.setEstimatedDurationMinutes(spec.minutes());
        lesson.setLearningType(LearningType.RECORDED);
        lesson.setUnitPosition(position);
        lesson.setContentStatus(status);
        lesson.setCreatedAt(at(daysAgo, 10, position));
        lesson.setUpdatedAt(at(Math.max(0, daysAgo - position), 10, 0));
        lesson = repos.lessons.save(lesson);
        int order = 1;
        for (Word word : spec.words()) {
            LessonKeyPhrase phrase = new LessonKeyPhrase();
            phrase.setLesson(lesson);
            phrase.setChinese(word.chinese());
            phrase.setPinyin(word.pinyin());
            phrase.setMeaning(word.meaning());
            phrase.setSortOrder(order++);
            repos.keyPhrases.save(phrase);
        }
        if (spec.trace().isEmpty()) {
            return new Seeded(coursePackage, lesson, null, null, null, null, List.of(), List.of(), null);
        }
        ContentStatus partStatus = status == ContentStatus.PUBLISHED ? ContentStatus.PUBLISHED : ContentStatus.DRAFT;
        RecordedLessonVideo video = spec.video() == null ? null : repos.videos.save(newVideo(lesson, spec.video(), spec.videoSeconds()));
        Flashcard flashcards = flashcards(lesson, spec.title() + " Flashcards", partStatus, 1);
        TracingExercise tracing = new TracingExercise();
        tracing.setTitle("Trace " + String.join(" and ", spec.trace().stream().map(Word::chinese).toList()));
        tracing.setCreatedByUser(teacher.getUser());
        tracing.setLearningStage(LearningStage.GUIDED);
        tracing.setContentStatus(partStatus);
        tracing.setLesson(lesson);
        tracing.setLessonPosition(2);
        TracingCharacters.write(tracing, spec.trace().stream()
                .map(word -> new ClassRequests.TraceCharacter(word.chinese(), word.pinyin(), word.meaning()))
                .toList());
        repos.tracing.save(tracing);
        MatchingGame matching = new MatchingGame();
        matching.setTitle("Match Word and Meaning");
        matching.setCreatedByUser(teacher.getUser());
        matching.setLearningStage(LearningStage.SCAFFOLDED);
        matching.setContentStatus(partStatus);
        matching.setLesson(lesson);
        matching.setLessonPosition(3);
        matching = repos.matching.save(matching);
        int pair = 1;
        for (Word word : spec.words().subList(0, Math.min(4, spec.words().size()))) {
            matchingItem(matching, word.chinese(), word.pinyin(), word.meaning(), pair++);
        }
        Quiz quiz = newQuiz(lesson, spec.title(), partStatus);
        repos.quizzes.save(quiz);
        List<QuizQuestion> questions = new ArrayList<>();
        List<List<QuizOption>> options = new ArrayList<>();
        int questionOrder = 1;
        for (Question question : DemoClassContent.quiz(spec)) {
            QuizQuestion saved = saveQuestion(quiz, question, questionOrder++);
            questions.add(saved);
            options.add(repos.options.findForQuestions(List.of(saved)).getOrDefault(saved.getId(), List.of()));
        }
        return new Seeded(coursePackage, lesson, flashcards, tracing, matching, quiz, questions, options, video);
    }

    private void replaceQuestions(Seeded seeded, List<Question> questions) {
        for (int index = 0; index < questions.size(); index++) {
            QuizQuestion target = seeded.questions().get(index);
            Question question = questions.get(index);
            repos.options.deleteByQuestionId(target.getId());
            target.setQuestionType(question.type());
            target.setQuestionText(question.text());
            target.setExplanation(question.explanation());
            target.setHint(question.hint());
            target.setCorrectAnswerText(question.answer() != null ? question.answer()
                    : question.options().stream().filter(DemoClassContent.Option::correct).findFirst().orElseThrow().text());
            repos.questions.save(target);
            List<QuizOption> saved = new ArrayList<>();
            int order = 1;
            for (DemoClassContent.Option option : question.options()) {
                saved.add(saveOption(target, option.text(), option.correct(), order++));
            }
            seeded.options().set(index, saved);
        }
    }

    private QuizQuestion saveQuestion(Quiz quiz, Question question, int order) {
        QuizQuestion saved = new QuizQuestion();
        saved.setQuiz(quiz);
        saved.setQuestionType(question.type());
        saved.setQuestionText(question.text());
        saved.setExplanation(question.explanation());
        saved.setHint(question.hint());
        saved.setPoints(2);
        saved.setSortOrder(order);
        saved.setCorrectAnswerText(question.answer() != null ? question.answer()
                : question.options().stream().filter(DemoClassContent.Option::correct).findFirst().orElseThrow().text());
        saved = repos.questions.save(saved);
        int optionOrder = 1;
        for (DemoClassContent.Option option : question.options()) {
            saveOption(saved, option.text(), option.correct(), optionOrder++);
        }
        return saved;
    }

    private QuizOption saveOption(QuizQuestion question, String text, boolean correct, int order) {
        QuizOption option = new QuizOption();
        option.setQuestion(question);
        option.setOptionText(text);
        option.setCorrectOption(correct);
        option.setSortOrder(order);
        return repos.options.save(option);
    }

    private Quiz newQuiz(Lesson lesson, String title, ContentStatus status) {
        Quiz quiz = new Quiz();
        quiz.setLesson(lesson);
        quiz.setTeacher(lesson.getTeacher());
        quiz.setTitle(title);
        quiz.setLearningStage(LearningStage.GUIDED);
        quiz.setTimeLimitMinutes(10);
        quiz.setTotalPoints(10);
        quiz.setContentStatus(status);
        return quiz;
    }

    private Flashcard flashcards(Lesson lesson, String title, ContentStatus status, int position) {
        Flashcard card = new Flashcard();
        card.setTitle(title);
        card.setFrontText(title);
        card.setBackText("");
        card.setCreatedByUser(lesson.getTeacher().getUser());
        card.setLearningStage(LearningStage.GUIDED);
        card.setContentStatus(status);
        card.setLesson(lesson);
        card.setLessonPosition(position);
        return repos.flashcards.save(card);
    }

    private void matchingItem(MatchingGame game, String chinese, String pinyin, String meaning, int order) {
        MatchingGameItem item = new MatchingGameItem();
        item.setMatchingGame(game);
        item.setLeftText(chinese);
        item.setPinyin(pinyin);
        item.setRightText(meaning);
        item.setSortOrder(order);
        repos.matchingItems.save(item);
    }

    private RecordedLessonVideo newVideo(Lesson lesson, String title, int seconds) {
        RecordedLessonVideo video = new RecordedLessonVideo();
        video.setLesson(lesson);
        video.setTitle(title);
        video.setSourceType(RecordedVideoSourceType.EXTERNAL_URL);
        video.setExternalVideoUrl("https://www.youtube.com/results?search_query="
                + URLEncoder.encode("learn mandarin " + title, StandardCharsets.UTF_8));
        video.setDurationSeconds(seconds);
        video.setActive(true);
        return video;
    }

    // ----- students -----

    private void join(StudentProfile student, CoursePackage coursePackage, int daysAgo) {
        ClassEnrolment enrolment = new ClassEnrolment();
        enrolment.setStudent(student);
        enrolment.setCoursePackage(coursePackage);
        enrolment.setJoinedAt(at(daysAgo, 20, 0));
        repos.enrolments.save(enrolment);
    }

    private void complete(StudentProfile student, Seeded seeded, int daysAgo) {
        completeSome(student, seeded, daysAgo, seeded.flashcards(), seeded.tracing(), seeded.matching());
    }

    private void completeSome(StudentProfile student, Seeded seeded, int daysAgo, Object... activities) {
        int minute = 0;
        for (Object activity : activities) {
            ClassActivityCompletion completion = new ClassActivityCompletion();
            completion.setStudent(student);
            completion.setLesson(seeded.lesson());
            completion.setActivityType(activity instanceof Flashcard ? ClassActivityType.FLASHCARDS
                    : activity instanceof TracingExercise ? ClassActivityType.TRACING : ClassActivityType.MATCHING);
            completion.setActivityId(((LessonActivity) activity).getId());
            completion.setCompletedAt(at(daysAgo, 18, minute += 7));
            completion.setTimesCompleted(1);
            repos.completions.save(completion);
        }
    }

    private void watched(StudentProfile student, Seeded seeded, int percent, int daysAgo) {
        VideoProgress progress = new VideoProgress();
        progress.setStudent(student);
        progress.setLesson(seeded.lesson());
        progress.setRecordedVideo(seeded.video());
        progress.setProgressPercentage(percent);
        progress.setCompleted(percent >= 100);
        progress.setLastWatchedAt(at(daysAgo, 17, 50));
        repos.videoProgress.save(progress);
    }

    /** A submitted attempt with the given questions right; wrong option answers pick the first wrong option. */
    private QuizAttempt attempt(StudentProfile student, Seeded seeded, int daysAgo, int hour, int minute, boolean[] right,
            boolean hintOnFirst) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(seeded.quiz());
        attempt.setStudent(student);
        attempt.setStartedAt(at(daysAgo, hour, minute).minusMinutes(8));
        attempt.setCompletedAt(at(daysAgo, hour, minute));
        attempt.setExpiresAt(attempt.getStartedAt().plusMinutes(10));
        attempt.setSubmitted(true);
        int score = 0;
        int max = 0;
        attempt = repos.attempts.save(attempt);
        for (int index = 0; index < seeded.questions().size(); index++) {
            QuizQuestion question = seeded.questions().get(index);
            List<QuizOption> options = seeded.options().get(index);
            boolean correct = index < right.length && right[index];
            QuizAttemptAnswer answer = new QuizAttemptAnswer();
            answer.setQuizAttempt(attempt);
            answer.setQuestion(question);
            answer.setCorrect(correct);
            answer.setHintUsed(hintOnFirst && index == 0);
            if (options.isEmpty()) {
                answer.setAnswerText(correct ? question.getCorrectAnswerText() : "你们");
            } else {
                QuizOption chosen = options.stream().filter(option -> option.isCorrectOption() == correct).findFirst().orElseThrow();
                answer.setSelectedOption(chosen);
                answer.setAnswerText(chosen.getOptionText());
            }
            answer.setCreatedAt(attempt.getCompletedAt());
            repos.answers.save(answer);
            max += question.getPoints();
            score += correct ? question.getPoints() : 0;
        }
        attempt.setScore(score);
        attempt.setMaxScore(max);
        return repos.attempts.save(attempt);
    }

    private void feedback(TeacherProfile teacher, StudentProfile student, CoursePackage coursePackage, Lesson lesson,
            QuizAttempt attempt, String type, String message, int daysAgo, int hour, int minute) {
        TeacherFeedback feedback = new TeacherFeedback();
        feedback.setTeacher(teacher);
        feedback.setStudent(student);
        feedback.setCoursePackage(coursePackage);
        feedback.setLesson(lesson);
        feedback.setQuizAttempt(attempt);
        feedback.setFeedbackType(type);
        feedback.setFeedbackMessage(message);
        feedback.setCreatedAt(at(daysAgo, hour, minute));
        repos.feedback.save(feedback);
    }

    // ----- live sessions -----

    private void seedLiveSessions(TeacherProfile teacher, CoursePackage coursePackage, Seeded greetings, Seeded numbers,
            Seeded universityLife, Seeded orderingFood, Seeded findingPlaces, StudentProfile amira, StudentProfile chen,
            StudentProfile zhang) {
        // Today's session starts two hours from now (or tomorrow afternoon late in the evening).
        LocalDateTime todayStart = now.getHour() <= 20
                ? now.toLocalDate().atTime(now.getHour() + 2, 0)
                : now.toLocalDate().plusDays(1).atTime(16, 0);
        LiveClass today = session(teacher, coursePackage, greetings.lesson(), "Greetings 问候", todayStart, 45, 25,
                MeetingPlatform.ZOOM, "https://zoom.us/j/88123456789");
        book(today, amira, 2, false);
        book(today, chen, 2, false);
        book(today, zhang, 3, false);

        LiveClass university = session(teacher, coursePackage, universityLife.lesson(), "University Life",
                day(2, 17), 45, 20, MeetingPlatform.GOOGLE_MEET, "https://meet.google.com/xjp-uffr-kqt");
        book(university, chen, 1, false);
        book(university, zhang, 1, false);

        LiveClass cancelled = session(teacher, coursePackage, findingPlaces.lesson(), "Finding Places 问路",
                day(3, 18), 30, 20, MeetingPlatform.ZOOM, "https://zoom.us/j/88987654321");
        cancelled.setLiveClassStatus(LiveClassStatus.CANCELLED);
        cancelled.setCancelledAt(at(1, 9, 30));
        repos.sessions.save(cancelled);

        LiveClass full = session(teacher, coursePackage, orderingFood.lesson(), "Ordering Food 点菜",
                day(4, 19), 45, 2, MeetingPlatform.MICROSOFT_TEAMS, "https://teams.microsoft.com/l/meetup-join/ordering-food");
        book(full, chen, 2, false);
        book(full, zhang, 2, false);

        LiveClass tones = session(teacher, coursePackage, greetings.lesson(), "Tones Clinic", day(7, 15), 30, 30,
                MeetingPlatform.ZOOM, "https://zoom.us/j/88555123456");
        tones.setRescheduledFromStartAt(day(7, 14));
        tones.setLiveClassStatus(LiveClassStatus.RESCHEDULED);
        repos.sessions.save(tones);
        book(tones, chen, 1, false);

        LiveClass pastNumbers = session(teacher, coursePackage, numbers.lesson(), "Numbers 数字", day(-7, 16), 45, 25,
                MeetingPlatform.ZOOM, "https://zoom.us/j/88001122334");
        pastNumbers.setRecordingUrl("https://zoom.us/rec/share/numbers-practice");
        repos.sessions.save(pastNumbers);
        book(pastNumbers, amira, 9, true);
        book(pastNumbers, chen, 9, true);
        book(pastNumbers, zhang, 9, false);

        LiveClass pastCampus = session(teacher, coursePackage, universityLife.lesson(), "Campus Words 校园", day(-11, 17), 45, 20,
                MeetingPlatform.GOOGLE_MEET, "https://meet.google.com/abc-defg-hij");
        pastCampus.setRecordingUrl("https://drive.google.com/file/d/campus-words-recording/view");
        repos.sessions.save(pastCampus);
        book(pastCampus, amira, 13, true);

        LiveClass pastGreetings = session(teacher, coursePackage, greetings.lesson(), "Greetings 问候", day(-14, 16), 45, 25,
                MeetingPlatform.ZOOM, "https://zoom.us/j/88777666555");
        pastGreetings.setRecordingUrl("https://zoom.us/rec/share/greetings-practice");
        repos.sessions.save(pastGreetings);
        book(pastGreetings, amira, 16, true);
        book(pastGreetings, chen, 16, true);
    }

    private void seedWongSessions(TeacherProfile teacher, CoursePackage coursePackage, StudentProfile nur) {
        LocalDate date = now.toLocalDate();
        int toFriday = (5 - date.getDayOfWeek().getValue() + 7) % 7;
        for (int week = 0; week < 3; week++) {
            LocalDateTime start = date.plusDays(toFriday + 7L * week).atTime(19, 0);
            if (!start.isAfter(now)) {
                start = start.plusWeeks(3);
            }
            LiveClass session = session(teacher, coursePackage, null, "Small talk practice", start, 45, 15,
                    MeetingPlatform.GOOGLE_MEET, "https://meet.google.com/smt-alkp-wsl");
            if (week == 0) {
                book(session, nur, 1, false);
            }
        }
    }

    private LiveClass session(TeacherProfile teacher, CoursePackage coursePackage, Lesson lesson, String title,
            LocalDateTime start, int minutes, Integer capacity, MeetingPlatform platform, String link) {
        LiveClass session = new LiveClass();
        session.setTeacher(teacher);
        session.setCoursePackage(coursePackage);
        session.setLesson(lesson);
        session.setTitle(title);
        session.setScheduledStartAt(start);
        session.setScheduledEndAt(start.plusMinutes(minutes));
        session.setTimeZone(platformProperties.defaultTimeZone());
        session.setMaxParticipants(capacity);
        session.setMeetingPlatform(platform);
        session.setMeetingLink(link);
        session.setLiveClassStatus(LiveClassStatus.SCHEDULED);
        return repos.sessions.save(session);
    }

    private void book(LiveClass session, StudentProfile student, int daysBefore, boolean attended) {
        LiveClassEnrolment booking = new LiveClassEnrolment();
        booking.setLiveClass(session);
        booking.setStudent(student);
        booking.setEnrolledAt(session.getScheduledStartAt().minusDays(daysBefore).isAfter(now)
                ? now.minusHours(1)
                : session.getScheduledStartAt().minusDays(daysBefore));
        booking.setEnrolmentStatus(attended ? LiveClassEnrolmentStatus.ATTENDED : LiveClassEnrolmentStatus.ENROLLED);
        if (attended) {
            booking.setAttendedAt(session.getScheduledStartAt().minusMinutes(5));
        }
        repos.bookings.save(booking);
    }

    // ----- history -----

    private void event(CoursePackage coursePackage, Lesson lesson, User actor, ClassContentEventType type, String note, int daysAgo) {
        event(coursePackage, lesson, actor, type, note, daysAgo, null);
    }

    private void event(CoursePackage coursePackage, Lesson lesson, User actor, ClassContentEventType type, String note,
            int daysAgo, String changes) {
        ClassContentEvent event = new ClassContentEvent();
        event.setCoursePackage(coursePackage);
        event.setLesson(lesson);
        event.setActor(actor);
        event.setEventType(type);
        event.setSubjectTitle(lesson != null ? lesson.getTitle() : coursePackage.getTitle());
        event.setNote(note);
        event.setChanges(changes);
        event.setCreatedAt(at(daysAgo, 10, 0));
        repos.events.save(event);
    }

    // ----- lookups and dates -----

    /** A wall-clock time {@code daysAgo} days back; one later today than now moves to the day before. */
    private LocalDateTime at(int daysAgo, int hour, int minute) {
        LocalDateTime time = now.toLocalDate().minusDays(daysAgo).atTime(LocalTime.of(hour, minute));
        return time.isAfter(now) ? time.minusDays(1) : time;
    }

    /** A wall-clock time {@code days} from today (negative: in the past). */
    private LocalDateTime day(int days, int hour) {
        return now.toLocalDate().plusDays(days).atTime(hour, 0);
    }

    private TeacherProfile teacher(DemoPersonas.Persona persona) {
        User user = repos.users.findByEmailWithRoles(persona.email()).orElseThrow();
        return repos.teachers.findDetailedByUserId(user.getId()).orElseThrow();
    }

    private StudentProfile student(DemoPersonas.Persona persona) {
        User user = repos.users.findByEmailWithRoles(persona.email()).orElseThrow();
        return repos.students.findByUserId(user.getId()).orElseThrow();
    }

    private LearningLevel level(String name) {
        return repos.levels.findByName(name).orElseThrow(() -> new IllegalStateException(name + " level must be seeded first."));
    }

    private LearningCategory topic(String name) {
        return repos.topics.findByName(name).orElseThrow(() -> new IllegalStateException(name + " topic must be seeded first."));
    }
}
