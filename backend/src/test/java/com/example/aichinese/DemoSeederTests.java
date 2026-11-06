package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.aichinese.config.seed.DemoClassesSeeder;
import com.example.aichinese.config.seed.DemoContentSeeder;
import com.example.aichinese.config.seed.DemoPersonas;
import com.example.aichinese.dto.classes.ClassStudentViews;
import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.dto.classes.LiveSessionViews;
import com.example.aichinese.dto.classes.TeacherClassViews;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.classes.ClassCatalogueService;
import com.example.aichinese.service.classes.ClassLiveSessionService;
import com.example.aichinese.service.classes.ClassReviewService;
import com.example.aichinese.service.classes.StudentClassService;
import com.example.aichinese.service.classes.TeacherClassService;
import com.example.aichinese.service.classes.TeacherClassStudentsService;

@SpringBootTest
@ActiveProfiles("demo")
class DemoSeederTests {

    @Autowired
    private DemoContentSeeder demoContentSeeder;

    @Autowired
    private DemoClassesSeeder demoClassesSeeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private CoursePackageRepository coursePackageRepository;

    @Autowired
    private ClassEnrolmentRepository enrolmentRepository;

    @Autowired
    private ClassCatalogueService catalogue;

    @Autowired
    private ClassReviewService reviews;

    @Autowired
    private StudentClassService studentClasses;

    @Autowired
    private TeacherClassService teacherClasses;

    @Autowired
    private TeacherClassStudentsService teacherStudents;

    @Autowired
    private ClassLiveSessionService liveSessions;

    @Test
    void seedsThePrototypeClassesOnceWithEightInTheCatalogue() throws Exception {
        assertThat(userRepository.existsByEmailIgnoreCase("teacher@fyp.local")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("student@fyp.local")).isTrue();
        assertThat(catalogue.search(new ClassCatalogueService.Query(null, null, null, null, null), Set.of(), 0, 50)
                .totalItems()).isEqualTo(8);
        assertThat(coursePackageRepository.findByContentStatus(ContentStatus.SUBMITTED))
                .extracting("title").containsExactly("Chinese Culture and Festivals");
        // The new class with its lesson, and one lesson revision, wait for review; the other revision has changes requested.
        assertThat(reviews.waitingCount()).isEqualTo(2);
        long lessons = lessonRepository.count();
        long classes = coursePackageRepository.count();
        long enrolments = enrolmentRepository.count();

        demoContentSeeder.run();
        demoClassesSeeder.run();

        assertThat(lessonRepository.count()).isEqualTo(lessons);
        assertThat(coursePackageRepository.count()).isEqualTo(classes);
        assertThat(enrolmentRepository.count()).isEqualTo(enrolments);
    }

    @Test
    void theCatalogueHasTheThemesTeachersAndBestAfterOfThePrototype() {
        Map<String, ClassViews.ClassCard> cards = catalogue.search(
                        new ClassCatalogueService.Query(null, null, null, null, null), Set.of(), 0, 50)
                .items().stream()
                .collect(Collectors.toMap(ClassViews.ClassCard::title, Function.identity()));
        assertCard(cards, "Mandarin for Campus Life", "Li Wei", "Study or exchange", 3);
        assertCard(cards, "Chinese Through Film", "Li Wei", "Personal interest", 5);
        assertCard(cards, "Everyday Conversations", "Wong Siew Ling", "Daily life", 3);
        assertCard(cards, "Travel Mandarin", "Wong Siew Ling", "Travel", 3);
        assertCard(cards, "Shopping and Prices", "John Lim", "Travel", 4);
        assertCard(cards, "Study Abroad Survival", "John Lim", "Study or exchange", 3);
        assertCard(cards, "Basic Work Mandarin", "Tan Mei Fong", "Work and career", 3);
        assertCard(cards, "Serving Customers", "Tan Mei Fong", "Work and career", 4);
        assertThat(cards.get("Mandarin for Campus Life").lessonCount()).isEqualTo(5);
        assertThat(cards.get("Chinese Through Film").lessonCount()).isEqualTo(6);
        assertThat(cards.get("Everyday Conversations").lessonCount()).isEqualTo(4);
        assertThat(cards.values()).allSatisfy(card -> {
            assertThat(card.unitCount()).isEqualTo(2);
            assertThat(card.lessonCount()).isBetween(4, 6);
        });
    }

