package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class StudentLearningAccessService {

    private static final String STUDENT_PROFILE_NOT_FOUND = "Student profile could not be found.";
    private static final String QUIZ_NOT_FOUND = "Quiz was not found.";

    private final StudentProfileRepository studentProfileRepository;
    private final LessonRepository lessonRepository;
    private final QuizRepository quizRepository;

    public StudentLearningAccessService(
            StudentProfileRepository studentProfileRepository,
            LessonRepository lessonRepository,
            QuizRepository quizRepository) {
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "studentProfileRepository");
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository");
        this.quizRepository = Objects.requireNonNull(quizRepository, "quizRepository");
    }

    @Transactional(readOnly = true)
    public StudentProfile requireStudent(SecurityUserDetails currentUser) {
        return requireStudentProfile(studentProfileRepository.findByUserId(currentUser.getUserId()));
    }

    @Transactional(readOnly = true)
    public Lesson requirePublishedLesson(Long lessonId) {
        return lessonRepository.findByIdAndContentStatus(lessonId, ContentStatus.PUBLISHED)
                .filter(ContentVisibilityPolicy::isLessonVisible)
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

    private static StudentProfile requireStudentProfile(Optional<StudentProfile> studentProfile) {
        return studentProfile.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, STUDENT_PROFILE_NOT_FOUND));
    }

    private static boolean isPublishedQuiz(Quiz quiz) {
        return ContentVisibilityPolicy.isQuizVisible(quiz);
    }
}
