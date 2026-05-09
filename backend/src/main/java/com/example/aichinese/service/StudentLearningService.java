package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learning.BadgeSummaryResponse;
import com.example.aichinese.dto.learning.CompleteTracingExerciseRequest;
import com.example.aichinese.dto.learning.DailyStreakResponse;
import com.example.aichinese.dto.learning.FlashcardOptionResponse;
import com.example.aichinese.dto.learning.FlashcardPracticeResponse;
import com.example.aichinese.dto.learning.ListeningExercisePlayResponse;
import com.example.aichinese.dto.learning.ListeningExerciseSubmissionResponse;
import com.example.aichinese.dto.learning.MatchingGamePlayResponse;
import com.example.aichinese.dto.learning.MatchingGameSubmissionResponse;
import com.example.aichinese.dto.learning.QuizAttemptResultQuestionResponse;
import com.example.aichinese.dto.learning.QuizAttemptResultResponse;
import com.example.aichinese.dto.learning.QuizSubmissionAnswerRequest;
import com.example.aichinese.dto.learning.RewardSummaryResponse;
import com.example.aichinese.dto.learning.StudentLessonActivitySummaryResponse;
import com.example.aichinese.dto.learning.StudentLessonPlayerResponse;
import com.example.aichinese.dto.learning.StudentProgressSummaryResponse;
import com.example.aichinese.dto.learning.StudentQuizResponse;
import com.example.aichinese.dto.learning.SubmitListeningExerciseRequest;
import com.example.aichinese.dto.learning.SubmitMatchingGameRequest;
import com.example.aichinese.dto.learning.SubmitQuizAttemptRequest;
import com.example.aichinese.dto.learning.TracingExerciseCompletionResponse;
import com.example.aichinese.dto.learning.TracingExercisePlayResponse;
import com.example.aichinese.dto.learning.WrongAnswerResponse;
import com.example.aichinese.dto.learning.PointsTransactionResponse;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonActivity;
import com.example.aichinese.entity.ListeningExercise;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LessonActivityType;
import com.example.aichinese.mapper.LearningActivityMapper;
import com.example.aichinese.repository.FlashcardRepository;
import com.example.aichinese.repository.LessonActivityRepository;
import com.example.aichinese.repository.ListeningExerciseRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.repository.MatchingGameRepository;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.repository.QuizQuestionRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.TracingExerciseRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class StudentLearningService {

    private final StudentLearningAccessService studentLearningAccessService;
    private final StudentLearningProgressService studentLearningProgressService;
    private final FlashcardRepository flashcardRepository;
    private final LessonActivityRepository lessonActivityRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final MatchingGameRepository matchingGameRepository;
    private final MatchingGameItemRepository matchingGameItemRepository;
    private final ListeningExerciseRepository listeningExerciseRepository;
    private final TracingExerciseRepository tracingExerciseRepository;
    private final LearningActivityMapper learningActivityMapper;
    private final LearningPayloadCodec learningPayloadCodec;

    public StudentLearningService(
            StudentLearningAccessService studentLearningAccessService,
            StudentLearningProgressService studentLearningProgressService,
            FlashcardRepository flashcardRepository,
            LessonActivityRepository lessonActivityRepository,
            QuizRepository quizRepository,
            QuizQuestionRepository quizQuestionRepository,
            QuizOptionRepository quizOptionRepository,
            QuizAttemptRepository quizAttemptRepository,
            QuizAttemptAnswerRepository quizAttemptAnswerRepository,
            MatchingGameRepository matchingGameRepository,
            MatchingGameItemRepository matchingGameItemRepository,
            ListeningExerciseRepository listeningExerciseRepository,
            TracingExerciseRepository tracingExerciseRepository,
            LearningActivityMapper learningActivityMapper,
            LearningPayloadCodec learningPayloadCodec) {
        this.studentLearningAccessService = studentLearningAccessService;
        this.studentLearningProgressService = studentLearningProgressService;
        this.flashcardRepository = flashcardRepository;
        this.lessonActivityRepository = lessonActivityRepository;
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizOptionRepository = quizOptionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.quizAttemptAnswerRepository = quizAttemptAnswerRepository;
        this.matchingGameRepository = matchingGameRepository;
        this.matchingGameItemRepository = matchingGameItemRepository;
        this.listeningExerciseRepository = listeningExerciseRepository;
        this.tracingExerciseRepository = tracingExerciseRepository;
        this.learningActivityMapper = learningActivityMapper;
        this.learningPayloadCodec = learningPayloadCodec;
    }

    @Transactional(readOnly = true)
    public StudentLessonPlayerResponse getLessonPlayer(SecurityUserDetails currentUser, Long lessonId) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        Lesson lesson = studentLearningAccessService.requirePublishedLesson(lessonId);
        boolean locked = !studentLearningAccessService.hasLessonAccess(student, lesson);
        studentLearningAccessService.ensureLessonAccess(student, lesson);
        int completionPercent = studentLearningProgressService.lessonCompletionPercent(student, lesson);

        return new StudentLessonPlayerResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getSummary(),
                lesson.getLessonContent(),
                lesson.getEstimatedDurationMinutes(),
                completionPercent,
                completionPercent >= 100,
                locked,
                buildLessonActivities(student, lesson, locked));
    }

    @Transactional(readOnly = true)
    public List<StudentLessonActivitySummaryResponse> getLessonActivities(SecurityUserDetails currentUser, Long lessonId) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        Lesson lesson = studentLearningAccessService.requirePublishedLesson(lessonId);
        studentLearningAccessService.ensureLessonAccess(student, lesson);
        return buildLessonActivities(student, lesson, !studentLearningAccessService.hasLessonAccess(student, lesson));
    }

    @Transactional(readOnly = true)
    public List<FlashcardOptionResponse> getFlashcards(SecurityUserDetails currentUser, Long lessonId) {
        studentLearningAccessService.requireStudent(currentUser);
        Lesson lesson = studentLearningAccessService.requirePublishedLesson(lessonId);
        studentLearningAccessService.ensureLessonAccess(studentLearningAccessService.requireStudent(currentUser), lesson);
        return flashcardRepository.findByLessonIdOrderBySortOrderAscCreatedAtAsc(lesson.getId()).stream()
                .filter(card -> card.getContentStatus() != ContentStatus.ARCHIVED)
                .map(learningActivityMapper::toFlashcardResponse)
                .toList();
    }

    @Transactional
    public FlashcardPracticeResponse practiceFlashcard(SecurityUserDetails currentUser, Long flashcardId) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        Flashcard flashcard = studentLearningAccessService.requirePublishedFlashcard(flashcardId);
        studentLearningAccessService.ensureLessonAccess(student, flashcard.getLesson());
        StudentLearningProgressService.CompletionOutcome outcome = studentLearningProgressService.completeFlashcard(student, flashcard);
        return new FlashcardPracticeResponse(
                flashcardId,
                true,
                outcome.firstCompletion(),
                outcome.totalPoints(),
                outcome.earnedPoints(),
                outcome.lessonCompletionPercent());
    }

    @Transactional(readOnly = true)
    public StudentQuizResponse getQuiz(SecurityUserDetails currentUser, Long quizId) {
        studentLearningAccessService.requireStudent(currentUser);
        Quiz quiz = studentLearningAccessService.requirePublishedQuiz(quizId);
        studentLearningAccessService.ensureLessonAccess(studentLearningAccessService.requireStudent(currentUser), quiz.getLesson());
        List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderBySortOrderAscCreatedAtAsc(quiz.getId());
        List<QuizOption> options = questions.stream()
                .flatMap(question -> quizOptionRepository.findByQuestionIdOrderBySortOrderAscCreatedAtAsc(question.getId()).stream())
                .toList();
        return learningActivityMapper.toStudentQuizResponse(
                quiz,
                questions,
                learningActivityMapper.groupOptionsByQuestionId(options));
    }

    @Transactional
    public QuizAttemptResultResponse submitQuizAttempt(SecurityUserDetails currentUser, Long quizId, SubmitQuizAttemptRequest request) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        Quiz quiz = studentLearningAccessService.requirePublishedQuiz(quizId);
        studentLearningAccessService.ensureLessonAccess(student, quiz.getLesson());
        List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderBySortOrderAscCreatedAtAsc(quiz.getId());
        if (questions.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Quiz must contain at least one question.");
        }

        Map<Long, QuizSubmissionAnswerRequest> answerByQuestionId = request.answers().stream()
                .collect(java.util.stream.Collectors.toMap(
                        QuizSubmissionAnswerRequest::questionId,
                        answer -> answer,
                        (left, right) -> right));

        if (answerByQuestionId.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one answer is required.");
        }

        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setStudent(student);
        attempt.setStartedAt(java.time.LocalDateTime.now());
        attempt.setSubmitted(true);
        attempt.setCompletedAt(java.time.LocalDateTime.now());

        int score = 0;
        int maxScore = 0;
        int correctCount = 0;
        List<QuizAttemptResultQuestionResponse> answerResults = new java.util.ArrayList<>();

        QuizAttempt savedAttempt = quizAttemptRepository.save(attempt);

        for (QuizQuestion question : questions) {
            maxScore += question.getPoints() == null ? 0 : question.getPoints();
            QuizSubmissionAnswerRequest answerRequest = answerByQuestionId.get(question.getId());
            if (answerRequest == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Every quiz question requires an answer.");
            }

            boolean correct = isCorrectAnswer(question, answerRequest.answerText());
            if (correct) {
                score += question.getPoints() == null ? 0 : question.getPoints();
                correctCount++;
            }

            QuizAttemptAnswer attemptAnswer = new QuizAttemptAnswer();
            attemptAnswer.setQuizAttempt(savedAttempt);
            attemptAnswer.setQuestion(question);
            attemptAnswer.setAnswerText(answerRequest.answerText().trim());
            attemptAnswer.setCorrect(correct);
            quizAttemptAnswerRepository.save(attemptAnswer);

            answerResults.add(new QuizAttemptResultQuestionResponse(
                    question.getId(),
                    question.getQuestionType(),
                    question.getQuestionText(),
                    answerRequest.answerText().trim(),
                    question.getCorrectAnswerText(),
                    correct,
                    question.getPoints(),
                    question.getExplanation()));
        }

        savedAttempt.setScore(score);
        savedAttempt.setMaxScore(maxScore);
        quizAttemptRepository.save(savedAttempt);

        int percentage = maxScore == 0 ? 0 : (int) Math.round((score * 100.0) / maxScore);
        StudentLearningProgressService.CompletionOutcome completionOutcome = studentLearningProgressService.completeQuiz(student, quiz, percentage);

        return new QuizAttemptResultResponse(
                savedAttempt.getId(),
                quiz.getId(),
                quiz.getTitle(),
                score,
                maxScore,
                questions.size(),
                correctCount,
                Math.max(0, questions.size() - correctCount),
                completionOutcome.earnedPoints(),
                completionOutcome.totalPoints(),
                percentage >= 60,
                savedAttempt.getStartedAt(),
                savedAttempt.getCompletedAt(),
                answerResults.stream().sorted(Comparator.comparing(QuizAttemptResultQuestionResponse::questionId)).toList());
    }

    @Transactional(readOnly = true)
    public QuizAttemptResultResponse getQuizAttempt(SecurityUserDetails currentUser, Long attemptId) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        QuizAttempt attempt = quizAttemptRepository.findByIdAndStudentUserId(attemptId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Quiz attempt was not found."));
        if (!Objects.equals(attempt.getStudent().getId(), student.getId())) {
            throw new ResponseStatusException(FORBIDDEN, "You do not have access to this quiz attempt.");
        }
        List<QuizAttemptAnswer> answers = quizAttemptAnswerRepository.findByQuizAttemptIdOrderByCreatedAtAsc(attempt.getId());
        List<QuizAttemptResultQuestionResponse> answerResponses = answers.stream()
                .map(answer -> new QuizAttemptResultQuestionResponse(
                        answer.getQuestion().getId(),
                        answer.getQuestion().getQuestionType(),
                        answer.getQuestion().getQuestionText(),
                        answer.getAnswerText(),
                        answer.getQuestion().getCorrectAnswerText(),
                        answer.isCorrect(),
                        answer.getQuestion().getPoints(),
                        answer.getQuestion().getExplanation()))
                .toList();

        int correctCount = (int) answers.stream().filter(QuizAttemptAnswer::isCorrect).count();
        int totalQuestions = answers.size();
        int score = attempt.getScore() == null ? 0 : attempt.getScore();
        int maxScore = attempt.getMaxScore() == null ? 0 : attempt.getMaxScore();
        int percentage = maxScore == 0 ? 0 : (int) Math.round((score * 100.0) / maxScore);

        return new QuizAttemptResultResponse(
                attempt.getId(),
                attempt.getQuiz().getId(),
                attempt.getQuiz().getTitle(),
                score,
                maxScore,
                totalQuestions,
                correctCount,
                Math.max(0, totalQuestions - correctCount),
                0,
                attempt.getStudent().getTotalPoints() == null ? 0 : attempt.getStudent().getTotalPoints(),
                percentage >= 60,
                attempt.getStartedAt(),
                attempt.getCompletedAt(),
                answerResponses);
    }

    @Transactional(readOnly = true)
    public List<WrongAnswerResponse> getWrongAnswers(SecurityUserDetails currentUser) {
        studentLearningAccessService.requireStudent(currentUser);
        return quizAttemptAnswerRepository.findByQuizAttemptStudentIdAndCorrectFalseOrderByCreatedAtDesc(
                studentLearningAccessService.requireStudent(currentUser).getId()).stream()
                .map(this::toWrongAnswerResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WrongAnswerResponse> getWrongAnswersForAttempt(SecurityUserDetails currentUser, Long attemptId) {
        QuizAttemptResultResponse attempt = getQuizAttempt(currentUser, attemptId);
        return attempt.answers().stream()
                .filter(answer -> !answer.correct())
                .map(answer -> new WrongAnswerResponse(
                        attemptId,
                        answer.questionId(),
                        attempt.quizTitle(),
                        answer.questionText(),
                        answer.questionType(),
                        answer.studentAnswer(),
                        answer.correctAnswer(),
                        answer.explanation(),
                        attempt.submittedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MatchingGamePlayResponse getMatchingGame(SecurityUserDetails currentUser, Long gameId) {
        studentLearningAccessService.requireStudent(currentUser);
        MatchingGame game = studentLearningAccessService.requirePublishedMatchingGame(gameId);
        studentLearningAccessService.ensureLessonAccess(studentLearningAccessService.requireStudent(currentUser), game.getLesson());
        return learningActivityMapper.toMatchingGamePlayResponse(
                game,
                matchingGameItemRepository.findByMatchingGameIdOrderBySortOrderAscCreatedAtAsc(game.getId()));
    }

    @Transactional
    public MatchingGameSubmissionResponse submitMatchingGame(
            SecurityUserDetails currentUser,
            Long gameId,
            SubmitMatchingGameRequest request) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        MatchingGame game = studentLearningAccessService.requirePublishedMatchingGame(gameId);
        studentLearningAccessService.ensureLessonAccess(student, game.getLesson());
        List<MatchingGameItem> items = matchingGameItemRepository.findByMatchingGameIdOrderBySortOrderAscCreatedAtAsc(game.getId());
        Map<Long, String> submitted = request.pairs().stream()
                .collect(java.util.stream.Collectors.toMap(
                        pair -> pair.itemId(),
                        pair -> pair.submittedRightText().trim(),
                        (left, right) -> right));

        int correctPairs = 0;
        for (MatchingGameItem item : items) {
            String submittedText = submitted.get(item.getId());
            if (submittedText != null && normalize(submittedText).equals(normalize(item.getRightText()))) {
                correctPairs++;
            }
        }

        boolean completed = !items.isEmpty() && correctPairs == items.size();
        StudentLearningProgressService.CompletionOutcome outcome = studentLearningProgressService.completeMatchingGame(
                student,
                game,
                completed);
        return new MatchingGameSubmissionResponse(
                gameId,
                completed,
                outcome.firstCompletion(),
                correctPairs,
                items.size(),
                outcome.earnedPoints(),
                outcome.totalPoints(),
                outcome.lessonCompletionPercent());
    }

    @Transactional(readOnly = true)
    public ListeningExercisePlayResponse getListeningExercise(SecurityUserDetails currentUser, Long exerciseId) {
        studentLearningAccessService.requireStudent(currentUser);
        ListeningExercise exercise = studentLearningAccessService.requirePublishedListeningExercise(exerciseId);
        studentLearningAccessService.ensureLessonAccess(studentLearningAccessService.requireStudent(currentUser), exercise.getLesson());
        return learningActivityMapper.toListeningExercisePlayResponse(
                exercise,
                learningPayloadCodec.deserializeListeningPayload(exercise.getQuestionPayload()));
    }

    @Transactional
    public ListeningExerciseSubmissionResponse submitListeningExercise(
            SecurityUserDetails currentUser,
            Long exerciseId,
            SubmitListeningExerciseRequest request) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        ListeningExercise exercise = studentLearningAccessService.requirePublishedListeningExercise(exerciseId);
        studentLearningAccessService.ensureLessonAccess(student, exercise.getLesson());
        var payload = learningPayloadCodec.deserializeListeningPayload(exercise.getQuestionPayload());

        String selectedAnswer = request.answerText().trim();
        String correctAnswer = payload.options().stream()
                .filter(option -> option.correctOption())
                .map(option -> option.optionText())
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Listening exercise is missing a correct option."));

        boolean correct = normalize(selectedAnswer).equals(normalize(correctAnswer));
        StudentLearningProgressService.CompletionOutcome outcome = studentLearningProgressService.completeListeningExercise(
                student,
                exercise);

        return new ListeningExerciseSubmissionResponse(
                exerciseId,
                correct,
                outcome.firstCompletion(),
                correctAnswer,
                payload.explanation(),
                outcome.earnedPoints(),
                outcome.totalPoints(),
                outcome.lessonCompletionPercent());
    }

    @Transactional(readOnly = true)
    public TracingExercisePlayResponse getTracingExercise(SecurityUserDetails currentUser, Long exerciseId) {
        studentLearningAccessService.requireStudent(currentUser);
        TracingExercise exercise = studentLearningAccessService.requirePublishedTracingExercise(exerciseId);
        studentLearningAccessService.ensureLessonAccess(studentLearningAccessService.requireStudent(currentUser), exercise.getLesson());
        return learningActivityMapper.toTracingExercisePlayResponse(exercise);
    }

    @Transactional
    public TracingExerciseCompletionResponse completeTracingExercise(
            SecurityUserDetails currentUser,
            Long exerciseId,
            CompleteTracingExerciseRequest request) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        TracingExercise exercise = studentLearningAccessService.requirePublishedTracingExercise(exerciseId);
        studentLearningAccessService.ensureLessonAccess(student, exercise.getLesson());
        StudentLearningProgressService.CompletionOutcome outcome = studentLearningProgressService.completeTracingExercise(
                student,
                exercise);
        return new TracingExerciseCompletionResponse(
                exerciseId,
                true,
                outcome.firstCompletion(),
                outcome.earnedPoints(),
                outcome.totalPoints(),
                outcome.lessonCompletionPercent());
    }

    @Transactional(readOnly = true)
    public StudentProgressSummaryResponse getProgressSummary(SecurityUserDetails currentUser) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        return studentLearningProgressService.getProgressSummary(student);
    }

    @Transactional(readOnly = true)
    public RewardSummaryResponse getRewards(SecurityUserDetails currentUser) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        return studentLearningProgressService.getRewardSummary(student);
    }

    @Transactional(readOnly = true)
    public List<BadgeSummaryResponse> getBadges(SecurityUserDetails currentUser) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        return studentLearningProgressService.getBadges(student);
    }

    @Transactional(readOnly = true)
    public List<PointsTransactionResponse> getPointsHistory(SecurityUserDetails currentUser) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        return studentLearningProgressService.getPointsHistory(student);
    }

    @Transactional(readOnly = true)
    public DailyStreakResponse getStreak(SecurityUserDetails currentUser) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        return studentLearningProgressService.getStreak(student);
    }

    private List<StudentLessonActivitySummaryResponse> buildLessonActivities(StudentProfile student, Lesson lesson, boolean locked) {
        List<LessonActivity> placeholders = lessonActivityRepository
                .findByLessonIdAndContentStatusNotOrderBySortOrderAscCreatedAtAsc(lesson.getId(), ContentStatus.ARCHIVED);

        Map<LessonActivityType, List<LessonActivity>> placeholdersByType = placeholders.stream()
                .collect(java.util.stream.Collectors.groupingBy(LessonActivity::getActivityType));

        List<StudentLessonActivitySummaryResponse> activities = new java.util.ArrayList<>();

        addFlashcardSummary(student, lesson, placeholdersByType.getOrDefault(LessonActivityType.FLASHCARD, List.of()), locked, activities);
        addQuizSummary(student, lesson, placeholdersByType.getOrDefault(LessonActivityType.QUIZ, List.of()), locked, activities);
        addMatchingSummary(student, lesson, placeholdersByType.getOrDefault(LessonActivityType.MATCHING, List.of()), locked, activities);
        addListeningSummary(student, lesson, placeholdersByType.getOrDefault(LessonActivityType.LISTENING, List.of()), locked, activities);
        addTracingSummary(student, lesson, placeholdersByType.getOrDefault(LessonActivityType.TRACING, List.of()), locked, activities);

        return activities.stream()
                .sorted(Comparator.comparing(StudentLessonActivitySummaryResponse::activityType))
                .toList();
    }

    private void addFlashcardSummary(
            StudentProfile student,
            Lesson lesson,
            List<LessonActivity> placeholders,
            boolean locked,
            List<StudentLessonActivitySummaryResponse> target) {
        List<Flashcard> flashcards = flashcardRepository.findByLessonIdOrderBySortOrderAscCreatedAtAsc(lesson.getId()).stream()
                .filter(card -> card.getContentStatus() != ContentStatus.ARCHIVED)
                .toList();
        Long placeholderId = placeholders.isEmpty() ? null : placeholders.get(0).getId();
        target.add(new StudentLessonActivitySummaryResponse(
                "FLASHCARD",
                flashcards.isEmpty() ? null : lesson.getId(),
                placeholderId,
                placeholders.isEmpty() ? "Flashcards" : placeholders.get(0).getTitle(),
                placeholders.isEmpty() ? "Practise vocabulary cards." : placeholders.get(0).getDescription(),
                !flashcards.isEmpty(),
                !flashcards.isEmpty() && flashcards.stream()
                        .allMatch(card -> studentLearningProgressService.isCompleted(student, "FLASHCARD", card.getId())),
                locked));
    }

    private void addQuizSummary(StudentProfile student, Lesson lesson, List<LessonActivity> placeholders, boolean locked,
            List<StudentLessonActivitySummaryResponse> target) {
        List<Quiz> quizzes = quizRepository.findByLessonIdOrderByCreatedAtAsc(lesson.getId()).stream()
                .filter(quiz -> quiz.getContentStatus() != ContentStatus.ARCHIVED)
                .toList();
        if (quizzes.isEmpty()) {
            target.add(new StudentLessonActivitySummaryResponse(
                    "QUIZ",
                    null,
                    placeholders.isEmpty() ? null : placeholders.get(0).getId(),
                    placeholders.isEmpty() ? "Quiz" : placeholders.get(0).getTitle(),
                    placeholders.isEmpty() ? "Take a short quiz." : placeholders.get(0).getDescription(),
                    false,
                    false,
                    locked));
            return;
        }
        Quiz quiz = quizzes.get(0);
        target.add(new StudentLessonActivitySummaryResponse(
                "QUIZ",
                quiz.getId(),
                placeholders.isEmpty() ? null : placeholders.get(0).getId(),
                quiz.getTitle(),
                quiz.getDescription(),
                true,
                studentLearningProgressService.isCompleted(student, "QUIZ", quiz.getId()),
                locked));
    }

    private void addMatchingSummary(StudentProfile student, Lesson lesson, List<LessonActivity> placeholders, boolean locked,
            List<StudentLessonActivitySummaryResponse> target) {
        List<MatchingGame> games = matchingGameRepository.findByLessonIdOrderByCreatedAtAsc(lesson.getId()).stream()
                .filter(game -> game.getContentStatus() != ContentStatus.ARCHIVED)
                .toList();
        MatchingGame game = games.isEmpty() ? null : games.get(0);
        target.add(new StudentLessonActivitySummaryResponse(
                "MATCHING",
                game != null ? game.getId() : null,
                placeholders.isEmpty() ? null : placeholders.get(0).getId(),
                game != null ? game.getTitle() : "Matching Game",
                placeholders.isEmpty() ? "Match words with meanings." : placeholders.get(0).getDescription(),
                game != null,
                game != null && studentLearningProgressService.isCompleted(student, "MATCHING", game.getId()),
                locked));
    }

    private void addListeningSummary(StudentProfile student, Lesson lesson, List<LessonActivity> placeholders, boolean locked,
            List<StudentLessonActivitySummaryResponse> target) {
        List<ListeningExercise> exercises = listeningExerciseRepository.findByLessonIdOrderByCreatedAtAsc(lesson.getId()).stream()
                .filter(exercise -> exercise.getContentStatus() != ContentStatus.ARCHIVED)
                .toList();
        ListeningExercise exercise = exercises.isEmpty() ? null : exercises.get(0);
        target.add(new StudentLessonActivitySummaryResponse(
                "LISTENING",
                exercise != null ? exercise.getId() : null,
                placeholders.isEmpty() ? null : placeholders.get(0).getId(),
                exercise != null ? exercise.getTitle() : "Listening Exercise",
                placeholders.isEmpty() ? "Listen and choose the best answer." : placeholders.get(0).getDescription(),
                exercise != null,
                exercise != null && studentLearningProgressService.isCompleted(student, "LISTENING", exercise.getId()),
                locked));
    }

    private void addTracingSummary(StudentProfile student, Lesson lesson, List<LessonActivity> placeholders, boolean locked,
            List<StudentLessonActivitySummaryResponse> target) {
        List<TracingExercise> exercises = tracingExerciseRepository.findByLessonIdOrderByCreatedAtAsc(lesson.getId()).stream()
                .filter(exercise -> exercise.getContentStatus() != ContentStatus.ARCHIVED)
                .toList();
        TracingExercise exercise = exercises.isEmpty() ? null : exercises.get(0);
        target.add(new StudentLessonActivitySummaryResponse(
                "TRACING",
                exercise != null ? exercise.getId() : null,
                placeholders.isEmpty() ? null : placeholders.get(0).getId(),
                exercise != null ? exercise.getTitle() : "Tracing Exercise",
                placeholders.isEmpty() ? "Practise character tracing." : placeholders.get(0).getDescription(),
                exercise != null,
                exercise != null && studentLearningProgressService.isCompleted(student, "TRACING", exercise.getId()),
                locked));
    }

    private boolean isCorrectAnswer(QuizQuestion question, String answerText) {
        String submitted = answerText == null ? "" : answerText.trim();
        return normalize(submitted).equals(normalize(question.getCorrectAnswerText()));
    }

    private WrongAnswerResponse toWrongAnswerResponse(QuizAttemptAnswer answer) {
        return new WrongAnswerResponse(
                answer.getQuizAttempt().getId(),
                answer.getQuestion().getId(),
                answer.getQuestion().getQuiz().getTitle(),
                answer.getQuestion().getQuestionText(),
                answer.getQuestion().getQuestionType(),
                answer.getAnswerText(),
                answer.getQuestion().getCorrectAnswerText(),
                answer.getQuestion().getExplanation(),
                answer.getQuizAttempt().getCompletedAt());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
