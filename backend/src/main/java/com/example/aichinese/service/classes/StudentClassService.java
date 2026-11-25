package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.dto.classes.LessonViews;
import com.example.aichinese.entity.ClassEnrolment;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.VideoProgress;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.LearningPlanStepRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.VideoProgressRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.FileStorageService;
import com.example.aichinese.service.QuizScoring;
import com.example.aichinese.service.StoredFileDownload;
import com.example.aichinese.service.StudentLearningAccessService;

import lombok.RequiredArgsConstructor;

/**
 * The learner's classes: My classes, Find classes, joining and leaving, the class page (lessons with progress) and the
 * lesson page (video, key phrases, activities, quizzes). Learners only open lessons of classes they joined; an archived
 * class is read-only.
 */
@Service
@RequiredArgsConstructor
public class StudentClassService {

    private final ClassAccessService access;
    private final StudentLearningAccessService studentAccess;
    private final ClassContentSupport content;
    private final ClassProgressService progress;
    private final ClassCatalogueService catalogue;
    private final ClassEnrolmentRepository enrolmentRepository;
    private final LearningPlanStepRepository planSteps;
    private final QuizAttemptRepository quizAttemptRepository;
    private final VideoProgressRepository videoProgressRepository;
    private final FileStorageService fileStorageService;

    // My classes and Find classes

    @Transactional(readOnly = true)
    public ClassViews.MyClasses myClasses(SecurityUserDetails currentUser) {
        StudentProfile student = studentAccess.requireStudent(currentUser);
        List<CoursePackage> joined = enrolmentRepository.findVisibleClasses(student.getId());
        return new ClassViews.MyClasses(joinedCards(student, joined), catalogue.countFor(student.getId()));
    }

    /** Progress for the joined classes on the learner's plan. */
    @Transactional(readOnly = true)
    public List<ClassViews.MyClassCard> joinedCards(StudentProfile student, Set<Long> classIds) {
        if (classIds.isEmpty()) {
            return List.of();
        }
        return joinedCards(student, enrolmentRepository.findVisibleClasses(student.getId(), classIds));
    }

