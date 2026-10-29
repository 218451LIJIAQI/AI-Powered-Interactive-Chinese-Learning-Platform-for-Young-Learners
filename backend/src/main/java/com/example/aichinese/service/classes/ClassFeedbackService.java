package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.ClassStudentViews;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherFeedback;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherFeedbackRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ContentSafetyService;
import com.example.aichinese.service.LessonMasteryCalculator;
import com.example.aichinese.service.NotificationService;

/**
 * Feedback a teacher writes to a student of one of their classes, about a quiz attempt, a lesson or in general. The
 * student reads it in the class Feedback tab (and is notified); the teacher can edit it from Feedback history.
 */
@Service
public class ClassFeedbackService {

    private static final Set<String> TYPES = Set.of("ENCOURAGING", "LESSON", "WEAK_AREA", "CORRECTIVE", "PROGRESS");
    private static final String FEEDBACK_NOT_FOUND = "Feedback was not found.";
    private static final String STUDENT_NOT_IN_CLASS = "This student has not joined the class.";

    private final ClassAccessService access;
    private final ClassContentSupport content;
    private final TeacherFeedbackRepository feedbackRepository;
    private final ClassEnrolmentRepository enrolmentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LessonRepository lessonRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final ContentSafetyService safety;
    private final NotificationService notificationService;

    public ClassFeedbackService(
            ClassAccessService access,
            ClassContentSupport content,
            TeacherFeedbackRepository feedbackRepository,
            ClassEnrolmentRepository enrolmentRepository,
            StudentProfileRepository studentProfileRepository,
            LessonRepository lessonRepository,
            QuizAttemptRepository quizAttemptRepository,
            ContentSafetyService safety,
            NotificationService notificationService) {
        this.access = access;
        this.content = content;
        this.feedbackRepository = feedbackRepository;
        this.enrolmentRepository = enrolmentRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.lessonRepository = lessonRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.safety = safety;
        this.notificationService = notificationService;
    }

    @Transactional
    public ClassStudentViews.FeedbackRow create(SecurityUserDetails currentUser, ClassRequests.Feedback request) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        CoursePackage coursePackage = access.requireOwnedClass(currentUser, request.classId());
        StudentProfile student = studentProfileRepository.findById(request.studentId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, STUDENT_NOT_IN_CLASS));
        if (!enrolmentRepository.existsByStudentIdAndCoursePackageId(student.getId(), coursePackage.getId())) {
            throw new ResponseStatusException(NOT_FOUND, STUDENT_NOT_IN_CLASS);
        }
        QuizAttempt attempt = null;
        Lesson lesson = null;
        if (request.quizAttemptId() != null) {
            attempt = quizAttemptRepository.findById(request.quizAttemptId())
                    .filter(found -> found.isSubmitted() && found.getStudent().getId().equals(student.getId()))
                    .filter(found -> inClass(found.getQuiz().getLesson(), coursePackage))
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Choose a quiz attempt of this student in this class."));
            lesson = attempt.getQuiz().getLesson();
        } else if (request.lessonId() != null) {
            lesson = lessonRepository.findById(request.lessonId())
                    .filter(found -> found.getRevisionOf() == null && inClass(found, coursePackage))
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Choose a lesson of this class."));
        }
        TeacherFeedback feedback = new TeacherFeedback();
        feedback.setTeacher(teacher);
        feedback.setStudent(student);
        feedback.setCoursePackage(coursePackage);
        feedback.setQuizAttempt(attempt);
        feedback.setLesson(lesson);
        feedback.setFeedbackType(type(request.type()));
        feedback.setFeedbackMessage(safety.requireSafeText(request.message(), 2000, "Feedback message is required."));
        TeacherFeedback saved = feedbackRepository.save(feedback);
        notificationService.safeCreateNotification(
                student.getUser(),
                NotificationType.TEACHER_FEEDBACK,
                "New feedback from " + teacher.getUser().getDisplayName(),
                "Your teacher wrote to you about " + coursePackage.getTitle() + ".",
                "TEACHER_FEEDBACK",
                saved.getId(),
                "/student/classes/" + coursePackage.getId() + "/feedback",
                teacher.getUser());
        return row(saved);
    }

    @Transactional
    public ClassStudentViews.FeedbackRow update(SecurityUserDetails currentUser, Long feedbackId, ClassRequests.FeedbackUpdate request) {
        access.requireTeacher(currentUser);
        TeacherFeedback feedback = feedbackRepository.findByIdAndTeacherUserId(feedbackId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, FEEDBACK_NOT_FOUND));
        feedback.setFeedbackType(type(request.type()));
        feedback.setFeedbackMessage(safety.requireSafeText(request.message(), 2000, "Feedback message is required."));
        feedback.setEditedAt(LocalDateTime.now());
        return row(feedbackRepository.save(feedback));
    }

    /** Everything the teacher sent to students of their classes, newest first. */
    @Transactional(readOnly = true)
    public List<ClassStudentViews.FeedbackRow> history(SecurityUserDetails currentUser, Long studentId, String type) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        String typeFilter = type == null || type.isBlank() || type.equalsIgnoreCase("ALL") ? null : type(type);
        return feedbackRepository.findByTeacherIdOrderByCreatedAtDescIdDesc(teacher.getId()).stream()
                .filter(feedback -> feedback.getCoursePackage() != null)
                .filter(feedback -> studentId == null || feedback.getStudent().getId().equals(studentId))
                .filter(feedback -> typeFilter == null || typeFilter.equals(feedback.getFeedbackType()))
                .map(ClassFeedbackService::row)
                .toList();
    }

    static ClassStudentViews.FeedbackRow row(TeacherFeedback feedback) {
        QuizAttempt attempt = feedback.getQuizAttempt();
        Lesson lesson = feedback.getLesson() != null ? feedback.getLesson() : attempt != null ? attempt.getQuiz().getLesson() : null;
        CoursePackage coursePackage = feedback.getCoursePackage();
        return new ClassStudentViews.FeedbackRow(
                feedback.getId(),
                feedback.getStudent().getUser().getDisplayName(),
                coursePackage == null ? null : coursePackage.getTitle(),
                feedback.getFeedbackType(),
                about(feedback),
                lesson == null ? null : lesson.getTitle(),
                attempt == null ? null : LessonMasteryCalculator.attemptPercentage(attempt),
                feedback.getFeedbackMessage(),
                feedback.getCreatedAt(),
                feedback.getEditedAt());
    }

    /** QUIZ_ATTEMPT, LESSON or GENERAL: what the feedback is about. */
    static String about(TeacherFeedback feedback) {
        if (feedback.getQuizAttempt() != null) {
            return "QUIZ_ATTEMPT";
        }
        return feedback.getLesson() != null ? "LESSON" : "GENERAL";
    }

    private boolean inClass(Lesson lesson, CoursePackage coursePackage) {
        return lesson != null && lesson.getModule() != null && content.classOfUnit(lesson.getModule().getId())
                .map(owner -> owner.getId().equals(coursePackage.getId()))
                .orElse(false);
    }

    private static String type(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Feedback type must be ENCOURAGING, LESSON, WEAK_AREA, CORRECTIVE or PROGRESS.");
        }
        return normalized;
    }
}
