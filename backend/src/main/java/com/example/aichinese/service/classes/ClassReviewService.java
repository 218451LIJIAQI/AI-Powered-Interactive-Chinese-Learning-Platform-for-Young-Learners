package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.ReviewViews;
import com.example.aichinese.dto.classes.TeacherClassViews;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ClassContentEventType;
import com.example.aichinese.entity.enums.ClassRevisionStatus;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.NotificationService;

/**
 * The admin review queue of teacher classes. A new class is reviewed together with the lessons submitted with it
 * and they go live together; a change to the details of a published class, a new lesson of a published class and a
 * revision of a published lesson are reviewed on their own. Approving makes them live; requesting changes sends the
 * reason to the teacher, who sees it in the editor. Every decision is notified and written to the audit log.
 */
@Service
public class ClassReviewService {

    public static final String KIND_NEW_CLASS = "NEW_CLASS";
    public static final String KIND_CLASS_DETAILS = "CLASS_DETAILS";
    public static final String KIND_NEW_LESSON = "NEW_LESSON";
    public static final String KIND_LESSON_REVISION = "LESSON_REVISION";
    private static final String NOTHING_TO_REVIEW = "Nothing of this is waiting for review.";
    private static final String TEACHER_CLASSES_PATH = "/teacher/classes/";

