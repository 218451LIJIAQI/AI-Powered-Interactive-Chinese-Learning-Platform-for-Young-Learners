package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;

/**
 * Read-side access checks used by student-facing services. Verifies the
 * caller has a {@link StudentProfile}, that the lesson / quiz the student
 * is trying to reach is {@link ContentStatus#PUBLISHED}, and that any paid
 * content is actually unlocked through an entitlement.
 *
 * <p>The interactive activity types (flashcard, matching, listening,
 * tracing, drag-drop, story) used to have their own {@code requirePublished*}
 * lookups here. They have moved to {@link StudentActivityLibraryService}
 * because activities are now platform-managed and decoupled from lessons.
 */
@Service
public class StudentLearningAccessService {

    private final StudentProfileRepository studentProfileRepository;
    private final LessonRepository lessonRepository;
    private final QuizRepository quizRepository;
    private final EntitlementService entitlementService;

    public StudentLearningAccessService(
            StudentProfileRepository studentProfileRepository,
            LessonRepository lessonRepository,
            QuizRepository quizRepository,
            EntitlementService entitlementService) {
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "studentProfileRepository");
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository");
        this.quizRepository = Objects.requireNonNull(quizRepository, "quizRepository");
        this.entitlementService = Objects.requireNonNull(entitlementService, "entitlementService");
    }

    @Transactional(readOnly = true)
    public StudentProfile requireStudent(SecurityUserDetails currentUser) {
        return studentProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student profile could not be found."));
    }

    @Transactional
    public StudentProfile requireLockedStudent(SecurityUserDetails currentUser) {
        return studentProfileRepository.findLockedByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student profile could not be found."));
    }

    @Transactional(readOnly = true)
    public Lesson requirePublishedLesson(Long lessonId) {
        return lessonRepository.findByIdAndContentStatus(lessonId, ContentStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Published lesson was not found."));
    }

    @Transactional(readOnly = true)
    public Quiz requirePublishedQuiz(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Quiz was not found."));
        Lesson lesson = quiz.getLesson();
        if (lesson == null || lesson.getContentStatus() != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(NOT_FOUND, "Quiz was not found.");
        }
        if (quiz.getContentStatus() != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(NOT_FOUND, "Quiz was not found.");
        }
        return quiz;
    }

    private boolean isLocked(Lesson lesson) {
        return lesson.getAccessType() == AccessType.PAID;
    }

    @Transactional(readOnly = true)
    public boolean hasLessonAccess(StudentProfile student, Lesson lesson) {
        if (!isLocked(lesson)) {
            return true;
        }

        boolean directLessonAccess = entitlementService.hasAccessToItem(student.getId(), "LESSON", lesson.getId());
        boolean moduleAccess = lesson.getModule() != null
                && entitlementService.hasAccessToItem(student.getId(), "MODULE", lesson.getModule().getId());
        return directLessonAccess || moduleAccess;
    }

    public void ensureLessonAccess(StudentProfile student, Lesson lesson) {
        if (!hasLessonAccess(student, lesson)) {
            throw new ResponseStatusException(FORBIDDEN,
                    "Paid content is locked until payment is completed.");
        }
    }
}
