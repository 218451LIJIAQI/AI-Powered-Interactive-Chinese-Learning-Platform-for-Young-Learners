package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.config.seed.DemoClassesSeeder;
import com.example.aichinese.config.seed.DemoContentSeeder;
import com.example.aichinese.config.seed.DemoPersonas;
import com.example.aichinese.dto.classes.AdminClassViews;
import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.entity.ClassEnrolment;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.ClassActivityCompletionRepository;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.RoleRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.classes.AdminClassList;
import com.example.aichinese.service.classes.ClassAuthoringService;
import com.example.aichinese.service.classes.ClassCatalogueService;
import com.example.aichinese.service.classes.LessonEditorService;
import com.example.aichinese.service.classes.StudentClassService;
import com.example.aichinese.support.DemoProfileTest;

@DemoProfileTest
class DemoSeederTests {

    @Autowired
    private DemoContentSeeder demoContentSeeder;

    @Autowired
    private DemoClassesSeeder demoClassesSeeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private PackageItemRepository packageItemRepository;

    @Autowired
    private CoursePackageRepository coursePackageRepository;

    @Autowired
    private ClassEnrolmentRepository enrolmentRepository;

    @Autowired
    private QuizAttemptRepository attemptRepository;

    @Autowired
    private ClassActivityCompletionRepository completionRepository;

    @Autowired
    private LessonProgressRepository progressRepository;

    @Autowired
    private ClassCatalogueService catalogue;

    @Autowired
    private ClassAuthoringService classAuthoring;

    @Autowired
    private AdminClassList classList;

    @Autowired
    private LessonEditorService lessonEditor;

    @Autowired
    private StudentClassService studentClasses;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Test
    void seedsThePrototypeClassesOnceWithEightInTheCatalogue() {
        assertThat(userRepository.existsByEmailIgnoreCase(DemoPersonas.STUDENT.email())).isTrue();
        assertThat(catalogue.search(null, 0, 50).totalItems()).isEqualTo(8);
        var packages = coursePackageRepository.findAll();
        assertThat(packages).filteredOn(pkg -> pkg.getContentStatus() == ContentStatus.DRAFT)
                .extracting("title").containsExactlyInAnyOrder("Chinese Culture and Festivals", "Study Group Mandarin");
        assertThat(packages).filteredOn(pkg -> pkg.getContentStatus() == ContentStatus.ARCHIVED)
                .extracting("title").containsExactly("Mandarin Foundations");
        long lessons = lessonRepository.count();
        long classes = coursePackageRepository.count();
        long enrolments = enrolmentRepository.count();

        demoContentSeeder.run();
        demoClassesSeeder.run();

        assertThat(lessonRepository.count()).isEqualTo(lessons);
        assertThat(coursePackageRepository.count()).isEqualTo(classes);
        assertThat(enrolmentRepository.count()).isEqualTo(enrolments);
    }

    /** Seeded roles and learner accounts match the five demo personas. */
    @Test
    void seedsOnlyLearnerAndAdminRolesWithTheFiveDemoLearners() {
        assertThat(roleRepository.findAll()).extracting(Role::getName)
                .containsExactlyInAnyOrder(RoleName.STUDENT, RoleName.ADMIN);
        assertThat(userRepository.findAllByRoleName(RoleName.STUDENT)).extracting(User::getEmail)
                .containsExactlyInAnyOrder(DemoPersonas.STUDENT.email(), DemoPersonas.CHEN_HAO.email(),
                        DemoPersonas.ZHANG_YI.email(), DemoPersonas.NUR_AISYAH.email(), DemoPersonas.ZHANG_YUXI.email());
    }

    @Test
    void theCatalogueHasTheThemesAndBestAfterOfThePrototype() {
        Map<String, ClassViews.ClassCard> cards = catalogue.search(null, 0, 50).items().stream()
                .collect(Collectors.toMap(ClassViews.ClassCard::title, Function.identity()));
        assertCard(cards, "Mandarin for Campus Life", "Study or exchange", 3, 5);
        assertCard(cards, "Chinese Through Film", "Personal interest", 5, 6);
        assertCard(cards, "Everyday Conversations", "Daily life", 3, 4);
        assertCard(cards, "Travel Mandarin", "Travel", 3, 5);
        assertCard(cards, "Shopping and Prices", "Travel", 4, 5);
        assertCard(cards, "Study Abroad Survival", "Study or exchange", 3, 5);
        assertCard(cards, "Basic Work Mandarin", "Work and career", 3, 5);
        assertCard(cards, "Serving Customers", "Work and career", 4, 4);
    }

