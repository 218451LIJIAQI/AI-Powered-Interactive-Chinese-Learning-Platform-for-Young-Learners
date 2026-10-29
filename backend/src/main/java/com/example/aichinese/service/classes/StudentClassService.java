package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.dto.classes.LessonViews;
import com.example.aichinese.dto.classes.LiveSessionViews;
import com.example.aichinese.entity.ClassEnrolment;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherFeedback;
import com.example.aichinese.entity.VideoProgress;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.TeacherFeedbackRepository;
import com.example.aichinese.repository.VideoProgressRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.FileStorageService;
import com.example.aichinese.service.NotificationService;
import com.example.aichinese.service.StoredFileDownload;

/**
 * The student's classes: My classes, Find classes, joining, the class page (lessons with progress, quiz results,
 * feedback) and the lesson page (video, key phrases, activities, quizzes, live sessions). Students only open
 * lessons of classes they joined.
 */
@Service
public class StudentClassService {

    private final ClassAccessService access;
    private final ClassContentSupport content;
    private final ClassProgressService progress;
    private final ClassCatalogueService catalogue;
    private final ClassLiveSessionService liveSessions;
    private final ClassQuizService quizzes;
    private final ClassEnrolmentRepository enrolmentRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final TeacherFeedbackRepository feedbackRepository;
    private final VideoProgressRepository videoProgressRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    public StudentClassService(
            ClassAccessService access,
            ClassContentSupport content,
            ClassProgressService progress,
            ClassCatalogueService catalogue,
            ClassLiveSessionService liveSessions,
            ClassQuizService quizzes,
            ClassEnrolmentRepository enrolmentRepository,
            QuizAttemptRepository quizAttemptRepository,
            TeacherFeedbackRepository feedbackRepository,
            VideoProgressRepository videoProgressRepository,
            FileStorageService fileStorageService,
            NotificationService notificationService) {
        this.access = access;
        this.content = content;
        this.progress = progress;
        this.catalogue = catalogue;
        this.liveSessions = liveSessions;
        this.quizzes = quizzes;
        this.enrolmentRepository = enrolmentRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.feedbackRepository = feedbackRepository;
        this.videoProgressRepository = videoProgressRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
    }

    // ----- My classes and Find classes -----

