package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Objects;
import java.util.Optional;

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

@Service
public class StudentLearningAccessService {

    private static final String STUDENT_PROFILE_NOT_FOUND = "Student profile could not be found.";
    private static final String QUIZ_NOT_FOUND = "Quiz was not found.";
    private static final String ITEM_TYPE_LESSON = "LESSON";
    private static final String ITEM_TYPE_MODULE = "MODULE";

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
        return requireStudentProfile(studentProfileRepository.findByUserId(currentUser.getUserId()));
    }

    @Transactional
    public StudentProfile requireLockedStudent(SecurityUserDetails currentUser) {
        return requireStudentProfile(studentProfileRepository.findLockedByUserId(currentUser.getUserId()));
    }

    @Transactional(readOnly = true)
    public Lesson requirePublishedLesson(Long lessonId) {
        return lessonRepository.findByIdAndContentStatus(lessonId, ContentStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Published lesson was not found."));
    }

    @Transactional(readOnly = true)
    public Quiz requirePublishedQuiz(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, QUIZ_NOT_FOUND));
        if (!isPublishedQuiz(quiz)) {
            throw new ResponseStatusException(NOT_FOUND, QUIZ_NOT_FOUND);
        }
        return quiz;
    }

    @Transactional(readOnly = true)
    public boolean hasLessonAccess(StudentProfile student, Lesson lesson) {
        if (lesson.getAccessType() != AccessType.PAID) {
            return true;
        }

        boolean directLessonAccess = entitlementService.hasAccessToItem(student.getId(), ITEM_TYPE_LESSON, lesson.getId());
        boolean moduleAccess = lesson.getModule() != null
                && entitlementService.hasAccessToItem(student.getId(), ITEM_TYPE_MODULE, lesson.getModule().getId());
        return directLessonAccess || moduleAccess;
    }

    public void ensureLessonAccess(StudentProfile student, Lesson lesson) {
        if (!hasLessonAccess(student, lesson)) {
            throw new ResponseStatusException(FORBIDDEN,
                    "Paid content is locked until payment is completed.");
        }
    }

    private static StudentProfile requireStudentProfile(Optional<StudentProfile> studentProfile) {
        return studentProfile.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, STUDENT_PROFILE_NOT_FOUND));
    }

    private static boolean isPublishedQuiz(Quiz quiz) {
        Lesson lesson = quiz.getLesson();
        return quiz.getContentStatus() == ContentStatus.PUBLISHED
                && lesson != null
                && lesson.getContentStatus() == ContentStatus.PUBLISHED;
    }
}