    /** Every published lesson is complete (video, key phrases, 3 activities, a quiz of exactly 5 questions). */
    @Test
    void theAdminsClassesAreInTheStatesOfThePrototype() {
        Map<String, AdminClassViews.ContentRow> classes = classList.list(null, "CLASS", null, 0, 50).items().stream()
                .collect(Collectors.toMap(AdminClassViews.ContentRow::title, Function.identity()));
        assertThat(classes).hasSize(11);

        AdminClassViews.ClassPage campusLife = classAuthoring.classPage(classes.get("Mandarin for Campus Life").id());
        assertThat(campusLife.status()).isEqualTo("PUBLISHED");
        assertThat(campusLife.category().name()).isEqualTo("Study or exchange");
        assertThat(campusLife.lessonCount()).isEqualTo(5);
        assertThat(campusLife.units()).extracting(AdminClassViews.Unit::title)
                .containsExactly("First Conversations", "Campus Life");
        List<AdminClassViews.LessonRow> lessons = campusLife.units().stream().flatMap(unit -> unit.lessons().stream()).toList();
        assertThat(lessons).extracting(AdminClassViews.LessonRow::title).containsExactly(
                "Meeting Classmates", "Room Numbers", "University Life", "Ordering Food at the Canteen", "Finding Places");
        assertThat(lessons).allSatisfy(lesson -> {
            assertThat(lesson.status()).isEqualTo("PUBLISHED");
            assertThat(lesson.hasUnpublishedChanges()).isFalse();
            AdminClassViews.LessonEditor editor = lessonEditor.editor(lesson.id());
            assertThat(editor.checklist().missing()).as(lesson.title()).isEmpty();
            assertThat(editor.quizzes().stream().mapToInt(quiz -> quiz.questions().size()).sum())
                    .as(lesson.title()).isEqualTo(5);
            assertThat(editor.quizzes()).allSatisfy(quiz -> assertThat(quiz.hintsAllowed()).isTrue());
        });

        AdminClassViews.ClassPage foundations = classAuthoring.classPage(classes.get("Mandarin Foundations").id());
        assertThat(foundations.status()).isEqualTo("ARCHIVED");
        assertThat(foundations.units()).extracting(AdminClassViews.Unit::title)
                .containsExactly("Sounds and Tones", "First Words");
        assertThat(foundations.units().stream().flatMap(unit -> unit.lessons().stream()))
                .extracting(AdminClassViews.LessonRow::title)
                .containsExactly("Tones in a Sentence", "Greetings", "Numbers 1–10", "Family Words", "Food and Drink");

        AdminClassViews.ClassPage culture = classAuthoring.classPage(classes.get("Chinese Culture and Festivals").id());
        assertThat(culture.status()).isEqualTo("DRAFT");
        assertThat(culture.lessonCount()).isEqualTo(2);
        assertThat(culture.canPublish()).isFalse();
        assertThat(culture.bestAfterUnit()).isEqualTo(3);
        AdminClassViews.ClassPage studyGroup = classAuthoring.classPage(classes.get("Study Group Mandarin").id());
        assertThat(studyGroup.status()).isEqualTo("DRAFT");
        assertThat(studyGroup.lessonCount()).isEqualTo(1);
    }

