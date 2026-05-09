package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.ListeningExercise;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.FlashcardRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.ListeningExerciseRepository;
import com.example.aichinese.repository.MatchingGameRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TracingExerciseRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class StudentLearningAccessService {

    private final StudentProfileRepository studentProfileRepository;
    private final LessonRepository lessonRepository;
    private final FlashcardRepository flashcardRepository;
    private final QuizRepository quizRepository;
    private final MatchingGameRepository matchingGameRepository;
    private final ListeningExerciseRepository listeningExerciseRepository;
    private final TracingExerciseRepository tracingExerciseRepository;
    private final EntitlementService entitlementService;

    public StudentLearningAccessService(
            StudentProfileRepository studentProfileRepository,
            LessonRepository lessonRepository,
            FlashcardRepository flashcardRepository,
            QuizRepository quizRepository,
            MatchingGameRepository matchingGameRepository,
            ListeningExerciseRepository listeningExerciseRepository,
            TracingExerciseRepository tracingExerciseRepository,
            EntitlementService entitlementService) {
        this.studentProfileRepository = studentProfileRepository;
        this.lessonRepository = lessonRepository;
        this.flashcardRepository = flashcardRepository;
        this.quizRepository = quizRepository;
        this.matchingGameRepository = matchingGameRepository;
        this.listeningExerciseRepository = listeningExerciseRepository;
        this.tracingExerciseRepository = tracingExerciseRepository;
        this.entitlementService = entitlementService;
    }

    @Transactional(readOnly = true)
    public StudentProfile requireStudent(SecurityUserDetails currentUser) {
        return studentProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student profile could not be found."));
    }

    @Transactional(readOnly = true)
    public Lesson requirePublishedLesson(Long lessonId) {
        return lessonRepository.findByIdAndContentStatus(lessonId, ContentStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Published lesson was not found."));
    }

    @Transactional(readOnly = true)
    public Flashcard requirePublishedFlashcard(Long flashcardId) {
        Flashcard flashcard = flashcardRepository.findById(flashcardId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Flashcard was not found."));
        validatePublishedActivityLesson(flashcard.getLesson());
        return flashcard;
    }

    @Transactional(readOnly = true)
    public Quiz requirePublishedQuiz(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Quiz was not found."));
        validatePublishedActivityLesson(quiz.getLesson());
        if (quiz.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(NOT_FOUND, "Quiz was not found.");
        }
        return quiz;
    }

    @Transactional(readOnly = true)
    public MatchingGame requirePublishedMatchingGame(Long gameId) {
        MatchingGame game = matchingGameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Matching game was not found."));
        validatePublishedActivityLesson(game.getLesson());
        if (game.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(NOT_FOUND, "Matching game was not found.");
        }
        return game;
    }

    @Transactional(readOnly = true)
    public ListeningExercise requirePublishedListeningExercise(Long exerciseId) {
        ListeningExercise exercise = listeningExerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Listening exercise was not found."));
        validatePublishedActivityLesson(exercise.getLesson());
        if (exercise.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(NOT_FOUND, "Listening exercise was not found.");
        }
        return exercise;
    }

    @Transactional(readOnly = true)
    public TracingExercise requirePublishedTracingExercise(Long exerciseId) {
        TracingExercise exercise = tracingExerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tracing exercise was not found."));
        validatePublishedActivityLesson(exercise.getLesson());
        if (exercise.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(NOT_FOUND, "Tracing exercise was not found.");
        }
        return exercise;
    }

    public boolean isLocked(Lesson lesson) {
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

    public void ensureFreeAccess(Lesson lesson) {
        if (isLocked(lesson)) {
            throw new ResponseStatusException(FORBIDDEN,
                    "Paid content is locked until payment workflows are implemented.");
        }
    }

    public void ensureLessonAccess(StudentProfile student, Lesson lesson) {
        if (!hasLessonAccess(student, lesson)) {
            throw new ResponseStatusException(FORBIDDEN,
                    "Paid content is locked until payment is completed.");
        }
    }

    private void validatePublishedActivityLesson(Lesson lesson) {
        if (lesson == null || lesson.getContentStatus() != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(NOT_FOUND, "Published lesson was not found.");
        }
    }
}