    private List<ClassViews.MyClassCard> joinedCards(StudentProfile student, List<CoursePackage> joined) {
        Map<Long, ClassProgress> progressByClass = classesProgress(student, joined);
        return joined.stream().map(course -> {
            ClassProgress state = progressByClass.get(course.getId());
            boolean archived = course.getContentStatus() == ContentStatus.ARCHIVED;
            return new ClassViews.MyClassCard(state.summary(course), state.completed(), state.percent(),
                    state.rows(), archived ? null : state.nextStep(), archived,
                    course.getArchivedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ClassViews.ClassCard> findClasses(SecurityUserDetails currentUser, int page, int size) {
        StudentProfile student = studentAccess.requireStudent(currentUser);
        return catalogue.search(student.getId(), page, size);
    }

    @Transactional(readOnly = true)
    public ClassViews.CatalogueMembership catalogueMembership(SecurityUserDetails currentUser) {
        Long studentId = studentAccess.requireStudent(currentUser).getId();
        return new ClassViews.CatalogueMembership(enrolmentRepository.countVisibleClasses(studentId),
                planSteps.findActiveClassIds(studentId));
    }

    /**
     * Joins a class from the catalogue (free). Joining twice is harmless, and joining again after leaving brings the
     * learner's progress back (it is kept by lesson). A class that left the catalogue (archived, or with no lesson left
     * to learn) stays visible only to learners who already joined it.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void join(SecurityUserDetails currentUser, Long classId) {
        StudentProfile student = studentAccess.requireLockedStudent(currentUser);
        ClassAccessService.EnrolmentState membership = access.lockForJoin(student, classId);
        if (membership.existing() != null) {
            return;
        }
        ClassEnrolment enrolment = new ClassEnrolment();
        enrolment.setStudent(student);
        enrolment.setCoursePackage(membership.coursePackage());
        enrolment.setJoinedAt(LocalDateTime.now());
        enrolmentRepository.save(enrolment);
    }

    /**
     * Leaves a class (also the Undo of joining it). The learner's lesson progress, activity completions and quiz attempts
     * are kept: joining again shows them again. Leaving a class that is not joined is harmless. An archived class cannot
     * be left: nobody can join it again, and its learners keep its lessons and results.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void leave(SecurityUserDetails currentUser, Long classId) {
        StudentProfile student = studentAccess.requireLockedStudent(currentUser);
        ClassAccessService.EnrolmentState membership = access.lockEnrolment(student, classId);
        if (membership.existing() == null) {
            return;
        }
        if (membership.coursePackage().getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(CONFLICT,
                    "An archived class can't be left. You can still open its lessons and your results.");
        }
        enrolmentRepository.delete(membership.existing());
    }

    // Class page

    @Transactional(readOnly = true)
    public ClassViews.StudentClass classPage(SecurityUserDetails currentUser, Long classId) {
        StudentProfile student = studentAccess.requireStudent(currentUser);
        CoursePackage coursePackage = content.requireClass(classId);
        boolean archived = coursePackage.getContentStatus() == ContentStatus.ARCHIVED;
        if (!access.hasJoined(student, coursePackage)) {
            ClassViews.ClassPreview preview = catalogue.preview(coursePackage);
            return new ClassViews.StudentClass(false, archived, coursePackage.getArchivedAt(), preview.summary(),
                    preview.units(), 0, null, List.of());
        }
        ClassProgress classProgress = classesProgress(student, List.of(coursePackage)).get(coursePackage.getId());
        return new ClassViews.StudentClass(
                true,
                archived,
                coursePackage.getArchivedAt(),
                classProgress.summary(coursePackage),
                List.of(),
                classProgress.completed(),
                archived ? null : classProgress.nextStep(),
                classProgress.units());
    }

    /** The learner's progress in one class: units with lesson rows, the open lessons ({@code rows}) and the next step. */
    private record ClassProgress(
            List<ClassViews.UnitProgress> units,
            List<ClassViews.LessonProgressRow> rows,
            int completed,
            ClassViews.NextStep nextStep) {

        int lessonCount() {
            return rows.size();
        }

        int percent() {
            return QuizScoring.percentage(completed, lessonCount());
        }

        ClassViews.ClassCard summary(CoursePackage coursePackage) {
            return ClassCatalogueService.card(coursePackage, lessonCount());
        }
    }

    /** Loads lesson progress across all of the learner's requested classes together. */
    private Map<Long, ClassProgress> classesProgress(StudentProfile student, List<CoursePackage> classes) {
        Map<Long, List<LearningModule>> unitsByClass = content.unitsByClass(classes);
        Map<Long, List<Lesson>> lessonsByUnit = content.lessonsByUnit(
                unitsByClass.values().stream().flatMap(List::stream).toList());
        List<Lesson> candidates = lessonsByUnit.values().stream().flatMap(List::stream)
                .filter(ClassAccessService::isVisibleToJoinedStudents)
                .toList();
        Map<Long, ClassProgressService.LessonState> states = progress.lessonStates(student, candidates);
        Map<Long, ClassProgress> result = new HashMap<>();
        for (CoursePackage course : classes) {
            result.put(course.getId(), classProgress(unitsByClass.getOrDefault(course.getId(), List.of()), lessonsByUnit, states));
        }
        return result;
    }

    private static ClassProgress classProgress(
            List<LearningModule> units,
            Map<Long, List<Lesson>> lessonsByUnit,
            Map<Long, ClassProgressService.LessonState> states) {
        List<ClassViews.UnitProgress> unitViews = new ArrayList<>();
        List<ClassViews.LessonProgressRow> rows = new ArrayList<>();
        int completed = 0;
        ClassViews.NextStep nextStep = null;
        for (LearningModule unit : units) {
            List<ClassViews.LessonProgressRow> unitRows = new ArrayList<>();
            int unitCompleted = 0;
            int unitLessonCount = 0;
            for (Lesson lesson : lessonsByUnit.getOrDefault(unit.getId(), List.of())) {
                ClassProgressService.LessonState state = states.get(lesson.getId());
                if (!ClassContentSupport.isLessonLive(lesson)) {
                    // A lesson no longer available stays in its unit, unnumbered, once the learner started it.
                    if (state != null && state.started()) {
                        unitRows.add(new ClassViews.LessonProgressRow(
                                lesson.getId(), lesson.getTitle(), state.status(), false));
                    }
                    continue;
                }
                unitLessonCount++;
                ClassViews.LessonProgressRow row = new ClassViews.LessonProgressRow(
                        lesson.getId(), lesson.getTitle(), state.status(), true);
                unitRows.add(row);
                rows.add(row);
                if (state.completed()) {
                    unitCompleted++;
                } else if (nextStep == null) {
                    nextStep = new ClassViews.NextStep(
                            lesson.getId(), lesson.getTitle(), rows.size(), state.stepsDone(), state.stepsTotal());
                }
            }
            if (!unitRows.isEmpty()) {
                unitViews.add(new ClassViews.UnitProgress(unit.getId(), unit.getTitle(), unitLessonCount, unitCompleted, unitRows));
            }
            completed += unitCompleted;
        }
        return new ClassProgress(unitViews, rows, completed, nextStep);
    }

    // Lesson page

    @Transactional(readOnly = true)
    public LessonViews.StudentLesson lesson(SecurityUserDetails currentUser, Long lessonId) {
        StudentProfile student = studentAccess.requireStudent(currentUser);
        ClassAccessService.StudentLesson found = access.requireStudentLesson(student, lessonId);
        return lessonView(student, found.coursePackage(), found.lesson());
    }

    /** Marks the lesson video as watched (it is not a lesson step, but it counts as starting the lesson). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void markVideoWatched(SecurityUserDetails currentUser, Long lessonId) {
        StudentProfile student = studentAccess.requireLockedStudent(currentUser);
        ClassAccessService.StudentLesson found = access.requireOpenLesson(student, lessonId);
        RecordedLessonVideo video = content.video(lessonId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Recorded video was not found."));
        VideoProgress watched = videoProgressRepository.findByStudentIdAndRecordedVideoId(student.getId(), video.getId())
                .orElseGet(() -> {
                    VideoProgress created = new VideoProgress();
                    created.setStudent(student);
                    created.setRecordedVideo(video);
                    return created;
                });
        watched.setCompleted(true);
        videoProgressRepository.save(watched);
        progress.recordProgress(student, found.lesson());
    }

    @Transactional(readOnly = true)
    public StoredFileDownload streamVideo(SecurityUserDetails currentUser, Long lessonId, Long videoId) {
        StudentProfile student = studentAccess.requireStudent(currentUser);
        access.requireOpenLesson(student, lessonId);
        RecordedLessonVideo video = content.video(lessonId)
                .filter(found -> found.getId().equals(videoId) && found.getUploadedFile() != null)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Recorded video was not found."));
        return fileStorageService.loadRecordedVideo(video.getUploadedFile());
    }

    /**
     * The lesson page. A lesson no longer available (archived) shows only where the learner got to; a lesson of an
     * archived class shows its key phrases, activities and quiz results, and its video without a link to play it.
     */
    private LessonViews.StudentLesson lessonView(StudentProfile student, CoursePackage coursePackage, Lesson lesson) {
        boolean available = ClassContentSupport.isLessonLive(lesson);
        boolean archived = coursePackage.getContentStatus() == ContentStatus.ARCHIVED;
        ClassProgressService.LessonState state = progress.lessonState(student, lesson);
        if (!available) {
            return new LessonViews.StudentLesson(coursePackage.getId(), coursePackage.getTitle(), lesson.getTitle(),
                    false, null, List.of(), List.of(), List.of(),
                    state.stepsDone(), state.stepsTotal(), state.status(), archived);
        }
        // In an archived class the video is listed but no longer plays: no link to it.
        RecordedLessonVideo video = state.video();
        LessonViews.Video videoView = video == null ? null : new LessonViews.Video(
                video.getTitle(),
                archived ? null : video.getExternalVideoUrl(),
                archived || video.getUploadedFile() == null
                        ? null
                        : "/api/v1/students/classes/lessons/" + lesson.getId() + "/video/" + video.getId() + "/stream",
                video.getDurationSeconds(),
                state.videoWatched());
        List<LessonViews.ActivityStep> activities = new ArrayList<>();
        int activityNumber = 1;
        for (LessonActivity activity : state.activities()) {
            activities.add(new LessonViews.ActivityStep(ClassContentSupport.typeOf(activity).name(), activity.getId(),
                    activity.getTitle(), activityNumber++, state.finished(activity)));
        }
        return new LessonViews.StudentLesson(
                coursePackage.getId(),
                coursePackage.getTitle(),
                lesson.getTitle(),
                true,
                videoView,
                content.keyPhraseViews(lesson.getId()),
                activities,
                quizCards(student.getId(), state, archived),
                state.stepsDone(),
                state.stepsTotal(),
                state.status(),
                archived);
    }

    private List<LessonViews.QuizCard> quizCards(Long studentId, ClassProgressService.LessonState state, boolean archived) {
        List<Long> quizIds = state.quizzes().stream().map(Quiz::getId).toList();
        if (quizIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> questionCounts = content.questionCounts(quizIds);
        Set<Long> inProgress = archived ? Set.of() : quizAttemptRepository.findOpenQuizIds(studentId, quizIds);
        return state.quizzes().stream()
                .map(quiz -> quizCard(quiz, questionCounts.getOrDefault(quiz.getId(), 0), state,
                        inProgress.contains(quiz.getId())))
                .toList();
    }

    private static LessonViews.QuizCard quizCard(
            Quiz quiz, int questionCount, ClassProgressService.LessonState state, boolean inProgress) {
        Optional<QuizAttempt> best = state.bestAttempt(quiz);
        return new LessonViews.QuizCard(
                quiz.getId(),
                questionCount,
                best.map(ClassProgressService::attemptPercent).orElse(null),
                state.attemptsFor(quiz).size(),
                best.map(QuizAttempt::getId).orElse(null),
                inProgress);
    }
}