    @Test
    void amiraChenHaoAndZhangYiHaveTheProgressOfThePrototype() {
        SecurityUserDetails amira = signedIn(DemoPersonas.STUDENT);
        ClassViews.MyClasses myClasses = studentClasses.myClasses(amira);
        assertThat(myClasses.classes()).extracting(card -> card.summary().title())
                .containsExactly("Mandarin for Campus Life", "Mandarin Foundations");
        assertThat(myClasses.moreClassCount()).isEqualTo(7);

        ClassViews.MyClassCard campusLife = myClasses.classes().get(0);
        assertThat(campusLife.archived()).isFalse();
        assertThat(campusLife.archivedAt()).isNull();
        assertThat(campusLife.completedLessons()).isEqualTo(2);
        assertThat(campusLife.progressPercent()).isEqualTo(40);
        assertThat(campusLife.nextStep().lessonTitle()).isEqualTo("Meeting Classmates");
        assertThat(campusLife.nextStep().stepsDone()).isEqualTo(3);
        assertThat(campusLife.nextStep().stepsTotal()).isEqualTo(4);
        List<ClassViews.LessonProgressRow> lessons = studentClasses
                .classPage(amira, campusLife.summary().id())
                .units().stream().flatMap(unit -> unit.lessons().stream()).toList();
        assertThat(lessons).extracting(ClassViews.LessonProgressRow::title, ClassViews.LessonProgressRow::status)
                .containsExactly(
                        tuple("Meeting Classmates", "IN_PROGRESS"),
                        tuple("Room Numbers", "COMPLETED"),
                        tuple("University Life", "NOT_STARTED"),
                        tuple("Ordering Food at the Canteen", "COMPLETED"),
                        tuple("Finding Places", "NOT_STARTED"));

        // Finished before an admin archived it: read-only, every lesson and result still there.
        ClassViews.MyClassCard foundations = myClasses.classes().get(1);
        assertThat(foundations.archived()).isTrue();
        assertThat(foundations.archivedAt()).isNotNull();
        assertThat(foundations.completedLessons()).isEqualTo(5);
        assertThat(foundations.progressPercent()).isEqualTo(100);
        assertThat(foundations.lessons()).filteredOn(row -> row.title().equals("Greetings"))
                .extracting(ClassViews.LessonProgressRow::status).containsExactly("COMPLETED");

        assertThat(studentClasses.myClasses(signedIn(DemoPersonas.CHEN_HAO)).classes().get(0).completedLessons())
                .isEqualTo(3);
        assertThat(studentClasses.myClasses(signedIn(DemoPersonas.ZHANG_YI)).classes().get(0).completedLessons())
                .isEqualTo(1);
    }

    /** No seeded history comes before "Account created": a class joined, the streak calendar. */
    @Test
    void theDemoAccountsAreOlderThanTheirSeededHistory() {
        List<ClassEnrolment> enrolments = enrolmentRepository.findAll();
        for (DemoPersonas.Persona persona : List.of(DemoPersonas.STUDENT, DemoPersonas.CHEN_HAO,
                DemoPersonas.ZHANG_YI, DemoPersonas.NUR_AISYAH)) {
            User student = userRepository.findByEmailWithRoles(persona.email()).orElseThrow();
            StudentProfile profile = studentProfileRepository.findByUserId(student.getId()).orElseThrow();
            assertThat(profile.getCreatedAt()).as(persona.email()).isEqualTo(student.getCreatedAt());
            assertThat(enrolments).as(persona.email())
                    .filteredOn(enrolment -> profile.getId().equals(enrolment.getStudent().getId()))
                    .isNotEmpty()
                    .allSatisfy(enrolment -> assertThat(enrolment.getJoinedAt()).isAfter(student.getCreatedAt()));
        }
    }

    /**
     * Learners have submitted attempts on the Ordering Food quiz, so the editor locks its questions; no seeded lesson has
     * unpublished changes.
     */
    @Test
    void aQuizWithSubmittedAttemptsIsLockedInTheEditor() {
        List<Lesson> lessons = lessonRepository.findAll();
        assertThat(lessons).allSatisfy(lesson -> assertThat(lesson.getRevisionOf()).isNull());
        Lesson orderingFood = lessons.stream()
                .filter(lesson -> lesson.getTitle().equals("Ordering Food at the Canteen"))
                .findFirst()
                .orElseThrow();

        AdminClassViews.LessonEditor editor = lessonEditor.editor(orderingFood.getId());

        assertThat(editor.hasUnpublishedChanges()).isFalse();
        assertThat(editor.quizzes()).singleElement().satisfies(quiz -> {
            assertThat(quiz.questionsLocked()).isTrue();
            assertThat(quiz.questions()).hasSize(5);
        });
    }

