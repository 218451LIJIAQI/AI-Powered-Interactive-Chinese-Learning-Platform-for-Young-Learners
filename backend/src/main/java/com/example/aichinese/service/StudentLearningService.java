package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.learning.BadgeSummaryResponse;
import com.example.aichinese.dto.learning.DailyStreakResponse;
import com.example.aichinese.dto.learning.LiveClassResponse;
import com.example.aichinese.dto.learning.PointsTransactionResponse;
import com.example.aichinese.dto.learning.QuizAttemptResultQuestionResponse;
import com.example.aichinese.dto.learning.QuizAttemptResultResponse;
import com.example.aichinese.dto.learning.QuizSubmissionAnswerRequest;
import com.example.aichinese.dto.learning.RecordedLessonVideoResponse;
import com.example.aichinese.dto.learning.RewardSummaryResponse;
import com.example.aichinese.dto.learning.StudentLessonPlayerResponse;
import com.example.aichinese.dto.learning.StudentProgressSummaryResponse;
import com.example.aichinese.dto.learning.StudentQuizResponse;
import com.example.aichinese.dto.learning.StudentQuizSummaryResponse;
import com.example.aichinese.dto.learning.SubmitQuizAttemptRequest;
import com.example.aichinese.dto.learning.WrongAnswerResponse;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;
import com.example.aichinese.entity.enums.RecordedVideoSourceType;
import com.example.aichinese.mapper.QuizMapper;
import com.example.aichinese.repository.LiveClassEnrolmentRepository;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.repository.QuizQuestionRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.PaginationUtils;

/**
 * Student-side read/submit access to lesson playback and quiz attempts.
 *
 * <p>What remains here:
 * <ul>
 *   <li>Lesson playback (live classes + recorded videos surfaced to a student
 *       given their enrolment / package access).</li>
 *   <li>Quiz attempts: load (without correct answers leaking), submit, fetch
 *       past attempts, and pull wrong answers for retry.</li>
 *   <li>Aggregated progress / rewards / badges / points history / streak
 *       passthroughs to {@link StudentLearningProgressService}.</li>
 * </ul>
 */
@Service
public class StudentLearningService {

    private final StudentLearningAccessService studentLearningAccessService;
    private final StudentLearningProgressService studentLearningProgressService;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizRepository quizRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final LiveClassRepository liveClassRepository;
    private final LiveClassEnrolmentRepository liveClassEnrolmentRepository;
    private final RecordedLessonVideoRepository recordedLessonVideoRepository;
    private final QuizMapper quizMapper;
    private final LiveLearningAccessService liveLearningAccessService;
    private final LiveLearningMapper liveLearningMapper;

    public StudentLearningService(
            StudentLearningAccessService studentLearningAccessService,
            StudentLearningProgressService studentLearningProgressService,
            QuizQuestionRepository quizQuestionRepository,
            QuizRepository quizRepository,
            QuizOptionRepository quizOptionRepository,
            QuizAttemptRepository quizAttemptRepository,
            QuizAttemptAnswerRepository quizAttemptAnswerRepository,
            LiveClassRepository liveClassRepository,
            LiveClassEnrolmentRepository liveClassEnrolmentRepository,
            RecordedLessonVideoRepository recordedLessonVideoRepository,
            QuizMapper quizMapper,
            LiveLearningAccessService liveLearningAccessService,
            LiveLearningMapper liveLearningMapper) {
        this.studentLearningAccessService = Objects.requireNonNull(studentLearningAccessService, "studentLearningAccessService");
        this.studentLearningProgressService = Objects.requireNonNull(studentLearningProgressService, "studentLearningProgressService");
        this.quizQuestionRepository = Objects.requireNonNull(quizQuestionRepository, "quizQuestionRepository");
        this.quizRepository = Objects.requireNonNull(quizRepository, "quizRepository");
        this.quizOptionRepository = Objects.requireNonNull(quizOptionRepository, "quizOptionRepository");
        this.quizAttemptRepository = Objects.requireNonNull(quizAttemptRepository, "quizAttemptRepository");
        this.quizAttemptAnswerRepository = Objects.requireNonNull(quizAttemptAnswerRepository, "quizAttemptAnswerRepository");
        this.liveClassRepository = Objects.requireNonNull(liveClassRepository, "liveClassRepository");
        this.liveClassEnrolmentRepository = Objects.requireNonNull(liveClassEnrolmentRepository, "liveClassEnrolmentRepository");
        this.recordedLessonVideoRepository = Objects.requireNonNull(recordedLessonVideoRepository, "recordedLessonVideoRepository");
        this.quizMapper = Objects.requireNonNull(quizMapper, "quizMapper");
        this.liveLearningAccessService = Objects.requireNonNull(liveLearningAccessService, "liveLearningAccessService");
        this.liveLearningMapper = Objects.requireNonNull(liveLearningMapper, "liveLearningMapper");
    }

