package com.example.aichinese.config.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.classes.AdminClassViews;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.ClassActivityCompletionRepository;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.service.classes.LessonEditorService;

/** The demo classes hold only states the product itself can reach. */
@SpringBootTest
@ActiveProfiles({"demo", "demo-test"})
class DemoClassesSeederTests {

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
    private LessonEditorService lessonEditor;

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
     * Mandarin Foundations is from before an admin archived it.
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
            assertThat(enrolmentRepository.countByCoursePackageId(draft.getId())).as(draft.getTitle()).isZero();
        }
        CoursePackage foundations = packages.stream()
                .filter(pkg -> pkg.getContentStatus() == ContentStatus.ARCHIVED)
                .findFirst().orElseThrow();
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
        assertThat(completionRepository.findAll()).filteredOn(completion -> foundationLessons.contains(completion.getLesson().getId()))
                .hasSize(15)
                .allSatisfy(completion -> assertThat(completion.getCompletedAt()).isBefore(archivedAt));
    }

    /** Mandarin Foundations reads as archiving left it, so Content › Classes dates its last change then. */
    @Test
    void theArchivedClassWasLastChangedWhenItWasArchived() {
        CoursePackage foundations = coursePackageRepository.findAll().stream()
                .filter(pkg -> pkg.getContentStatus() == ContentStatus.ARCHIVED)
                .findFirst().orElseThrow();

        assertThat(foundations.getStatusBeforeArchive()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(foundations.getUpdatedAt()).isEqualTo(foundations.getArchivedAt());
    }

    /** Timestamps are server-local: what the learners did is in the past whatever zone the server runs in. */
    @Test
    void theLearnersWorkIsInThePast() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(lessonRepository.findAll()).isNotEmpty()
                .allSatisfy(lesson -> assertThat(lesson.getUpdatedAt()).isAfterOrEqualTo(lesson.getCreatedAt()));
        assertThat(attemptRepository.findAll()).isNotEmpty()
                .allSatisfy(attempt -> {
                    assertThat(attempt.getCompletedAt()).isBeforeOrEqualTo(now);
                    assertThat(attempt.getCreatedAt()).isBeforeOrEqualTo(attempt.getCompletedAt());
                    assertThat(attempt.getUpdatedAt()).isEqualTo(attempt.getCompletedAt());
                });
        assertThat(completionRepository.findAll()).isNotEmpty()
                .allSatisfy(completion -> assertThat(completion.getCompletedAt()).isBeforeOrEqualTo(now));
    }

    @Test
    @Transactional
    void everyCompletedActivityFallsWithinTheLearnersVisitsToItsLesson() {
        assertThat(completionRepository.findAll()).isNotEmpty().allSatisfy(activity -> {
            var progress = progressRepository.findByStudentIdAndLessonId(
                    activity.getStudent().getId(), activity.getLesson().getId()).orElseThrow();
            assertThat(activity.getCompletedAt()).as("%s in %s", activity.getActivityType(), activity.getLesson().getTitle())
                    .isBetween(progress.getCreatedAt(), progress.getLastAccessedAt());
        });
    }
}