    /**
     * Nobody joins a draft class, and an archived class is read-only to the learners who joined it: all their work in
     * Mandarin Foundations is from before an admin archived it, and its last change is when it was archived.
     */
    @Test
    @Transactional
    void learnersWorkOnlyInClassesThatWereLive() {
        List<CoursePackage> packages = coursePackageRepository.findAll();
        List<CoursePackage> draftPackages = packages.stream()
                .filter(pkg -> pkg.getContentStatus() == ContentStatus.DRAFT)
                .toList();
        assertThat(draftPackages).isNotEmpty();
        for (CoursePackage draft : draftPackages) {
            assertThat(enrolmentRepository.existsByCoursePackageId(draft.getId())).as(draft.getTitle()).isFalse();
        }
        CoursePackage foundations = packages.stream()
                .filter(pkg -> pkg.getContentStatus() == ContentStatus.ARCHIVED)
                .findFirst()
                .orElseThrow();
        assertThat(foundations.getStatusBeforeArchive()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(foundations.getUpdatedAt()).isEqualTo(foundations.getArchivedAt());

        LocalDateTime archivedAt = foundations.getArchivedAt();
        List<Long> lessonsAttemptedSinceArchiving = attemptRepository.findAll().stream()
                .filter(attempt -> attempt.getQuiz().getLesson() != null)
                .filter(attempt -> attempt.getCompletedAt().isAfter(archivedAt))
                .map(attempt -> attempt.getQuiz().getLesson().getId())
                .toList();
        List<Long> unitIds = packageItemRepository.findUnitsInOrder(List.of(foundations.getId())).stream()
                .map(unit -> unit.unit().getId())
                .toList();
        List<Long> foundationLessons = lessonRepository
                .findByModuleIdInAndRevisionOfIsNullOrderByUnitPositionAscCreatedAtAscIdAsc(unitIds).stream()
                .map(Lesson::getId)
                .toList();
        assertThat(foundationLessons).hasSize(5);
        assertThat(lessonsAttemptedSinceArchiving).doesNotContainAnyElementsOf(foundationLessons);
        assertThat(completionRepository.findAll())
                .filteredOn(completion -> foundationLessons.contains(completion.getLesson().getId()))
                .hasSize(15)
                .allSatisfy(completion -> assertThat(completion.getCompletedAt()).isBefore(archivedAt));
    }

    /** Timestamps are server-local: what the learners did is in the past and falls within their lesson visits. */
    @Test
    @Transactional
    void seededLearnerWorkIsInThePastAndWithinLessonVisits() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(lessonRepository.findAll()).isNotEmpty()
                .allSatisfy(lesson -> assertThat(lesson.getUpdatedAt()).isAfterOrEqualTo(lesson.getCreatedAt()));
        assertThat(attemptRepository.findAll()).isNotEmpty()
                .allSatisfy(attempt -> {
                    assertThat(attempt.getCompletedAt()).isBeforeOrEqualTo(now);
                    assertThat(attempt.getCreatedAt()).isBeforeOrEqualTo(attempt.getCompletedAt());
                    assertThat(attempt.getUpdatedAt()).isEqualTo(attempt.getCompletedAt());
                });
        assertThat(completionRepository.findAll()).isNotEmpty().allSatisfy(activity -> {
            assertThat(activity.getCompletedAt()).isBeforeOrEqualTo(now);
            var progress = progressRepository.findByStudentIdAndLessonId(
                    activity.getStudent().getId(), activity.getLesson().getId()).orElseThrow();
            assertThat(activity.getCompletedAt()).as("%s in %s", activity.getActivityType(), activity.getLesson().getTitle())
                    .isBetween(progress.getCreatedAt(), progress.getLastAccessedAt());
        });
    }

    private static void assertCard(Map<String, ClassViews.ClassCard> cards, String title, String theme, int bestAfter,
            int lessons) {
        ClassViews.ClassCard card = cards.get(title);
        assertThat(card).as(title).isNotNull();
        assertThat(card.topicName()).as(title).isEqualTo(theme);
        assertThat(card.bestAfterUnit()).as(title).isEqualTo(bestAfter);
        assertThat(card.lessonCount()).as(title).isEqualTo(lessons);
    }

    private SecurityUserDetails signedIn(DemoPersonas.Persona persona) {
        return new SecurityUserDetails(userRepository.findByEmailWithRoles(persona.email()).orElseThrow());
    }
}