    private final ClassContentSupport content;
    private final ClassEventService events;
    private final LessonRevisionService revisions;
    private final CoursePackageRepository coursePackageRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public ClassReviewService(
            ClassContentSupport content,
            ClassEventService events,
            LessonRevisionService revisions,
            CoursePackageRepository coursePackageRepository,
            LessonRepository lessonRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.content = content;
        this.events = events;
        this.revisions = revisions;
        this.coursePackageRepository = coursePackageRepository;
        this.lessonRepository = lessonRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    // ----- queue -----

    /** Everything waiting for a decision, oldest first. {@code type}: ALL, CLASS, LESSON or QUIZ (changes a quiz). */
    @Transactional(readOnly = true)
    public ReviewViews.Queue queue(String type) {
        String filter = type == null || type.isBlank() ? "ALL" : type.strip().toUpperCase(Locale.ROOT);
        if (!List.of("ALL", "CLASS", "LESSON", "QUIZ").contains(filter)) {
            throw new ResponseStatusException(BAD_REQUEST, "Review type must be ALL, CLASS, LESSON or QUIZ.");
        }
        List<ReviewViews.QueueItem> items = queueItems().stream()
                .filter(item -> switch (filter) {
                    case "CLASS" -> item.type().equals("CLASS");
                    case "LESSON" -> item.type().equals("LESSON");
                    case "QUIZ" -> item.changesQuiz();
                    default -> true;
                })
                .toList();
        return new ReviewViews.Queue(items, items.size());
    }

    @Transactional(readOnly = true)
    public long waitingCount() {
        return queueItems().size();
    }

    private List<ReviewViews.QueueItem> queueItems() {
        List<ReviewViews.QueueItem> items = new ArrayList<>();
        for (CoursePackage coursePackage : coursePackageRepository.findByContentStatusOrderBySubmittedAtAscIdAsc(ContentStatus.SUBMITTED)) {
            items.add(newClassItem(coursePackage, submittedLessons(coursePackage)));
        }
        for (CoursePackage coursePackage : coursePackageRepository.findByRevisionStatusOrderByRevisionSubmittedAtAscIdAsc(ClassRevisionStatus.SUBMITTED)) {
            items.add(classDetailsItem(coursePackage));
        }
        for (Lesson lesson : lessonRepository.findByContentStatusAndRevisionOfIsNullOrderBySubmittedAtAscIdAsc(ContentStatus.SUBMITTED)) {
            CoursePackage coursePackage = content.requireClassOfLesson(lesson);
            if (coursePackage.getContentStatus() == ContentStatus.SUBMITTED) {
                continue;
            }
            items.add(newLessonItem(coursePackage, lesson));
        }
        for (Lesson revision : lessonRepository.findByContentStatusAndRevisionOfIsNotNullOrderBySubmittedAtAscIdAsc(ContentStatus.SUBMITTED)) {
            CoursePackage coursePackage = content.requireClassOfLesson(revision.getRevisionOf());
            items.add(revisionItem(coursePackage, revision, revisions.changes(revision)));
        }
        items.sort(Comparator.comparing(ReviewViews.QueueItem::submittedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return items;
    }

    private static ReviewViews.QueueItem newClassItem(CoursePackage coursePackage, List<Lesson> lessons) {
        return new ReviewViews.QueueItem(KIND_NEW_CLASS, "CLASS", coursePackage.getId(), null, coursePackage.getTitle(), null,
                teacherName(coursePackage), coursePackage.getSubmittedAt(), lessons.stream().map(Lesson::getTitle).toList(),
                !lessons.isEmpty());
    }

    private static ReviewViews.QueueItem classDetailsItem(CoursePackage coursePackage) {
        return new ReviewViews.QueueItem(KIND_CLASS_DETAILS, "CLASS", coursePackage.getId(), null, coursePackage.getTitle(),
                null, teacherName(coursePackage), coursePackage.getRevisionSubmittedAt(), List.of(), false);
    }

    private static ReviewViews.QueueItem newLessonItem(CoursePackage coursePackage, Lesson lesson) {
        return new ReviewViews.QueueItem(KIND_NEW_LESSON, "LESSON", coursePackage.getId(), lesson.getId(), lesson.getTitle(),
                coursePackage.getTitle(), teacherName(coursePackage), lesson.getSubmittedAt(), List.of(), true);
    }

    private static ReviewViews.QueueItem revisionItem(
            CoursePackage coursePackage,
            Lesson revision,
            List<TeacherClassViews.Change> changes) {
        return new ReviewViews.QueueItem(KIND_LESSON_REVISION, "LESSON", coursePackage.getId(), revision.getRevisionOf().getId(),
                revision.getTitle(), coursePackage.getTitle(), teacherName(coursePackage), revision.getSubmittedAt(),
                List.of(), LessonRevisionService.touchesQuiz(changes));
    }

    // ----- detail -----

    @Transactional(readOnly = true)
    public ReviewViews.Detail classDetail(Long classId) {
        CoursePackage coursePackage = requireClass(classId);
        List<TeacherClassViews.Event> history = events.classOnlyHistory(classId);
        if (coursePackage.getContentStatus() == ContentStatus.SUBMITTED) {
            List<Lesson> lessons = submittedLessons(coursePackage);
            List<String> notInReview = content.lessonsInOrder(coursePackage, false).stream()
                    .filter(lesson -> lesson.getContentStatus() != ContentStatus.SUBMITTED
                            && lesson.getContentStatus() != ContentStatus.ARCHIVED)
                    .map(Lesson::getTitle)
                    .toList();
            return new ReviewViews.Detail(newClassItem(coursePackage, lessons), classPreview(coursePackage, false, lessons),
                    lessons.stream().map(lesson -> lessonPreview(coursePackage, lesson, lesson)).toList(),
                    List.of(), notInReview, history);
        }
        if (coursePackage.getRevisionStatus() == ClassRevisionStatus.SUBMITTED) {
            return new ReviewViews.Detail(classDetailsItem(coursePackage), classPreview(coursePackage, true, List.of()),
                    List.of(), TeacherClassService.detailChanges(coursePackage), List.of(), history);
        }
        throw new ResponseStatusException(NOT_FOUND, NOTHING_TO_REVIEW);
    }

    @Transactional(readOnly = true)
    public ReviewViews.Detail lessonDetail(Long lessonId) {
        Lesson lesson = requireLesson(lessonId);
        CoursePackage coursePackage = content.requireClassOfLesson(lesson);
        Lesson revision = content.revisionOf(lesson).orElse(null);
        List<TeacherClassViews.Event> history = events.lessonHistory(lessonId);
        if (revision != null && revision.getContentStatus() == ContentStatus.SUBMITTED) {
            List<TeacherClassViews.Change> changes = revisions.changes(revision);
            return new ReviewViews.Detail(revisionItem(coursePackage, revision, changes), null,
                    List.of(lessonPreview(coursePackage, lesson, revision)), changes, List.of(), history);
        }
        if (lesson.getContentStatus() == ContentStatus.SUBMITTED && coursePackage.getContentStatus() != ContentStatus.SUBMITTED) {
            return new ReviewViews.Detail(newLessonItem(coursePackage, lesson), null,
                    List.of(lessonPreview(coursePackage, lesson, lesson)), List.of(), List.of(), history);
        }
        throw new ResponseStatusException(NOT_FOUND, NOTHING_TO_REVIEW);
    }

    // ----- decisions -----

    @Transactional
    public ReviewViews.Queue approveClass(SecurityUserDetails currentUser, Long classId) {
        User admin = requireAdmin(currentUser);
        CoursePackage coursePackage = requireClass(classId);
        if (coursePackage.getContentStatus() == ContentStatus.SUBMITTED) {
            List<Lesson> lessons = submittedLessons(coursePackage);
            coursePackage.setContentStatus(ContentStatus.PUBLISHED);
            coursePackage.setRejectionReason(null);
            coursePackageRepository.save(coursePackage);
            events.record(coursePackage, null, admin, ClassContentEventType.CLASS_APPROVED,
                    lessons.isEmpty() ? null : String.join(", ", lessons.stream().map(Lesson::getTitle).toList()), null);
            for (Lesson lesson : lessons) {
                revisions.publishNewLesson(lesson);
                events.record(coursePackage, lesson, admin, ClassContentEventType.LESSON_APPROVED);
            }
            notifyTeacher(coursePackage, admin, "Class approved",
                    "\"" + coursePackage.getTitle() + "\" is approved and live for students.");
            audit(admin, "ADMIN_CLASS_APPROVE", coursePackage, null, "Admin approved a new class.");
        } else if (coursePackage.getRevisionStatus() == ClassRevisionStatus.SUBMITTED) {
            List<TeacherClassViews.Change> changes = TeacherClassService.detailChanges(coursePackage);
            TeacherClassService.applyApprovedDetails(coursePackage);
            coursePackageRepository.save(coursePackage);
            events.record(coursePackage, null, admin, ClassContentEventType.CLASS_DETAILS_APPROVED, null, changes);
            notifyTeacher(coursePackage, admin, "Class details approved",
                    "Your changes to \"" + coursePackage.getTitle() + "\" are approved and live.");
            audit(admin, "ADMIN_CLASS_DETAILS_APPROVE", coursePackage, null, "Admin approved a change to class details.");
        } else {
            throw new ResponseStatusException(BAD_REQUEST, NOTHING_TO_REVIEW);
        }
        return queue("ALL");
    }

    @Transactional
    public ReviewViews.Queue requestClassChanges(SecurityUserDetails currentUser, Long classId, String reason) {
        User admin = requireAdmin(currentUser);
        CoursePackage coursePackage = requireClass(classId);
        if (coursePackage.getContentStatus() == ContentStatus.SUBMITTED) {
            List<Lesson> lessons = submittedLessons(coursePackage);
            coursePackage.setContentStatus(ContentStatus.REJECTED);
            coursePackage.setRejectionReason(reason);
            coursePackageRepository.save(coursePackage);
            events.record(coursePackage, null, admin, ClassContentEventType.CLASS_CHANGES_REQUESTED, reason, null);
            for (Lesson lesson : lessons) {
                lesson.setContentStatus(ContentStatus.REJECTED);
                lesson.setRejectionReason(reason);
                lessonRepository.save(lesson);
                events.record(coursePackage, lesson, admin, ClassContentEventType.LESSON_CHANGES_REQUESTED, reason, null);
            }
            notifyTeacher(coursePackage, admin, "Changes requested",
                    "An admin asked for changes to \"" + coursePackage.getTitle() + "\": " + reason);
            audit(admin, "ADMIN_CLASS_REQUEST_CHANGES", coursePackage, reason, "Admin requested changes to a new class.");
        } else if (coursePackage.getRevisionStatus() == ClassRevisionStatus.SUBMITTED) {
            coursePackage.setRevisionStatus(ClassRevisionStatus.REJECTED);
            coursePackage.setRevisionRejectionReason(reason);
            coursePackageRepository.save(coursePackage);
            events.record(coursePackage, null, admin, ClassContentEventType.CLASS_DETAILS_CHANGES_REQUESTED, reason, null);
            notifyTeacher(coursePackage, admin, "Changes requested",
                    "An admin asked for changes to the details of \"" + coursePackage.getTitle() + "\": " + reason);
            audit(admin, "ADMIN_CLASS_DETAILS_REQUEST_CHANGES", coursePackage, reason,
                    "Admin requested changes to class details.");
        } else {
            throw new ResponseStatusException(BAD_REQUEST, NOTHING_TO_REVIEW);
        }
        return queue("ALL");
    }

    @Transactional
    public ReviewViews.Queue approveLesson(SecurityUserDetails currentUser, Long lessonId) {
        User admin = requireAdmin(currentUser);
        Lesson lesson = requireLesson(lessonId);
        CoursePackage coursePackage = content.requireClassOfLesson(lesson);
        Lesson revision = content.revisionOf(lesson).orElse(null);
        if (revision != null && revision.getContentStatus() == ContentStatus.SUBMITTED) {
            List<TeacherClassViews.Change> changes = revisions.changes(revision);
            revisions.applyRevision(revision);
            events.record(coursePackage, lesson, admin, ClassContentEventType.REVISION_APPROVED, null, changes);
            notifyTeacher(coursePackage, admin, "Lesson changes approved",
                    "Your changes to \"" + lesson.getTitle() + "\" are approved and live for students.");
            audit(admin, "ADMIN_LESSON_REVISION_APPROVE", lesson, null, "Admin approved a lesson revision.");
        } else if (lesson.getContentStatus() == ContentStatus.SUBMITTED && coursePackage.getContentStatus() != ContentStatus.SUBMITTED) {
            revisions.publishNewLesson(lesson);
            events.record(coursePackage, lesson, admin, ClassContentEventType.LESSON_APPROVED);
            notifyTeacher(coursePackage, admin, "Lesson approved",
                    "\"" + lesson.getTitle() + "\" is approved and live for students.");
            audit(admin, "ADMIN_LESSON_APPROVE", lesson, null, "Admin approved a new lesson.");
        } else {
            throw new ResponseStatusException(BAD_REQUEST, NOTHING_TO_REVIEW);
        }
        return queue("ALL");
    }

    @Transactional
    public ReviewViews.Queue requestLessonChanges(SecurityUserDetails currentUser, Long lessonId, String reason) {
        User admin = requireAdmin(currentUser);
        Lesson lesson = requireLesson(lessonId);
        CoursePackage coursePackage = content.requireClassOfLesson(lesson);
        Lesson revision = content.revisionOf(lesson).orElse(null);
        if (revision != null && revision.getContentStatus() == ContentStatus.SUBMITTED) {
            revision.setContentStatus(ContentStatus.REJECTED);
            revision.setRejectionReason(reason);
            lessonRepository.save(revision);
            events.record(coursePackage, lesson, admin, ClassContentEventType.REVISION_CHANGES_REQUESTED, reason, null);
            audit(admin, "ADMIN_LESSON_REVISION_REQUEST_CHANGES", lesson, reason,
                    "Admin requested changes to a lesson revision.");
        } else if (lesson.getContentStatus() == ContentStatus.SUBMITTED && coursePackage.getContentStatus() != ContentStatus.SUBMITTED) {
            lesson.setContentStatus(ContentStatus.REJECTED);
            lesson.setRejectionReason(reason);
            lessonRepository.save(lesson);
            events.record(coursePackage, lesson, admin, ClassContentEventType.LESSON_CHANGES_REQUESTED, reason, null);
            audit(admin, "ADMIN_LESSON_REQUEST_CHANGES", lesson, reason, "Admin requested changes to a new lesson.");
        } else {
            throw new ResponseStatusException(BAD_REQUEST, NOTHING_TO_REVIEW);
        }
        notifyTeacher(coursePackage, admin, "Changes requested",
                "An admin asked for changes to \"" + lesson.getTitle() + "\": " + reason);
        return queue("ALL");
    }

    // ----- previews -----

    private ReviewViews.ClassPreview classPreview(CoursePackage coursePackage, boolean pending, List<Lesson> lessonsInReview) {
        List<LearningModule> units = content.units(coursePackage, false);
        Map<Long, List<Lesson>> lessonsByUnit = content.lessonsByUnit(units);
        int lessonCount = (int) lessonsByUnit.values().stream().flatMap(List::stream)
                .filter(lesson -> lesson.getContentStatus() != ContentStatus.ARCHIVED)
                .count();
        return new ReviewViews.ClassPreview(
                pending ? coursePackage.getPendingTitle() : coursePackage.getTitle(),
                pending ? coursePackage.getPendingDescription() : coursePackage.getDescription(),
                TeacherClassService.levelName(pending ? coursePackage.getPendingLevel() : coursePackage.getLevel()),
                TeacherClassService.topicName(pending ? coursePackage.getPendingCategory() : coursePackage.getCategory()),
                pending ? coursePackage.getPendingLearningType() : coursePackage.getLearningType(),
                teacherName(coursePackage),
                units.size(),
                pending ? lessonCount : lessonsInReview.size(),
                lessonsInReview.stream()
                        .map(lesson -> new ReviewViews.LessonTitle(lesson.getId(), lesson.getTitle(), lesson.getModule().getTitle()))
                        .toList());
    }

    /** {@code shown} is the lesson itself, or its revision when the revision is reviewed. */
    private ReviewViews.LessonPreview lessonPreview(CoursePackage coursePackage, Lesson lesson, Lesson shown) {
        RecordedLessonVideo video = content.video(shown.getId()).orElse(null);
        List<ReviewViews.ActivityPreview> activities = content.activities(shown.getId()).stream()
                .filter(ClassContentSupport::isActive)
                .map(activity -> new ReviewViews.ActivityPreview(
                        ClassContentSupport.typeOf(activity).name(),
                        activity.getTitle(),
                        stage(((PracticeActivityContent) activity).getLearningStage()),
                        shown != lesson && activity.getRevisionSourceId() == null))
                .toList();
        List<Quiz> quizzes = content.quizzes(shown.getId()).stream().filter(ClassContentSupport::isActive).toList();
        Map<Long, List<QuizQuestion>> questions = content.questionsByQuiz(quizzes.stream().map(Quiz::getId).toList());
        List<ReviewViews.QuizPreview> quizPreviews = quizzes.stream()
                .map(quiz -> {
                    List<QuizQuestion> quizQuestions = questions.getOrDefault(quiz.getId(), List.of());
                    return new ReviewViews.QuizPreview(
                            quiz.getTitle(),
                            stage(quiz.getLearningStage()),
                            quizQuestions.size(),
                            quizQuestions.stream().allMatch(question -> question.getExplanation() != null),
                            shown != lesson && quiz.getRevisionSourceId() == null);
                })
                .toList();
        return new ReviewViews.LessonPreview(
                lesson.getId(),
                shown.getTitle(),
                coursePackage.getTitle(),
                lesson.getModule().getTitle(),
                shown.getSummary(),
                content.keyPhrases(shown.getId()).stream()
                        .map(phrase -> new TeacherClassViews.KeyPhrase(phrase.getChinese(), phrase.getPinyin(), phrase.getMeaning()))
                        .toList(),
                video == null ? null : video.getTitle(),
                video == null ? null : video.getDurationSeconds(),
                activities,
                quizPreviews);
    }

    // ----- helpers -----

    private List<Lesson> submittedLessons(CoursePackage coursePackage) {
        return content.lessonsInOrder(coursePackage, false).stream()
                .filter(lesson -> lesson.getContentStatus() == ContentStatus.SUBMITTED)
                .toList();
    }

    CoursePackage requireClass(Long classId) {
        return coursePackageRepository.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ClassContentSupport.CLASS_NOT_FOUND));
    }

    /** A lesson itself, never a revision copy. */
    Lesson requireLesson(Long lessonId) {
        return lessonRepository.findById(lessonId)
                .filter(lesson -> lesson.getRevisionOf() == null)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ClassContentSupport.LESSON_NOT_FOUND));
    }

    User requireAdmin(SecurityUserDetails currentUser) {
        return userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User could not be found."));
    }

    void notifyTeacher(CoursePackage coursePackage, User admin, String title, String message) {
        notifyTeacher(coursePackage, admin, NotificationType.CONTENT_REVIEW, title, message);
    }

    void notifyTeacher(CoursePackage coursePackage, User admin, NotificationType type, String title, String message) {
        notificationService.safeCreateNotification(
                coursePackage.getTeacher().getUser(),
                type,
                title,
                message,
                "CLASS",
                coursePackage.getId(),
                TEACHER_CLASSES_PATH + coursePackage.getId(),
                admin);
    }

    /** An audit entry about a class, named by its title, with the reason the admin gave (if any). */
    void audit(User admin, String action, CoursePackage coursePackage, String reason, String description) {
        auditLogService.record(admin, action,
                new AuditLogService.AuditTarget("CoursePackage", coursePackage.getId(), coursePackage.getTitle()), reason,
                description);
    }

    /** An audit entry about a lesson, named by its title, with the reason the admin gave (if any). */
    void audit(User admin, String action, Lesson lesson, String reason, String description) {
        auditLogService.record(admin, action, new AuditLogService.AuditTarget("Lesson", lesson.getId(), lesson.getTitle()),
                reason, description);
    }

    static String teacherName(CoursePackage coursePackage) {
        return coursePackage.getTeacher().getUser().getDisplayName();
    }

    private static String stage(LearningStage stage) {
        return stage == null ? null : stage.name();
    }
}