    // ===================================================================
    // Lesson playback
    // ===================================================================

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
                lesson.getLearningType() == null ? null : lesson.getLearningType().name(),
                buildLessonLiveClasses(student, lesson),
                buildRecordedVideos(student, lesson));
    }

    // ===================================================================
    // Quiz: load, submit, history
    // ===================================================================

    @Transactional(readOnly = true)
    public PageResponse<StudentQuizSummaryResponse> listQuizzes(
            SecurityUserDetails currentUser,
            Long lessonId,
            int page,
            int size) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<Quiz> quizPage = quizRepository.findAccessiblePublishedForStudent(
                student.getId(),
                lessonId,
                ContentStatus.PUBLISHED,
                ContentStatus.PUBLISHED,
                PageRequest.of(safePage, safeSize));
        List<StudentQuizSummaryResponse> items = quizPage.getContent().stream()
                .map(quiz -> quizMapper.toStudentQuizSummaryResponse(
                        quiz,
                        quizQuestionRepository.findByQuizIdOrderBySortOrderAscCreatedAtAsc(quiz.getId()).size()))
                .toList();
        return new PageResponse<>(items, safePage, safeSize, quizPage.getTotalElements(), quizPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public StudentQuizResponse getQuiz(SecurityUserDetails currentUser, Long quizId) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        Quiz quiz = studentLearningAccessService.requirePublishedQuiz(quizId);
        studentLearningAccessService.ensureLessonAccess(student, quiz.getLesson());
        List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderBySortOrderAscCreatedAtAsc(quiz.getId());
        List<QuizOption> options = questions.stream()
                .flatMap(question -> quizOptionRepository.findByQuestionIdOrderBySortOrderAscCreatedAtAsc(question.getId()).stream())
                .toList();
        return quizMapper.toStudentQuizResponse(
                quiz,
                questions,
                quizMapper.groupOptionsByQuestionId(options));
    }

    @Transactional
    public QuizAttemptResultResponse submitQuizAttempt(
            SecurityUserDetails currentUser,
            Long quizId,
            SubmitQuizAttemptRequest request) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        Quiz quiz = studentLearningAccessService.requirePublishedQuiz(quizId);
        studentLearningAccessService.ensureLessonAccess(student, quiz.getLesson());
        List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderBySortOrderAscCreatedAtAsc(quiz.getId());
        if (questions.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Quiz must contain at least one question.");
        }

        Set<Long> quizQuestionIds = questions.stream()
                .map(QuizQuestion::getId)
                .collect(Collectors.toSet());
        Map<Long, QuizSubmissionAnswerRequest> answerByQuestionId = new LinkedHashMap<>();
        for (QuizSubmissionAnswerRequest answer : request.answers()) {
            Long questionId = answer.questionId();
            if (!quizQuestionIds.contains(questionId)) {
                throw new ResponseStatusException(BAD_REQUEST, "Submitted answer does not belong to this quiz.");
            }
            if (answerByQuestionId.putIfAbsent(questionId, answer) != null) {
                throw new ResponseStatusException(BAD_REQUEST, "Duplicate answers for the same quiz question are not allowed.");
            }
        }

        if (answerByQuestionId.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one answer is required.");
        }

        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setStudent(student);
        attempt.setStartedAt(LocalDateTime.now());
        attempt.setSubmitted(true);
        attempt.setCompletedAt(LocalDateTime.now());

        int score = 0;
        int maxScore = 0;
        int correctCount = 0;
        List<QuizAttemptResultQuestionResponse> answerResults = new ArrayList<>();

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
        StudentLearningProgressService.CompletionOutcome completionOutcome =
                studentLearningProgressService.completeQuiz(student, quiz, percentage);

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
                answerResults.stream()
                        .sorted(Comparator.comparing(QuizAttemptResultQuestionResponse::questionId))
                        .toList());
    }

    @Transactional(readOnly = true)
    public QuizAttemptResultResponse getQuizAttempt(SecurityUserDetails currentUser, Long attemptId) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        QuizAttempt attempt = quizAttemptRepository.findByIdAndStudentUserId(attemptId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Quiz attempt was not found."));
        if (!Objects.equals(attempt.getStudent().getId(), student.getId())) {
            throw new ResponseStatusException(FORBIDDEN, "You do not have access to this quiz attempt.");
        }
        ensureQuizAttemptStillAccessible(student, attempt);
        List<QuizAttemptAnswer> answers = quizAttemptAnswerRepository
                .findByQuizAttemptIdOrderByCreatedAtAsc(attempt.getId());
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
    public PageResponse<WrongAnswerResponse> getWrongAnswers(SecurityUserDetails currentUser, int page, int size) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<QuizAttemptAnswer> wrongAnswerPage = quizAttemptAnswerRepository.findAccessibleWrongAnswers(
                student.getId(),
                ContentStatus.PUBLISHED,
                ContentStatus.ARCHIVED,
                AccessType.FREE,
                PageRequest.of(safePage, safeSize));
        List<WrongAnswerResponse> items = wrongAnswerPage.getContent().stream()
                .map(this::toWrongAnswerResponse)
                .toList();
        return new PageResponse<>(items, safePage, safeSize, wrongAnswerPage.getTotalElements(), wrongAnswerPage.getTotalPages());
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

    // ===================================================================
    // Aggregations
    // ===================================================================

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
    public PageResponse<BadgeSummaryResponse> getBadges(SecurityUserDetails currentUser, int page, int size) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        return studentLearningProgressService.getBadges(student, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<PointsTransactionResponse> getPointsHistory(SecurityUserDetails currentUser, int page, int size) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        return studentLearningProgressService.getPointsHistory(student, page, size);
    }

    @Transactional(readOnly = true)
    public DailyStreakResponse getStreak(SecurityUserDetails currentUser) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        return studentLearningProgressService.getStreak(student);
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    private void ensureQuizAttemptStillAccessible(StudentProfile student, QuizAttempt attempt) {
        if (!isQuizAttemptStillAccessible(student, attempt)) {
            throw new ResponseStatusException(FORBIDDEN, "Paid content is locked until payment is completed.");
        }
    }

    private boolean isQuizAttemptStillAccessible(StudentProfile student, QuizAttempt attempt) {
        if (attempt == null || attempt.getQuiz() == null || attempt.getQuiz().getLesson() == null) {
            return false;
        }
        Lesson lesson = attempt.getQuiz().getLesson();
        return lesson.getContentStatus() == ContentStatus.PUBLISHED
                && studentLearningAccessService.hasLessonAccess(student, lesson);
    }

    private List<LiveClassResponse> buildLessonLiveClasses(StudentProfile student, Lesson lesson) {
        Set<Long> relatedPackageIds = liveLearningAccessService.findPublishedPackageIdsContainingLesson(lesson);

        // Targeted query so we only pull live classes that target this lesson
        // or one of its related packages, instead of scanning every live class
        // in the system and filtering in-memory. When the related package set
        // is empty we still need to send a sentinel value because Hibernate
        // rejects `IN ()` against an empty collection.
        Collection<Long> packageIdsForQuery = relatedPackageIds.isEmpty()
                ? Collections.singleton(-1L)
                : relatedPackageIds;

        return liveClassRepository.findRelatedToLessonOrPackages(lesson.getId(), packageIdsForQuery).stream()
                .filter(liveClass -> liveClass.getLiveClassStatus() != LiveClassStatus.CANCELLED
                        && liveClass.getLiveClassStatus() != LiveClassStatus.COMPLETED)
                .map(liveClass -> {
                    long enrolledCount = liveClassEnrolmentRepository.countByLiveClassIdAndEnrolmentStatus(
                            liveClass.getId(),
                            LiveClassEnrolmentStatus.ENROLLED);
                    boolean enrolled = liveLearningAccessService.isStudentEnrolled(liveClass.getId(), student.getId());
                    boolean accessAllowed = hasLiveClassAccess(student, liveClass);
                    boolean canEnrol = !enrolled && accessAllowed;
                    boolean canJoin = enrolled && accessAllowed
                            && (liveClass.getLiveClassStatus() == LiveClassStatus.SCHEDULED
                                    || liveClass.getLiveClassStatus() == LiveClassStatus.ONGOING);
                    return liveLearningMapper.toLiveClassResponse(
                            liveClass,
                            enrolledCount,
                            enrolled,
                            canEnrol,
                            accessAllowed,
                            canJoin,
                            false);
                })
                .sorted(Comparator.comparing(LiveClassResponse::startTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<RecordedLessonVideoResponse> buildRecordedVideos(StudentProfile student, Lesson lesson) {
        Set<Long> relatedPackageIds = liveLearningAccessService.findPublishedPackageIdsContainingLesson(lesson);

        // Same DB-side filtering pattern as live classes above: ask the
        // database for only the videos that target this lesson or one of its
        // related packages. The sentinel id guards the `IN ()` edge case.
        Collection<Long> packageIdsForQuery = relatedPackageIds.isEmpty()
                ? Collections.singleton(-1L)
                : relatedPackageIds;

        return recordedLessonVideoRepository.findActiveRelatedToLessonOrPackages(lesson.getId(), packageIdsForQuery).stream()
                .filter(video -> canAccessVideo(student, video))
                .map(video -> liveLearningMapper.toRecordedVideoResponse(
                        video,
                        video.getSourceType() == RecordedVideoSourceType.UPLOADED_FILE
                                ? "/api/v1/students/recorded-videos/" + video.getId() + "/stream"
                                : null,
                        video.getSourceType() == RecordedVideoSourceType.EXTERNAL_URL ? video.getExternalVideoUrl() : null))
                .sorted(Comparator.comparing(RecordedLessonVideoResponse::sortOrder)
                        .thenComparing(RecordedLessonVideoResponse::createdAt))
                .toList();
    }

    private boolean hasLiveClassAccess(StudentProfile student, LiveClass liveClass) {
        try {
            if (liveClass.getLesson() != null) {
                liveLearningAccessService.ensureStudentHasLessonAccess(student, liveClass.getLesson());
            }
            if (liveClass.getCoursePackage() != null) {
                liveLearningAccessService.ensureStudentHasPackageAccess(student, liveClass.getCoursePackage());
            }
            return true;
        } catch (ResponseStatusException exception) {
            return false;
        }
    }

    private boolean canAccessVideo(StudentProfile student, RecordedLessonVideo video) {
        try {
            liveLearningAccessService.ensureStudentHasLessonAccess(student, video.getLesson());
            if (video.getCoursePackage() != null) {
                liveLearningAccessService.ensureStudentHasPackageAccess(student, video.getCoursePackage());
            }
            return true;
        } catch (ResponseStatusException exception) {
            return false;
        }
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
