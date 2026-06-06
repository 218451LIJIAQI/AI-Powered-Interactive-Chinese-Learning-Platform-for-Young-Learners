package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
import com.example.aichinese.util.TextUtils;

@Service
public class StudentLearningService {

    private static final int QUIZ_PASS_PERCENT = 60;
    private static final int COMPLETE_PERCENT = 100;
    private static final String QUIZ_ATTEMPT_NOT_FOUND = "Quiz attempt was not found.";
    private static final String PAID_CONTENT_LOCKED = "Paid content is locked until payment is completed.";
    private static final String RECORDED_VIDEO_STREAM_PATH = "/api/v1/students/recorded-videos/";
    private static final String RECORDED_VIDEO_STREAM_SUFFIX = "/stream";

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

    @Transactional(readOnly = true)
    public StudentLessonPlayerResponse getLessonPlayer(SecurityUserDetails currentUser, Long lessonId) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        Lesson lesson = studentLearningAccessService.requirePublishedLesson(lessonId);
        studentLearningAccessService.ensureLessonAccess(student, lesson);
        int completionPercent = studentLearningProgressService.lessonCompletionPercent(student, lesson);

        return new StudentLessonPlayerResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getSummary(),
                lesson.getLessonContent(),
                lesson.getEstimatedDurationMinutes(),
                completionPercent,
                completionPercent >= COMPLETE_PERCENT,
                false,
                lesson.getLearningType() == null ? null : lesson.getLearningType().name(),
                buildLessonLiveClasses(student, lesson),
                buildRecordedVideos(student, lesson));
    }

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
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Quiz submission request is required.");
        }
        if (request.answers().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one answer is required.");
        }
        List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderBySortOrderAscCreatedAtAsc(quiz.getId());
        if (questions.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Quiz must contain at least one question.");
        }

        Map<Long, QuizSubmissionAnswerRequest> answerByQuestionId =
                validateAndIndexAnswers(request.answers(), questions);

        int score = 0;
        int maxScore = 0;
        int correctCount = 0;
        List<QuizAttemptResultQuestionResponse> answerResults = new ArrayList<>();

        QuizAttempt savedAttempt = quizAttemptRepository.save(newSubmittedAttempt(student, quiz));

        for (QuizQuestion question : questions) {
            int questionPoints = points(question);
            maxScore += questionPoints;
            QuizSubmissionAnswerRequest answerRequest = answerByQuestionId.get(question.getId());
            if (answerRequest == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Every quiz question requires an answer.");
            }

            String submittedAnswer = normalizeAnswerText(answerRequest.answerText());
            boolean correct = isCorrectAnswer(question, answerRequest.answerText());
            if (correct) {
                score += questionPoints;
                correctCount++;
            }

            QuizAttemptAnswer attemptAnswer = new QuizAttemptAnswer();
            attemptAnswer.setQuizAttempt(savedAttempt);
            attemptAnswer.setQuestion(question);
            attemptAnswer.setAnswerText(submittedAnswer);
            attemptAnswer.setCorrect(correct);
            quizAttemptAnswerRepository.save(attemptAnswer);

            answerResults.add(new QuizAttemptResultQuestionResponse(
                    question.getId(),
                    question.getQuestionType(),
                    question.getQuestionText(),
                    submittedAnswer,
                    question.getCorrectAnswerText(),
                    correct,
                    question.getPoints(),
                    question.getExplanation()));
        }

        savedAttempt.setScore(score);
        savedAttempt.setMaxScore(maxScore);
        quizAttemptRepository.save(savedAttempt);

        int percentage = percentageScore(score, maxScore);
        StudentLearningProgressService.CompletionOutcome completionOutcome =
                studentLearningProgressService.completeQuiz(student, quiz, percentage);

        return toQuizAttemptResultResponse(
                savedAttempt,
                score,
                maxScore,
                questions.size(),
                correctCount,
                completionOutcome.earnedPoints(),
                completionOutcome.totalPoints(),
                answerResults.stream()
                        .sorted(Comparator.comparing(QuizAttemptResultQuestionResponse::questionId))
                        .toList());
    }

    @Transactional(readOnly = true)
    public QuizAttemptResultResponse getQuizAttempt(SecurityUserDetails currentUser, Long attemptId) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        QuizAttempt attempt = quizAttemptRepository.findByIdAndStudentUserId(attemptId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, QUIZ_ATTEMPT_NOT_FOUND));
        if (!Objects.equals(attempt.getStudent().getId(), student.getId())) {
            throw new ResponseStatusException(FORBIDDEN, "You do not have access to this quiz attempt.");
        }
        ensureQuizAttemptStillAccessible(student, attempt);
        List<QuizAttemptAnswer> answers = quizAttemptAnswerRepository
                .findByQuizAttemptIdOrderByCreatedAtAsc(attempt.getId());
        List<QuizAttemptResultQuestionResponse> answerResponses = answers.stream()
                .map(StudentLearningService::toQuizAttemptResultQuestionResponse)
                .toList();

        int correctCount = (int) answers.stream().filter(QuizAttemptAnswer::isCorrect).count();
        int totalQuestions = answers.size();
        int score = attempt.getScore() == null ? 0 : attempt.getScore();
        int maxScore = attempt.getMaxScore() == null ? 0 : attempt.getMaxScore();

        return toQuizAttemptResultResponse(
                attempt,
                score,
                maxScore,
                totalQuestions,
                correctCount,
                0,
                pointsTotal(attempt.getStudent()),
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
                .map(StudentLearningService::toWrongAnswerResponse)
                .toList();
        return new PageResponse<>(items, safePage, safeSize, wrongAnswerPage.getTotalElements(), wrongAnswerPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<WrongAnswerResponse> getWrongAnswersForAttempt(SecurityUserDetails currentUser, Long attemptId) {
        QuizAttemptResultResponse attempt = getQuizAttempt(currentUser, attemptId);
        return attempt.answers().stream()
                .filter(answer -> !answer.correct())
                .map(answer -> toWrongAnswerResponse(attempt, answer))
                .toList();
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

    private void ensureQuizAttemptStillAccessible(StudentProfile student, QuizAttempt attempt) {
        if (!isQuizAttemptStillAccessible(student, attempt)) {
            throw new ResponseStatusException(FORBIDDEN, PAID_CONTENT_LOCKED);
        }
    }

    private boolean isQuizAttemptStillAccessible(StudentProfile student, QuizAttempt attempt) {
        Quiz quiz = attempt == null ? null : attempt.getQuiz();
        Lesson lesson = quiz == null ? null : quiz.getLesson();
        if (lesson == null) {
            return false;
        }
        return lesson.getContentStatus() == ContentStatus.PUBLISHED
                && studentLearningAccessService.hasLessonAccess(student, lesson);
    }

    private List<LiveClassResponse> buildLessonLiveClasses(StudentProfile student, Lesson lesson) {
        Set<Long> relatedPackageIds = liveLearningAccessService.findPublishedPackageIdsContainingLesson(lesson);

        return liveClassRepository.findRelatedToLessonOrPackages(lesson.getId(), relatedPackageIds).stream()
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
                            && liveLearningAccessService.isJoinWindowOpen(liveClass);
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

        return recordedLessonVideoRepository.findActiveRelatedToLessonOrPackages(lesson.getId(), relatedPackageIds).stream()
                .filter(video -> canAccessVideo(student, video))
                .map(video -> liveLearningMapper.toRecordedVideoResponse(
                        video,
                        studentRecordedVideoStreamUrl(video),
                        externalVideoUrl(video)))
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

    private static boolean isCorrectAnswer(QuizQuestion question, String answerText) {
        return normalize(answerText).equals(normalize(question.getCorrectAnswerText()));
    }

    private static Map<Long, QuizSubmissionAnswerRequest> validateAndIndexAnswers(
            List<QuizSubmissionAnswerRequest> answers,
            List<QuizQuestion> questions) {
        Set<Long> quizQuestionIds = questions.stream()
                .map(QuizQuestion::getId)
                .collect(Collectors.toSet());
        Map<Long, QuizSubmissionAnswerRequest> answerByQuestionId = new LinkedHashMap<>();
        for (QuizSubmissionAnswerRequest answer : answers) {
            if (answer == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Quiz answer must not be null.");
            }
            Long questionId = answer.questionId();
            if (!quizQuestionIds.contains(questionId)) {
                throw new ResponseStatusException(BAD_REQUEST, "Submitted answer does not belong to this quiz.");
            }
            if (answerByQuestionId.putIfAbsent(questionId, answer) != null) {
                throw new ResponseStatusException(BAD_REQUEST, "Duplicate answers for the same quiz question are not allowed.");
            }
        }
        return answerByQuestionId;
    }

    private static QuizAttempt newSubmittedAttempt(StudentProfile student, Quiz quiz) {
        LocalDateTime now = LocalDateTime.now();
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setStudent(student);
        attempt.setStartedAt(now);
        attempt.setSubmitted(true);
        attempt.setCompletedAt(now);
        return attempt;
    }

    private static QuizAttemptResultResponse toQuizAttemptResultResponse(
            QuizAttempt attempt,
            int score,
            int maxScore,
            int totalQuestions,
            int correctCount,
            int earnedPoints,
            int totalPoints,
            List<QuizAttemptResultQuestionResponse> answerResponses) {
        int percentage = percentageScore(score, maxScore);
        return new QuizAttemptResultResponse(
                attempt.getId(),
                attempt.getQuiz().getId(),
                attempt.getQuiz().getTitle(),
                score,
                maxScore,
                totalQuestions,
                correctCount,
                Math.max(0, totalQuestions - correctCount),
                earnedPoints,
                totalPoints,
                percentage >= QUIZ_PASS_PERCENT,
                attempt.getStartedAt(),
                attempt.getCompletedAt(),
                answerResponses);
    }

    private static WrongAnswerResponse toWrongAnswerResponse(
            QuizAttemptResultResponse attempt,
            QuizAttemptResultQuestionResponse answer) {
        return new WrongAnswerResponse(
                attempt.attemptId(),
                answer.questionId(),
                attempt.quizTitle(),
                answer.questionText(),
                answer.questionType(),
                answer.studentAnswer(),
                answer.correctAnswer(),
                answer.explanation(),
                attempt.submittedAt());
    }

    private static QuizAttemptResultQuestionResponse toQuizAttemptResultQuestionResponse(QuizAttemptAnswer answer) {
        QuizQuestion question = answer.getQuestion();
        return new QuizAttemptResultQuestionResponse(
                question.getId(),
                question.getQuestionType(),
                question.getQuestionText(),
                answer.getAnswerText(),
                question.getCorrectAnswerText(),
                answer.isCorrect(),
                question.getPoints(),
                question.getExplanation());
    }

    private static WrongAnswerResponse toWrongAnswerResponse(QuizAttemptAnswer answer) {
        QuizAttempt attempt = answer.getQuizAttempt();
        QuizQuestion question = answer.getQuestion();
        return new WrongAnswerResponse(
                attempt.getId(),
                question.getId(),
                question.getQuiz().getTitle(),
                question.getQuestionText(),
                question.getQuestionType(),
                answer.getAnswerText(),
                question.getCorrectAnswerText(),
                question.getExplanation(),
                attempt.getCompletedAt());
    }

    private static int percentageScore(int score, int maxScore) {
        return maxScore == 0 ? 0 : (int) Math.round(score * 100.0 / maxScore);
    }

    private static int points(QuizQuestion question) {
        return question.getPoints() == null ? 0 : question.getPoints();
    }

    private static int pointsTotal(StudentProfile student) {
        return student.getTotalPoints() == null ? 0 : student.getTotalPoints();
    }

    private static String studentRecordedVideoStreamUrl(RecordedLessonVideo video) {
        return video.getSourceType() == RecordedVideoSourceType.UPLOADED_FILE
                ? RECORDED_VIDEO_STREAM_PATH + video.getId() + RECORDED_VIDEO_STREAM_SUFFIX
                : null;
    }

    private static String externalVideoUrl(RecordedLessonVideo video) {
        return video.getSourceType() == RecordedVideoSourceType.EXTERNAL_URL ? video.getExternalVideoUrl() : null;
    }

    private static String normalize(String value) {
        return normalizeAnswerText(value).toLowerCase(Locale.ROOT);
    }

    private static String normalizeAnswerText(String value) {
        return Objects.requireNonNullElse(TextUtils.trimToNull(value), "");
    }
}
