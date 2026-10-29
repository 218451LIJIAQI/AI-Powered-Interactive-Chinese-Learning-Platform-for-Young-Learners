package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.classes.ReviewViews;
import com.example.aichinese.dto.classes.TeacherClassViews;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.security.SecurityUserDetails;

/**
 * The admin Teacher classes list: every class and lesson teachers made, with search, type, status and teacher
 * filters, details with the history, and archive (the teacher is told) or restore.
 */
@Service
public class AdminClassContentService {

    private static final List<String> STATUS_FILTERS = List.of(
            "PUBLISHED", "SUBMITTED", "DRAFT", "REJECTED", "ARCHIVED", "REVISION_SUBMITTED", "REVISION_REJECTED");

    private final ClassContentSupport content;
    private final ClassEventService events;
    private final ClassReviewService reviews;
    private final TeacherClassService teacherClasses;
    private final TeacherLessonEditorService lessonEditor;
    private final CoursePackageRepository coursePackageRepository;
    private final ClassEnrolmentRepository enrolmentRepository;

    public AdminClassContentService(
            ClassContentSupport content,
            ClassEventService events,
            ClassReviewService reviews,
            TeacherClassService teacherClasses,
            TeacherLessonEditorService lessonEditor,
            CoursePackageRepository coursePackageRepository,
            ClassEnrolmentRepository enrolmentRepository) {
        this.content = content;
        this.events = events;
        this.reviews = reviews;
        this.teacherClasses = teacherClasses;
        this.lessonEditor = lessonEditor;
        this.coursePackageRepository = coursePackageRepository;
        this.enrolmentRepository = enrolmentRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewViews.ContentRow> list(
            String keyword,
            String type,
            String status,
            Long teacherId,
            int page,
            int size) {
        String typeFilter = normalize(type);
        if (typeFilter != null && !typeFilter.equals("CLASS") && !typeFilter.equals("LESSON")) {
            throw new ResponseStatusException(BAD_REQUEST, "Type must be CLASS or LESSON.");
        }
        String statusFilter = normalize(status);
        if (statusFilter != null && !STATUS_FILTERS.contains(statusFilter)) {
            throw new ResponseStatusException(BAD_REQUEST, "Status filter is not supported.");
        }
        String search = keyword == null || keyword.isBlank() ? null : keyword.strip().toLowerCase(Locale.ROOT);
        List<ReviewViews.ContentRow> rows = allRows(teacherId).stream()
                .filter(row -> typeFilter == null || row.type().equals(typeFilter))
                .filter(row -> matchesStatus(row, statusFilter))
                .filter(row -> search == null || contains(row.title(), search) || contains(row.classTitle(), search))
                .sorted(Comparator.comparing(ReviewViews.ContentRow::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return PageResponse.fromList(rows, page, size, slice -> slice);
    }

    /** Teachers who have made at least one class, for the teacher filter. */
    @Transactional(readOnly = true)
    public List<ReviewViews.TeacherOption> teachers() {
        return coursePackageRepository.findAll().stream()
                .map(CoursePackage::getTeacher)
                .distinct()
                .map(teacher -> new ReviewViews.TeacherOption(teacher.getId(), teacher.getUser().getDisplayName()))
                .sorted(Comparator.comparing(ReviewViews.TeacherOption::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewViews.ContentDetail classDetail(Long classId) {
        CoursePackage coursePackage = reviews.requireClass(classId);
        List<LearningModule> units = content.units(coursePackage, false);
        int lessonCount = (int) content.lessonsByUnit(units).values().stream().flatMap(List::stream)
                .filter(lesson -> lesson.getContentStatus() != ContentStatus.ARCHIVED)
                .count();
        List<TeacherClassViews.Event> history = events.classOnlyHistory(classId);
        String note = coursePackage.getRevisionRejectionReason() != null
                ? coursePackage.getRevisionRejectionReason()
                : coursePackage.getContentStatus() == ContentStatus.REJECTED ? coursePackage.getRejectionReason() : null;
        TeacherClassViews.Event noteEvent = note == null ? null : ClassEventService.latestChangesRequested(history);
        return new ReviewViews.ContentDetail(
                classRow(coursePackage),
                note,
                noteEvent == null ? null : noteEvent.actorName(),
                noteEvent == null ? null : noteEvent.createdAt(),
                null,
                units.size(),
                lessonCount,
                0,
                0,
                (int) enrolmentRepository.countByCoursePackageId(classId),
                history,
                canArchive(coursePackage.getContentStatus()),
                coursePackage.getContentStatus() == ContentStatus.ARCHIVED);
    }

    @Transactional(readOnly = true)
    public ReviewViews.ContentDetail lessonDetail(Long lessonId) {
        Lesson lesson = reviews.requireLesson(lessonId);
        CoursePackage coursePackage = content.requireClassOfLesson(lesson);
        Lesson revision = content.revisionOf(lesson).orElse(null);
        List<TeacherClassViews.Event> history = events.lessonHistory(lessonId);
        String note = revision != null ? revision.getRejectionReason()
                : lesson.getContentStatus() == ContentStatus.REJECTED ? lesson.getRejectionReason() : null;
        TeacherClassViews.Event noteEvent = note == null ? null : ClassEventService.latestChangesRequested(history);
        int activityCount = (int) content.activities(lessonId).stream().filter(ClassContentSupport::isActive).count();
        List<Quiz> quizzes = content.quizzes(lessonId).stream().filter(ClassContentSupport::isActive).toList();
        int questionCount = content.questionsByQuiz(quizzes.stream().map(Quiz::getId).toList()).values().stream()
                .mapToInt(List::size).sum();
        return new ReviewViews.ContentDetail(
                lessonRow(lesson, coursePackage, revision),
                note,
                noteEvent == null ? null : noteEvent.actorName(),
                noteEvent == null ? null : noteEvent.createdAt(),
                lesson.getModule().getTitle(),
                0,
                0,
                activityCount,
                questionCount,
                (int) enrolmentRepository.countByCoursePackageId(coursePackage.getId()),
                history,
                canArchive(lesson.getContentStatus()),
                lesson.getContentStatus() == ContentStatus.ARCHIVED);
    }

    // ----- archive and restore -----

    @Transactional
    public ReviewViews.ContentDetail archiveClass(SecurityUserDetails currentUser, Long classId) {
        User admin = reviews.requireAdmin(currentUser);
        CoursePackage coursePackage = reviews.requireClass(classId);
        teacherClasses.archive(coursePackage, admin);
        reviews.notifyTeacher(coursePackage, admin, NotificationType.MODERATION_ACTION, "Class archived by an admin",
                "An admin archived \"" + coursePackage.getTitle() + "\". New students can't join it; students who joined keep their progress.");
        reviews.audit(admin, "ADMIN_CLASS_ARCHIVE", coursePackage, null, "Admin archived a class.");
        return classDetail(classId);
    }

    @Transactional
    public ReviewViews.ContentDetail restoreClass(SecurityUserDetails currentUser, Long classId) {
        User admin = reviews.requireAdmin(currentUser);
        CoursePackage coursePackage = reviews.requireClass(classId);
        teacherClasses.restore(coursePackage, admin);
        reviews.notifyTeacher(coursePackage, admin, NotificationType.MODERATION_ACTION, "Class restored by an admin", "An admin restored \"" + coursePackage.getTitle() + "\".");
        reviews.audit(admin, "ADMIN_CLASS_RESTORE", coursePackage, null, "Admin restored a class.");
        return classDetail(classId);
    }

    @Transactional
    public ReviewViews.ContentDetail archiveLesson(SecurityUserDetails currentUser, Long lessonId) {
        User admin = reviews.requireAdmin(currentUser);
        Lesson lesson = reviews.requireLesson(lessonId);
        lessonEditor.archiveLesson(lesson, admin);
        reviews.notifyTeacher(content.requireClassOfLesson(lesson), admin, NotificationType.MODERATION_ACTION,
                "Lesson archived by an admin",
                "An admin archived \"" + lesson.getTitle() + "\". Students can't open it any more; their progress is kept.");
        reviews.audit(admin, "ADMIN_LESSON_ARCHIVE", lesson, null, "Admin archived a lesson.");
        return lessonDetail(lessonId);
    }

    @Transactional
    public ReviewViews.ContentDetail restoreLesson(SecurityUserDetails currentUser, Long lessonId) {
        User admin = reviews.requireAdmin(currentUser);
        Lesson lesson = reviews.requireLesson(lessonId);
        lessonEditor.restoreLesson(lesson, admin);
        reviews.notifyTeacher(content.requireClassOfLesson(lesson), admin, NotificationType.MODERATION_ACTION,
                "Lesson restored by an admin", "An admin restored \"" + lesson.getTitle() + "\".");
        reviews.audit(admin, "ADMIN_LESSON_RESTORE", lesson, null, "Admin restored a lesson.");
        return lessonDetail(lessonId);
    }

    // ----- rows -----

    /** Every class (of the teacher, when given) followed by its lessons. */
    private List<ReviewViews.ContentRow> allRows(Long teacherId) {
        List<CoursePackage> classes = coursePackageRepository.findAll().stream()
                .filter(coursePackage -> teacherId == null || teacherId.equals(coursePackage.getTeacher().getId()))
                .toList();
        Map<Long, List<LearningModule>> units = content.unitsByClass(classes, true);
        Map<Long, List<Lesson>> lessonsByUnit = content.lessonsByUnit(units.values().stream().flatMap(List::stream).toList());
        Map<Long, Lesson> revisionByLesson = content.revisionsByLesson(lessonsByUnit.values().stream().flatMap(List::stream).toList());
        List<ReviewViews.ContentRow> rows = new ArrayList<>();
        for (CoursePackage coursePackage : classes) {
            rows.add(classRow(coursePackage));
            for (LearningModule unit : units.getOrDefault(coursePackage.getId(), List.of())) {
                for (Lesson lesson : lessonsByUnit.getOrDefault(unit.getId(), List.of())) {
                    rows.add(lessonRow(lesson, coursePackage, revisionByLesson.get(lesson.getId())));
                }
            }
        }
        return rows;
    }

    private static ReviewViews.ContentRow classRow(CoursePackage coursePackage) {
        return new ReviewViews.ContentRow(
                "CLASS",
                coursePackage.getId(),
                coursePackage.getTitle(),
                null,
                coursePackage.getTeacher().getUser().getDisplayName(),
                coursePackage.getContentStatus().name(),
                coursePackage.getRevisionStatus() == null ? null : coursePackage.getRevisionStatus().name(),
                coursePackage.getUpdatedAt());
    }

    private static ReviewViews.ContentRow lessonRow(Lesson lesson, CoursePackage coursePackage, Lesson revision) {
        return new ReviewViews.ContentRow(
                "LESSON",
                lesson.getId(),
                lesson.getTitle(),
                coursePackage.getTitle(),
                coursePackage.getTeacher().getUser().getDisplayName(),
                lesson.getContentStatus().name(),
                revision == null ? null : TeacherClassService.revisionStatusName(revision.getContentStatus()),
                ClassContentSupport.latest(lesson.getUpdatedAt(), revision == null ? null : revision.getUpdatedAt()));
    }

    private static boolean matchesStatus(ReviewViews.ContentRow row, String filter) {
        if (filter == null) {
            return true;
        }
        return switch (filter) {
            case "REVISION_SUBMITTED" -> "SUBMITTED".equals(row.revisionStatus());
            case "REVISION_REJECTED" -> "REJECTED".equals(row.revisionStatus());
            default -> filter.equals(row.status());
        };
    }

    private static boolean canArchive(ContentStatus status) {
        return status != ContentStatus.ARCHIVED && status != ContentStatus.SUBMITTED;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() || value.equalsIgnoreCase("ALL") ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    private static boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }
}
