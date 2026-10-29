package com.example.aichinese.service.classes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.classes.ClassStudentViews;
import com.example.aichinese.dto.classes.LiveSessionViews;
import com.example.aichinese.dto.classes.TeacherClassViews;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ClassRevisionStatus;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.security.SecurityUserDetails;

/**
 * The approved teacher's dashboard: a To do list (review outcomes that need the teacher first, then items in review
 * and the next live session), the number strip, students needing support and the upcoming live sessions.
 */
@Service
public class TeachingDashboardService {

    private static final int SUPPORT_LIST_SIZE = 5;
    private static final int SESSION_LIST_SIZE = 4;

    private final ClassAccessService access;
    private final ClassContentSupport content;
    private final ClassEventService events;
    private final ClassLiveSessionService liveSessions;
    private final TeacherClassStudentsService students;
    private final CoursePackageRepository coursePackageRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    public TeachingDashboardService(
            ClassAccessService access,
            ClassContentSupport content,
            ClassEventService events,
            ClassLiveSessionService liveSessions,
            TeacherClassStudentsService students,
            CoursePackageRepository coursePackageRepository,
            QuizAttemptRepository quizAttemptRepository) {
        this.access = access;
        this.content = content;
        this.events = events;
        this.liveSessions = liveSessions;
        this.students = students;
        this.coursePackageRepository = coursePackageRepository;
        this.quizAttemptRepository = quizAttemptRepository;
    }

    @Transactional(readOnly = true)
    public ClassStudentViews.Dashboard dashboard(SecurityUserDetails currentUser) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        List<CoursePackage> classes = coursePackageRepository.findByTeacherUserIdOrderByUpdatedAtDesc(currentUser.getUserId());
        Map<Long, List<LearningModule>> units = content.unitsByClass(classes, false);
        Map<Long, List<Lesson>> lessonsByUnit = content.lessonsByUnit(units.values().stream().flatMap(List::stream).toList());
        List<Lesson> lessons = lessonsByUnit.values().stream().flatMap(List::stream).toList();
        Map<Long, Lesson> revisions = content.revisionsByLesson(lessons);

        List<ClassStudentViews.TodoItem> needsYou = new ArrayList<>();
        List<ClassStudentViews.TodoItem> watching = new ArrayList<>();
        int inReview = 0;
        int publishedLessons = 0;
        for (CoursePackage coursePackage : classes) {
            ContentStatus classStatus = coursePackage.getContentStatus();
            List<TeacherClassViews.Event> history = events.classHistory(coursePackage.getId());
            if (classStatus == ContentStatus.REJECTED) {
                TeacherClassViews.Event note = latest(history, null, "CLASS_CHANGES_REQUESTED");
                needsYou.add(todo("CLASS_CHANGES_REQUESTED", true, coursePackage, null, coursePackage.getTitle(),
                        coursePackage.getRejectionReason(), note));
            } else if (classStatus == ContentStatus.SUBMITTED) {
                inReview++;
                String firstLesson = units.getOrDefault(coursePackage.getId(), List.of()).stream()
                        .flatMap(unit -> lessonsByUnit.getOrDefault(unit.getId(), List.of()).stream())
                        .filter(lesson -> lesson.getContentStatus() == ContentStatus.SUBMITTED)
                        .map(Lesson::getTitle)
                        .findFirst()
                        .orElse(null);
                watching.add(new ClassStudentViews.TodoItem("CLASS_IN_REVIEW", false, coursePackage.getId(), null, null,
                        coursePackage.getTitle(), null, null, coursePackage.getSubmittedAt(), firstLesson));
            }
            if (coursePackage.getRevisionStatus() == ClassRevisionStatus.REJECTED) {
                TeacherClassViews.Event note = latest(history, null, "CLASS_DETAILS_CHANGES_REQUESTED");
                needsYou.add(todo("DETAILS_CHANGES_REQUESTED", true, coursePackage, null, coursePackage.getTitle(),
                        coursePackage.getRevisionRejectionReason(), note));
            } else if (coursePackage.getRevisionStatus() == ClassRevisionStatus.SUBMITTED) {
                inReview++;
                watching.add(new ClassStudentViews.TodoItem("DETAILS_IN_REVIEW", false, coursePackage.getId(), null, null,
                        coursePackage.getTitle(), null, null, coursePackage.getRevisionSubmittedAt(), null));
            }
            for (LearningModule unit : units.getOrDefault(coursePackage.getId(), List.of())) {
                for (Lesson lesson : lessonsByUnit.getOrDefault(unit.getId(), List.of())) {
                    Lesson revision = revisions.get(lesson.getId());
                    if (lesson.getContentStatus() == ContentStatus.PUBLISHED && classStatus == ContentStatus.PUBLISHED) {
                        publishedLessons++;
                    }
                    if (lesson.getContentStatus() == ContentStatus.REJECTED && classStatus != ContentStatus.REJECTED) {
                        needsYou.add(todo("LESSON_CHANGES_REQUESTED", true, coursePackage, lesson, lesson.getTitle(),
                                lesson.getRejectionReason(), latest(history, lesson.getId(), "LESSON_CHANGES_REQUESTED")));
                    } else if (lesson.getContentStatus() == ContentStatus.SUBMITTED && classStatus != ContentStatus.SUBMITTED) {
                        inReview++;
                        watching.add(new ClassStudentViews.TodoItem("LESSON_IN_REVIEW", false, coursePackage.getId(),
                                lesson.getId(), null, lesson.getTitle(), null, null, lesson.getSubmittedAt(), null));
                    }
                    if (revision != null && revision.getContentStatus() == ContentStatus.REJECTED) {
                        needsYou.add(todo("REVISION_CHANGES_REQUESTED", true, coursePackage, lesson, revision.getTitle(),
                                revision.getRejectionReason(), latest(history, lesson.getId(), "REVISION_CHANGES_REQUESTED")));
                    } else if (revision != null && revision.getContentStatus() == ContentStatus.SUBMITTED) {
                        inReview++;
                        watching.add(new ClassStudentViews.TodoItem("REVISION_IN_REVIEW", false, coursePackage.getId(),
                                lesson.getId(), null, revision.getTitle(), null, null, revision.getSubmittedAt(), null));
                    }
                }
            }
        }
        List<LiveSessionViews.TeacherSession> upcoming = liveSessions.teacherSessions(currentUser).upcoming().stream()
                .filter(session -> !"CANCELLED".equals(session.status()))
                .toList();
        upcoming.stream().findFirst().ifPresent(next -> watching.add(new ClassStudentViews.TodoItem(
                "NEXT_LIVE_SESSION", false, next.classId(), next.lessonId(), next.id(), next.title(), null, null,
                next.localStart(), null)));
        List<ClassStudentViews.TodoItem> todo = new ArrayList<>(needsYou);
        todo.addAll(watching);