    @Transactional(readOnly = true)
    public ClassViews.MyClasses myClasses(SecurityUserDetails currentUser) {
        StudentProfile student = access.requireStudent(currentUser);
        List<ClassEnrolment> enrolments = enrolmentRepository.findByStudentIdOrderByJoinedAtDesc(student.getId()).stream()
                .filter(enrolment -> ClassAccessService.isOpenToJoinedStudents(enrolment.getCoursePackage()))
                .toList();
        List<CoursePackage> joined = enrolments.stream().map(ClassEnrolment::getCoursePackage).toList();
        Map<Long, List<LiveSessionViews.SessionBrief>> sessions = liveSessions.upcomingByClass(
                joined.stream().map(CoursePackage::getId).toList(), student);
        List<ClassViews.MyClassCard> cards = new ArrayList<>();
        for (ClassEnrolment enrolment : enrolments) {
            CoursePackage coursePackage = enrolment.getCoursePackage();
            ClassProgress classProgress = classProgress(student, coursePackage);
            List<LiveSessionViews.SessionBrief> classSessions = sessions.getOrDefault(coursePackage.getId(), List.of());
            cards.add(new ClassViews.MyClassCard(
                    catalogue.card(coursePackage),
                    enrolment.getJoinedAt(),
                    classProgress.completed(),
                    ClassProgressService.classPercent(classProgress.completed(), classProgress.lessonCount()),
                    classProgress.rows().stream().limit(3).toList(),
                    classProgress.nextStep(),
                    classSessions.stream().filter(LiveSessionViews.SessionBrief::booked).findFirst()
                            .orElse(classSessions.stream().findFirst().orElse(null)),
                    coursePackage.getContentStatus() == ContentStatus.ARCHIVED));
        }
        LocalDate today = LocalDate.now();
        LiveSessionViews.SessionBrief liveToday = sessions.values().stream()
                .flatMap(List::stream)
                .filter(LiveSessionViews.SessionBrief::booked)
                .filter(session -> session.startsAt().toLocalDate().equals(today))
                .min(Comparator.comparing(LiveSessionViews.SessionBrief::startsAt))
                .orElse(null);
        Set<Long> joinedIds = new HashSet<>(joined.stream().map(CoursePackage::getId).toList());
        List<CoursePackage> others = catalogue.catalogueClasses().stream()
                .filter(coursePackage -> !joinedIds.contains(coursePackage.getId()))
                .toList();
        Map<Long, ClassViews.TeacherView> otherTeachers = new LinkedHashMap<>();
        others.forEach(coursePackage -> otherTeachers.putIfAbsent(coursePackage.getTeacher().getId(),
                ClassCatalogueService.teacher(coursePackage.getTeacher())));
        long upcoming = sessions.values().stream().mapToLong(List::size).sum();
        return new ClassViews.MyClasses(cards, liveToday, others.size(),
                otherTeachers.values().stream().limit(3).toList(), upcoming);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClassViews.ClassCard> findClasses(
            SecurityUserDetails currentUser,
            ClassCatalogueService.Query query,
            int page,
            int size) {
        StudentProfile student = access.requireStudent(currentUser);
        Set<Long> joined = new HashSet<>(enrolmentRepository.findClassIdsByStudentId(student.getId()));
        return catalogue.search(query, joined, page, size);
    }

    /** Joins a class from the catalogue (free); the teacher is told about the new student. Joining twice is harmless. */
    @Transactional
    public ClassViews.StudentClass join(SecurityUserDetails currentUser, Long classId) {
        StudentProfile student = access.requireStudent(currentUser);
        CoursePackage coursePackage = access.requireVisibleClass(student, classId);
        if (!access.isJoined(student, coursePackage)) {
            if (!ClassAccessService.isInCatalogue(coursePackage)) {
                throw new ResponseStatusException(BAD_REQUEST, "This class is not open for new students.");
            }
            ClassEnrolment enrolment = new ClassEnrolment();
            enrolment.setStudent(student);
            enrolment.setCoursePackage(coursePackage);
            enrolment.setJoinedAt(LocalDateTime.now());
            enrolmentRepository.save(enrolment);
            notificationService.safeCreateNotification(
                    coursePackage.getTeacher().getUser(),
                    NotificationType.CLASS_ENROLMENT,
                    "New student",
                    student.getUser().getDisplayName() + " joined " + coursePackage.getTitle() + ".",
                    "CLASS",
                    coursePackage.getId(),
                    "/teacher/students",
                    student.getUser());
        }
        return classView(student, coursePackage, true);
    }

    // ----- class page -----

    @Transactional(readOnly = true)
    public ClassViews.StudentClass classPage(SecurityUserDetails currentUser, Long classId) {
        StudentProfile student = access.requireStudent(currentUser);
        CoursePackage coursePackage = access.requireVisibleClass(student, classId);
        return classView(student, coursePackage, access.isJoined(student, coursePackage));
    }

    private ClassViews.StudentClass classView(StudentProfile student, CoursePackage coursePackage, boolean joined) {
        List<LiveSessionViews.SessionBrief> sessions = liveSessions
                .upcomingByClass(List.of(coursePackage.getId()), student)
                .getOrDefault(coursePackage.getId(), List.of());
        boolean archived = coursePackage.getContentStatus() == ContentStatus.ARCHIVED;
        if (!joined) {
            return new ClassViews.StudentClass(false, archived, catalogue.card(coursePackage), coursePackage.getTeacherNote(),
                    catalogue.unitPreviews(coursePackage), 0, 0, 0, null, List.of(), sessions, 0, 0);
        }
        ClassProgress classProgress = classProgress(student, coursePackage);
        long attempts = quizAttemptRepository
                .findByStudentIdAndQuizLessonIdInAndSubmittedTrueOrderByCompletedAtDescIdDesc(student.getId(), classProgress.allLessonIds())
                .size();
        long feedback = feedbackRepository.countByStudentIdAndCoursePackageId(student.getId(), coursePackage.getId());
        return new ClassViews.StudentClass(
                true,
                archived,
                catalogue.card(coursePackage),
                coursePackage.getTeacherNote(),
                List.of(),
                ClassProgressService.classPercent(classProgress.completed(), classProgress.lessonCount()),
                classProgress.completed(),
                classProgress.lessonCount(),
                classProgress.nextStep(),
                classProgress.units(),
                sessions,
                attempts,
                feedback);
    }

    /** The student's progress in one class: units with lesson rows, totals and the next step. */
    private record ClassProgress(
            List<ClassViews.UnitProgress> units,
            List<ClassViews.LessonProgressRow> rows,
            int completed,
            int lessonCount,
            ClassViews.NextStep nextStep,
            List<Long> allLessonIds) {
    }

    private ClassProgress classProgress(StudentProfile student, CoursePackage coursePackage) {
        List<LearningModule> units = content.units(coursePackage, false);
        Map<Long, List<Lesson>> lessonsByUnit = content.lessonsByUnit(units);
        List<Lesson> candidates = lessonsByUnit.values().stream().flatMap(List::stream)
                .filter(lesson -> ClassContentSupport.isLessonLive(lesson) || wasPublished(lesson))
                .toList();
        Map<Long, ClassProgressService.LessonState> states = progress.lessonStates(student, candidates);
        List<ClassViews.UnitProgress> unitViews = new ArrayList<>();
        List<ClassViews.LessonProgressRow> rows = new ArrayList<>();
        int completed = 0;
        int lessonCount = 0;
        ClassViews.NextStep nextStep = null;
        for (LearningModule unit : units) {
            List<ClassViews.LessonProgressRow> unitRows = new ArrayList<>();
            int unitCompleted = 0;
            int unitCount = 0;
            int number = 0;
            for (Lesson lesson : lessonsByUnit.getOrDefault(unit.getId(), List.of())) {
                ClassProgressService.LessonState state = states.get(lesson.getId());
                boolean live = ClassContentSupport.isLessonLive(lesson);
                if (!live && (state == null || !state.started())) {
                    continue;
                }
                if (live) {
                    number++;
                    unitCount++;
                }
                ClassViews.LessonProgressRow row = new ClassViews.LessonProgressRow(
                        lesson.getId(),
                        lesson.getTitle(),
                        live ? number : 0,
                        ClassProgressService.status(state),
                        state == null ? 0 : state.percent(),
                        state == null ? null : state.bestQuizPercent(),
                        state == null ? 0 : state.activityCount(),
                        live);
                unitRows.add(row);
                if (!live) {
                    continue;
                }
                rows.add(row);
                if (state != null && state.completed()) {
                    unitCompleted++;
                } else if (nextStep == null) {
                    nextStep = new ClassViews.NextStep(
                            lesson.getId(),
                            lesson.getTitle(),
                            number,
                            state == null ? 0 : state.activitiesDone(),
                            state == null ? 0 : state.activityCount(),
                            state != null && state.quizCount() > 0 && state.quizzesDone() >= state.quizCount());
                }
            }
            if (!unitRows.isEmpty()) {
                unitViews.add(new ClassViews.UnitProgress(unit.getId(), unit.getTitle(), unitCount, unitCompleted, unitRows));
            }
            completed += unitCompleted;
            lessonCount += unitCount;
        }
        return new ClassProgress(unitViews, rows, completed, lessonCount, nextStep,
                candidates.stream().map(Lesson::getId).toList());
    }

    private static boolean wasPublished(Lesson lesson) {
        return lesson.getContentStatus() == ContentStatus.ARCHIVED && lesson.getStatusBeforeArchive() == ContentStatus.PUBLISHED;
    }

    @Transactional(readOnly = true)
    public ClassViews.QuizResults quizResults(SecurityUserDetails currentUser, Long classId) {
        StudentProfile student = access.requireStudent(currentUser);
        CoursePackage coursePackage = access.requireJoinedClass(student, classId);
        List<Long> lessonIds = content.lessonsInOrder(coursePackage, true).stream().map(Lesson::getId).toList();
        List<ClassViews.AttemptRow> rows = quizAttemptRepository
                .findByStudentIdAndQuizLessonIdInAndSubmittedTrueOrderByCompletedAtDescIdDesc(student.getId(), lessonIds).stream()
                .map(ClassQuizService::attemptRow)
                .toList();
        return new ClassViews.QuizResults(ClassProgressService.average(rows.stream().map(ClassViews.AttemptRow::percent).toList()), rows);
    }

    @Transactional(readOnly = true)
    public List<ClassViews.FeedbackItem> feedback(SecurityUserDetails currentUser, Long classId) {
        StudentProfile student = access.requireStudent(currentUser);
        CoursePackage coursePackage = access.requireJoinedClass(student, classId);
        return feedbackRepository.findByStudentIdAndCoursePackageIdOrderByCreatedAtDescIdDesc(student.getId(), coursePackage.getId())
                .stream()
                .map(StudentClassService::feedbackItem)
                .toList();
    }

    static ClassViews.FeedbackItem feedbackItem(TeacherFeedback feedback) {
        Lesson lesson = feedback.getLesson() != null ? feedback.getLesson()
                : feedback.getQuizAttempt() != null ? feedback.getQuizAttempt().getQuiz().getLesson() : null;
        return new ClassViews.FeedbackItem(
                feedback.getId(),
                feedback.getTeacher().getUser().getDisplayName(),
                feedback.getFeedbackType(),
                ClassFeedbackService.about(feedback),
                lesson == null ? null : lesson.getTitle(),
                feedback.getFeedbackMessage(),
                feedback.getCreatedAt(),
                feedback.getEditedAt());
    }

    // ----- lesson page -----

    /** Writable: an expired quiz attempt of the lesson is submitted first, as the timer promised. */
    @Transactional
    public LessonViews.StudentLesson lesson(SecurityUserDetails currentUser, Long lessonId) {
        StudentProfile student = access.requireStudent(currentUser);
        ClassAccessService.StudentLesson found = access.requireStudentLesson(student, lessonId);
        return lessonView(student, found.coursePackage(), found.lesson());
    }

    /** Marks the lesson video as watched (it is not a lesson step, but shows on the lesson and to the teacher). */
    @Transactional
    public LessonViews.StudentLesson markVideoWatched(SecurityUserDetails currentUser, Long lessonId) {
        StudentProfile student = access.requireStudent(currentUser);
        ClassAccessService.StudentLesson found = access.requireOpenLesson(student, lessonId);
        RecordedLessonVideo video = content.video(lessonId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Recorded video was not found."));
        VideoProgress watched = videoProgressRepository.findByStudentIdAndRecordedVideoId(student.getId(), video.getId())
                .orElseGet(() -> {
                    VideoProgress created = new VideoProgress();
                    created.setStudent(student);
                    created.setLesson(found.lesson());
                    created.setRecordedVideo(video);
                    return created;
                });
        watched.setCompleted(true);
        watched.setProgressPercentage(100);
        watched.setLastWatchedAt(LocalDateTime.now());
        videoProgressRepository.save(watched);
        return lessonView(student, found.coursePackage(), found.lesson());
    }

    @Transactional(readOnly = true)
    public StoredFileDownload streamVideo(SecurityUserDetails currentUser, Long lessonId, Long videoId) {
        StudentProfile student = access.requireStudent(currentUser);
        access.requireOpenLesson(student, lessonId);
        RecordedLessonVideo video = content.video(lessonId)
                .filter(found -> found.getId().equals(videoId) && found.getUploadedFile() != null)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Recorded video was not found."));
        return fileStorageService.loadRecordedVideo(video.getUploadedFile());
    }

    private LessonViews.StudentLesson lessonView(StudentProfile student, CoursePackage coursePackage, Lesson lesson) {
        boolean available = ClassContentSupport.isLessonLive(lesson);
        quizzes.finishExpiredAttempts(student, lesson);
        ClassProgressService.LessonState state = progress.lessonStates(student, List.of(lesson)).get(lesson.getId());
        List<Lesson> unitLessons = content.lessonsByUnit(List.of(lesson.getModule())).getOrDefault(lesson.getModule().getId(), List.of())
                .stream().filter(ClassContentSupport::isLessonLive).toList();
        int number = unitLessons.indexOf(lesson) + 1;
        String teacherName = coursePackage.getTeacher().getUser().getDisplayName();
        if (!available) {
            return new LessonViews.StudentLesson(coursePackage.getId(), coursePackage.getTitle(), teacherName,
                    lesson.getModule().getTitle(), lesson.getTitle(), number,
                    null, null, false, null, List.of(), List.of(), List.of(),
                    state == null ? 0 : state.stepsDone(), state == null ? 0 : state.stepsTotal(),
                    state == null ? 0 : state.percent(), ClassProgressService.status(state),
                    state == null ? null : state.bestQuizPercent(), List.of());
        }
        RecordedLessonVideo video = content.video(lesson.getId()).orElse(null);
        LessonViews.Video videoView = null;
        if (video != null) {
            VideoProgress watched = videoProgressRepository.findByStudentIdAndRecordedVideoId(student.getId(), video.getId()).orElse(null);
            videoView = new LessonViews.Video(
                    video.getTitle(),
                    video.getExternalVideoUrl(),
                    video.getUploadedFile() == null
                            ? null
                            : "/api/v1/students/classes/lessons/" + lesson.getId() + "/video/" + video.getId() + "/stream",
                    video.getDurationSeconds(),
                    watched != null && watched.isCompleted(),
                    watched == null || watched.getProgressPercentage() == null ? 0 : watched.getProgressPercentage());
        }
        Set<String> done = new HashSet<>(progress.completedActivityKeys(student, lesson));
        List<LessonViews.ActivityStep> activities = new ArrayList<>();
        int activityNumber = 1;
        for (LessonActivity activity : content.activities(lesson.getId())) {
            if (!ClassContentSupport.isLive(activity)) {
                continue;
            }
            String type = ClassContentSupport.typeOf(activity).name();
            var stage = ((PracticeActivityContent) activity).getLearningStage();
            activities.add(new LessonViews.ActivityStep(type, activity.getId(), activity.getTitle(), activityNumber++,
                    stage == null ? null : stage.name(), done.contains(type + "#" + activity.getId())));
        }
        List<Quiz> liveQuizzes = content.quizzes(lesson.getId()).stream().filter(ClassContentSupport::isLive).toList();
        Map<Long, List<QuizQuestion>> questions = content.questionsByQuiz(liveQuizzes.stream().map(Quiz::getId).toList());
        List<QuizAttempt> attempts = quizAttemptRepository
                .findByStudentIdAndQuizLessonIdInAndSubmittedTrueOrderByCompletedAtDescIdDesc(student.getId(), List.of(lesson.getId()));
        List<LessonViews.QuizCard> quizCards = liveQuizzes.stream()
                .map(quiz -> quizCard(quiz, questions.getOrDefault(quiz.getId(), List.of()), attempts,
                        quizzes.hasOpenAttempt(student, quiz)))
                .toList();
        List<LiveSessionViews.SessionBrief> sessions = liveSessions
                .upcomingByClass(List.of(coursePackage.getId()), student)
                .getOrDefault(coursePackage.getId(), List.of()).stream()
                .filter(session -> lesson.getId().equals(session.lessonId()))
                .toList();
        return new LessonViews.StudentLesson(
                coursePackage.getId(),
                coursePackage.getTitle(),
                teacherName,
                lesson.getModule().getTitle(),
                lesson.getTitle(),
                number,
                lesson.getSummary(),
                lesson.getEstimatedDurationMinutes(),
                true,
                videoView,
                content.keyPhrases(lesson.getId()).stream()
                        .map(phrase -> new LessonViews.KeyPhrase(phrase.getChinese(), phrase.getPinyin(), phrase.getMeaning()))
                        .toList(),
                activities,
                quizCards,
                state == null ? 0 : state.stepsDone(),
                state == null ? 0 : state.stepsTotal(),
                state == null ? 0 : state.percent(),
                ClassProgressService.status(state),
                state == null ? null : state.bestQuizPercent(),
                sessions);
    }

    private static LessonViews.QuizCard quizCard(Quiz quiz, List<QuizQuestion> questions, List<QuizAttempt> attempts, boolean inProgress) {
        List<QuizAttempt> own = attempts.stream().filter(attempt -> attempt.getQuiz().getId().equals(quiz.getId())).toList();
        return new LessonViews.QuizCard(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getLearningStage() == null ? null : quiz.getLearningStage().name(),
                questions.size(),
                questions.stream().mapToInt(QuizQuestion::getPoints).sum(),
                quiz.getTimeLimitMinutes(),
                ClassQuizService.hintsAvailable(quiz, questions),
                ClassQuizService.best(own),
                own.size(),
                inProgress);
    }
}