    @Test
    void liWeisClassesAreInTheStatesOfThePrototype() {
        SecurityUserDetails liWei = signedIn(DemoPersonas.TEACHER);
        Map<String, TeacherClassViews.ClassListItem> classes = teacherClasses.listClasses(liWei).stream()
                .collect(Collectors.toMap(TeacherClassViews.ClassListItem::title, Function.identity()));
        assertThat(classes.keySet()).containsExactlyInAnyOrder("Mandarin for Campus Life", "Chinese Through Film",
                "Chinese Culture and Festivals", "Study Group Mandarin");
        assertThat(classes.get("Chinese Culture and Festivals").status()).isEqualTo("SUBMITTED");
        assertThat(classes.get("Chinese Culture and Festivals").topicName()).isEqualTo("Personal interest");
        assertThat(classes.get("Chinese Culture and Festivals").bestAfterUnit()).isEqualTo(3);
        assertThat(classes.get("Study Group Mandarin").status()).isEqualTo("DRAFT");
        assertThat(classes.get("Study Group Mandarin").bestAfterUnit()).isEqualTo(3);

        TeacherClassViews.ClassPage campusLife = teacherClasses.getClass(liWei, classes.get("Mandarin for Campus Life").id());
        assertThat(campusLife.units()).extracting(TeacherClassViews.Unit::title)
                .containsExactly("First Conversations", "Campus Life");
        List<TeacherClassViews.LessonRow> lessons = campusLife.units().stream().flatMap(unit -> unit.lessons().stream()).toList();
        assertThat(lessons).extracting(TeacherClassViews.LessonRow::title).containsExactly(
                "Meeting Classmates", "Room Numbers", "University Life", "Ordering Food at the Canteen", "Finding Places");
        assertThat(lessons).allSatisfy(lesson -> {
            assertThat(lesson.activityCount()).isEqualTo(3);
            assertThat(lesson.questionCount()).isEqualTo(5);
        });
        TeacherClassViews.LessonRow universityLife = lessons.get(2);
        assertThat(universityLife.revisionStatus()).isEqualTo("REJECTED");
        assertThat(universityLife.reviewNote())
                .isEqualTo("Add pinyin to the Campus Places matching cards and under each new word in the key phrases.");
        assertThat(lessons.get(3).revisionStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void amiraChenHaoAndZhangYiHaveTheProgressOfThePrototype() {
        SecurityUserDetails liWei = signedIn(DemoPersonas.TEACHER);
        Map<String, ClassStudentViews.RosterRow> roster = teacherStudents.roster(liWei, null, null).students().stream()
                .collect(Collectors.toMap(ClassStudentViews.RosterRow::name, Function.identity()));
        assertThat(roster.get("Amira Hassan").progressPercent()).isEqualTo(40);
        assertThat(roster.get("Amira Hassan").quizAverage()).isEqualTo(75);
        assertThat(roster.get("Chen Hao").progressPercent()).isEqualTo(60);
        assertThat(roster.get("Chen Hao").quizAverage()).isEqualTo(70);
        assertThat(roster.get("Zhang Yi").progressPercent()).isEqualTo(20);
        assertThat(roster.get("Zhang Yi").quizAverage()).isEqualTo(50);
        assertThat(roster.get("Zhang Yi").needsSupport()).isTrue();

        ClassViews.MyClasses myClasses = studentClasses.myClasses(signedIn(DemoPersonas.STUDENT));
        ClassViews.MyClassCard campusLife = myClasses.classes().get(0);
        assertThat(campusLife.summary().title()).isEqualTo("Mandarin for Campus Life");
        assertThat(campusLife.completedLessons()).isEqualTo(2);
        assertThat(campusLife.progressPercent()).isEqualTo(40);
        assertThat(campusLife.nextStep().lessonTitle()).isEqualTo("Meeting Classmates");
        assertThat(campusLife.nextStep().activitiesDone()).isEqualTo(2);
        List<ClassViews.LessonProgressRow> lessons = studentClasses
                .classPage(signedIn(DemoPersonas.STUDENT), campusLife.summary().id())
                .units().stream().flatMap(unit -> unit.lessons().stream()).toList();
        assertThat(lessons).extracting(ClassViews.LessonProgressRow::title, ClassViews.LessonProgressRow::percent,
                        ClassViews.LessonProgressRow::bestQuizPercent)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Meeting Classmates", 75, 80),
                        org.assertj.core.groups.Tuple.tuple("Room Numbers", 100, 100),
                        org.assertj.core.groups.Tuple.tuple("University Life", 0, null),
                        org.assertj.core.groups.Tuple.tuple("Ordering Food at the Canteen", 100, 60),
                        org.assertj.core.groups.Tuple.tuple("Finding Places", 0, null));
    }

    @Test
    void theLiveSessionsOfCampusLifeAreThoseOfThePrototype() {
        LiveSessionViews.TeacherSessions sessions = liveSessions.teacherSessions(signedIn(DemoPersonas.TEACHER));
        assertThat(sessions.upcoming()).extracting(LiveSessionViews.TeacherSession::title).containsExactlyInAnyOrder(
                "Meeting Classmates 认识同学", "University Life", "Finding Places 问路", "Ordering Food 点菜", "Campus Q&A");
        assertThat(sessions.past()).extracting(LiveSessionViews.TeacherSession::title).containsExactlyInAnyOrder(
                "Room Numbers 房号", "Campus Words 校园", "Greetings 问候");
        assertThat(sessions.upcoming()).filteredOn(session -> "CANCELLED".equals(session.status()))
                .extracting(LiveSessionViews.TeacherSession::title).containsExactly("Finding Places 问路");
        assertThat(sessions.past()).allSatisfy(session -> assertThat(session.recordingUrl()).isNotBlank());
    }

    private static void assertCard(Map<String, ClassViews.ClassCard> cards, String title, String teacher, String theme, int bestAfter) {
        ClassViews.ClassCard card = cards.get(title);
        assertThat(card).as(title).isNotNull();
        assertThat(card.teacher().name()).as(title).isEqualTo(teacher);
        assertThat(card.topicName()).as(title).isEqualTo(theme);
        assertThat(card.bestAfterUnit()).as(title).isEqualTo(bestAfter);
    }

    private SecurityUserDetails signedIn(DemoPersonas.Persona persona) {
        return new SecurityUserDetails(userRepository.findByEmailWithRoles(persona.email()).orElseThrow());
    }
}