        List<ClassStudentViews.RosterRow> roster = students.rosterRows(teacher);
        List<Long> lessonIds = lessons.stream().map(Lesson::getId).toList();
        List<QuizAttempt> attempts = lessonIds.isEmpty() ? List.of() : quizAttemptRepository
                .findByStudentIdInAndQuizLessonIdInAndSubmittedTrueOrderByCompletedAtDescIdDesc(
                        roster.stream().map(ClassStudentViews.RosterRow::studentId).toList(), lessonIds);
        Integer quizAverage = ClassProgressService.average(attempts.stream().map(ClassProgressService::attemptPercent).toList());
        return new ClassStudentViews.Dashboard(
                todo,
                roster.size(),
                (int) roster.stream().filter(ClassStudentViews.RosterRow::needsSupport).count(),
                (int) classes.stream().filter(coursePackage -> coursePackage.getContentStatus() == ContentStatus.PUBLISHED).count(),
                publishedLessons,
                inReview,
                upcoming.size(),
                upcoming.stream().mapToInt(LiveSessionViews.TeacherSession::bookedCount).sum(),
                quizAverage,
                roster.stream()
                        .filter(ClassStudentViews.RosterRow::needsSupport)
                        .sorted(Comparator.comparingInt(ClassStudentViews.RosterRow::progressPercent))
                        .limit(SUPPORT_LIST_SIZE)
                        .toList(),
                upcoming.stream().limit(SESSION_LIST_SIZE).toList());
    }

    private static ClassStudentViews.TodoItem todo(
            String kind,
            boolean needsYou,
            CoursePackage coursePackage,
            Lesson lesson,
            String title,
            String note,
            TeacherClassViews.Event noteEvent) {
        return new ClassStudentViews.TodoItem(kind, needsYou, coursePackage.getId(), lesson == null ? null : lesson.getId(),
                null, title, note, noteEvent == null ? null : noteEvent.actorName(),
                noteEvent == null ? null : noteEvent.createdAt(), null);
    }

    private static TeacherClassViews.Event latest(List<TeacherClassViews.Event> history, Long lessonId, String type) {
        return history.stream()
                .filter(event -> Objects.equals(event.lessonId(), lessonId) && event.type().equals(type))
                .findFirst()
                .orElse(null);
    }
}
