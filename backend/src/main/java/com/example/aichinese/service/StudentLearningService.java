package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.example.aichinese.dto.learning.QuizHintResponse;
import com.example.aichinese.dto.learning.QuizOptionResponse;
import com.example.aichinese.dto.learning.QuizAttemptResultResponse;
import com.example.aichinese.dto.learning.QuizSubmissionAnswerRequest;
import com.example.aichinese.dto.learning.RecordedLessonVideoResponse;
import com.example.aichinese.dto.learning.RewardSummaryResponse;
import com.example.aichinese.dto.learning.StudentLessonPlayerResponse;
import com.example.aichinese.dto.learning.StudentLessonProgressResponse;
import com.example.aichinese.dto.learning.StudentProgressSummaryResponse;
import com.example.aichinese.dto.learning.StudentQuizResponse;
import com.example.aichinese.dto.learning.StudentQuizSummaryResponse;
import com.example.aichinese.dto.learning.SubmitQuizAttemptRequest;
import com.example.aichinese.dto.learning.WrongAnswerResponse;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.PackageItem;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;
import com.example.aichinese.entity.enums.PackageItemType;
import com.example.aichinese.entity.enums.RecordedVideoSourceType;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.mapper.QuizMapper;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.repository.QuizQuestionRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.PaginationUtils;

@Service
public class StudentLearningService {

    private static final int QUIZ_PASS_PERCENT = 60;
    private static final int COMPLETE_PERCENT = 100;
    private static final String QUIZ_ATTEMPT_NOT_FOUND = "Quiz attempt was not found.";
    private static final String QUIZ_ATTEMPT_UNAVAILABLE = "This quiz attempt is no longer available because its lesson is not published.";
    private static final String RECORDED_VIDEO_STREAM_PATH = "/api/v1/students/recorded-videos/";
    private static final String RECORDED_VIDEO_STREAM_SUFFIX = "/stream";
    private static final String HINTS_NOT_AVAILABLE = "Hints are not available for independent practice.";
    private static final int LESSON_QUIZ_LIMIT = 50;

    private final StudentLearningAccessService studentLearningAccessService;
    private final StudentLearningProgressService studentLearningProgressService;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizRepository quizRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final LiveClassRepository liveClassRepository;
    private final RecordedLessonVideoRepository recordedLessonVideoRepository;
    private final QuizMapper quizMapper;
    private final LiveLearningAccessService liveLearningAccessService;
    private final LiveLearningMapper liveLearningMapper;
    private final PackageItemRepository packageItemRepository;

    public StudentLearningService(
            StudentLearningAccessService studentLearningAccessService,
            StudentLearningProgressService studentLearningProgressService,
            QuizQuestionRepository quizQuestionRepository,
            QuizRepository quizRepository,
            QuizOptionRepository quizOptionRepository,
            QuizAttemptRepository quizAttemptRepository,
            QuizAttemptAnswerRepository quizAttemptAnswerRepository,
            LiveClassRepository liveClassRepository,
            RecordedLessonVideoRepository recordedLessonVideoRepository,
            QuizMapper quizMapper,
            LiveLearningAccessService liveLearningAccessService,
            LiveLearningMapper liveLearningMapper,
            PackageItemRepository packageItemRepository) {
        this.packageItemRepository = Objects.requireNonNull(packageItemRepository, "packageItemRepository");
        this.studentLearningAccessService = Objects.requireNonNull(studentLearningAccessService, "studentLearningAccessService");
        this.studentLearningProgressService = Objects.requireNonNull(studentLearningProgressService, "studentLearningProgressService");
        this.quizQuestionRepository = Objects.requireNonNull(quizQuestionRepository, "quizQuestionRepository");
        this.quizRepository = Objects.requireNonNull(quizRepository, "quizRepository");
        this.quizOptionRepository = Objects.requireNonNull(quizOptionRepository, "quizOptionRepository");
        this.quizAttemptRepository = Objects.requireNonNull(quizAttemptRepository, "quizAttemptRepository");
        this.quizAttemptAnswerRepository = Objects.requireNonNull(quizAttemptAnswerRepository, "quizAttemptAnswerRepository");
        this.liveClassRepository = Objects.requireNonNull(liveClassRepository, "liveClassRepository");
        this.recordedLessonVideoRepository = Objects.requireNonNull(recordedLessonVideoRepository, "recordedLessonVideoRepository");
        this.quizMapper = Objects.requireNonNull(quizMapper, "quizMapper");
        this.liveLearningAccessService = Objects.requireNonNull(liveLearningAccessService, "liveLearningAccessService");
        this.liveLearningMapper = Objects.requireNonNull(liveLearningMapper, "liveLearningMapper");
    }

    @Transactional(readOnly = true)
    public StudentLessonPlayerResponse getLessonPlayer(SecurityUserDetails currentUser, Long lessonId) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        Lesson lesson = studentLearningAccessService.requirePublishedLesson(lessonId);
        StudentLearningProgressService.LessonProgressSnapshot snapshot =
                studentLearningProgressService.lessonSnapshot(student, lesson);
        LearningModule module = lesson.getModule();
        CoursePackage course = findVisibleCourse(lesson, module);
        Set<Long> relatedPackageIds = liveLearningAccessService.findPublishedPackageIdsContainingLesson(lesson);

        return new StudentLessonPlayerResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getSummary(),
                lesson.getLessonContent(),
                lesson.getEstimatedDurationMinutes(),
                snapshot.completionPercent(),
                snapshot.completed() || snapshot.completionPercent() >= COMPLETE_PERCENT,
                lesson.getLearningType() == null ? null : lesson.getLearningType().name(),
                buildLessonLiveClasses(student, lesson, relatedPackageIds),
                buildRecordedVideos(lesson, relatedPackageIds),
                buildLessonQuizzes(lesson),
                snapshot.contentViewed(),
                snapshot.mastery().name(),
                snapshot.bestQuizPercentage(),
                lesson.getCoverImageUrl(),
                module == null ? null : module.getId(),
                module == null ? null : module.getTitle(),
                course == null ? null : course.getId(),
                course == null ? null : course.getTitle());
    }

    /**
     * FR-LRN-02: the first published course that lists this lesson directly or through its module,
     * so the lesson page can show "Course / Module / Lesson". Null when the lesson is in no course.
     */
    private CoursePackage findVisibleCourse(Lesson lesson, LearningModule module) {
        List<PackageItem> items = new ArrayList<>(
                packageItemRepository.findByItemTypeAndItemId(PackageItemType.LESSON, lesson.getId()));
        if (module != null) {
            items.addAll(packageItemRepository.findByItemTypeAndItemId(PackageItemType.MODULE, module.getId()));
        }
        return items.stream()
                .map(PackageItem::getCoursePackage)
                .filter(Objects::nonNull)
                .filter(ContentVisibilityPolicy::isPackageVisible)
                .min(Comparator.comparing(CoursePackage::getId))
                .orElse(null);
    }

    /**
     * The student opened the lesson page and the content was shown: recorded for lesson
     * completion (lessons without quizzes complete from content + recorded videos, D25).
     */
    @Transactional
    public StudentLessonProgressResponse markLessonContentViewed(SecurityUserDetails currentUser, Long lessonId) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        Lesson lesson = studentLearningAccessService.requirePublishedLesson(lessonId);
        return studentLearningProgressService.recordLessonContentViewed(student, lesson);
    }

    /** Reveals one question's teacher-authored hint on request (D8); never for INDEPENDENT quizzes. */
    @Transactional(readOnly = true)
    public QuizHintResponse getQuestionHint(SecurityUserDetails currentUser, Long quizId, Long questionId) {
        studentLearningAccessService.requireStudent(currentUser);
        Quiz quiz = studentLearningAccessService.requirePublishedQuiz(quizId);
        QuizQuestion question = quizQuestionRepository.findById(questionId)
                .filter(candidate -> candidate.getQuiz() != null && Objects.equals(candidate.getQuiz().getId(), quiz.getId()))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Quiz question was not found."));
        if (!QuizMapper.hintsAllowed(quiz)) {
            throw new ResponseStatusException(FORBIDDEN, HINTS_NOT_AVAILABLE);
        }
        if (!QuizMapper.hasHint(question)) {
            throw new ResponseStatusException(NOT_FOUND, "This question has no hint.");
        }
        return new QuizHintResponse(question.getId(), question.getHint().strip());
    }

    private List<StudentQuizSummaryResponse> buildLessonQuizzes(Lesson lesson) {
        return quizRepository.findVisibleForStudent(
                        lesson.getId(),
                        ContentStatus.PUBLISHED,
                        ContentStatus.PUBLISHED,
                        TeacherStatus.APPROVED,
                        PageRequest.of(0, LESSON_QUIZ_LIMIT))
                .getContent().stream()
                .map(quiz -> quizMapper.toStudentQuizSummaryResponse(quiz, questionCount(quiz)))
                .toList();
    }

    private int questionCount(Quiz quiz) {
        return Math.toIntExact(quizQuestionRepository.countByQuizId(quiz.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentQuizSummaryResponse> listQuizzes(
            SecurityUserDetails currentUser,
            Long lessonId,
            int page,
            int size) {
        studentLearningAccessService.requireStudent(currentUser);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<Quiz> quizPage = quizRepository.findVisibleForStudent(
                lessonId,
                ContentStatus.PUBLISHED,
                ContentStatus.PUBLISHED,
                TeacherStatus.APPROVED,
                PageRequest.of(safePage, safeSize));
        List<StudentQuizSummaryResponse> items = quizPage.getContent().stream()
                .map(quiz -> quizMapper.toStudentQuizSummaryResponse(quiz, questionCount(quiz)))
                .toList();
        return new PageResponse<>(items, safePage, safeSize, quizPage.getTotalElements(), quizPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public StudentQuizResponse getQuiz(SecurityUserDetails currentUser, Long quizId) {
        studentLearningAccessService.requireStudent(currentUser);
        Quiz quiz = studentLearningAccessService.requirePublishedQuiz(quizId);
        List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderBySortOrderAscCreatedAtAsc(quiz.getId());
        return quizMapper.toStudentQuizResponse(
                quiz,
                questions,
                loadOptions(questions));
    }

    @Transactional
    public QuizAttemptResultResponse submitQuizAttempt(
            SecurityUserDetails currentUser,
            Long quizId,
            SubmitQuizAttemptRequest request) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        Quiz quiz = studentLearningAccessService.requirePublishedQuiz(quizId);
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
        Map<Long, List<QuizOption>> optionsByQuestionId = loadOptions(questions);
        boolean hintsAllowed = QuizMapper.hintsAllowed(quiz);

        // T32: every answer is validated before anything is written, so a rejected submission
        // leaves no partial attempt, answers, points or progress (the transaction also rolls back).
        for (QuizQuestion question : questions) {
            QuizSubmissionAnswerRequest answerRequest = answerByQuestionId.get(question.getId());
            if (answerRequest == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Every quiz question requires an answer.");
            }
            QuizScoring.validate(question, optionsByQuestionId.getOrDefault(question.getId(), List.of()), answerRequest);
        }

        int score = 0;
        int maxScore = 0;
        int correctCount = 0;
        List<QuizAttemptResultQuestionResponse> answerResults = new ArrayList<>();

        QuizAttempt savedAttempt = quizAttemptRepository.save(newSubmittedAttempt(student, quiz));

        for (QuizQuestion question : questions) {
            int questionPoints = points(question);
            maxScore += questionPoints;
            QuizSubmissionAnswerRequest answerRequest = answerByQuestionId.get(question.getId());
            List<QuizOption> options = optionsByQuestionId.getOrDefault(question.getId(), List.of());
            QuizScoring.ScoredAnswer scored = QuizScoring.score(question, options, answerRequest);
            boolean hintUsed = hintsAllowed && QuizMapper.hasHint(question) && answerRequest.hintRevealed();
            if (scored.correct()) {
                score += questionPoints;
                correctCount++;
            }

            QuizAttemptAnswer attemptAnswer = new QuizAttemptAnswer();
            attemptAnswer.setQuizAttempt(savedAttempt);
            attemptAnswer.setQuestion(question);
            attemptAnswer.setAnswerText(scored.studentAnswerText());
            attemptAnswer.setSelectedOption(scored.selectedOption());
            attemptAnswer.setCorrect(scored.correct());
            attemptAnswer.setHintUsed(hintUsed);
            quizAttemptAnswerRepository.save(attemptAnswer);

            answerResults.add(toResultQuestion(question, options, scored.studentAnswerText(),
                    scored.selectedOption(), scored.correct(), hintUsed));
        }

        savedAttempt.setScore(score);
        savedAttempt.setMaxScore(maxScore);
        quizAttemptRepository.save(savedAttempt);

        int percentage = QuizScoring.percentage(score, maxScore);
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
                // Display order, the same order as the quiz and as the attempt reopened later.
                List.copyOf(answerResults));
    }

    @Transactional(readOnly = true)
    public QuizAttemptResultResponse getQuizAttempt(SecurityUserDetails currentUser, Long attemptId) {
        studentLearningAccessService.requireStudent(currentUser);
        // The lookup is by the student's own user id, so the attempt always belongs to this student.
        QuizAttempt attempt = quizAttemptRepository.findByIdAndStudentUserId(attemptId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, QUIZ_ATTEMPT_NOT_FOUND));
        ensureQuizAttemptStillAccessible(attempt);
        List<QuizAttemptAnswer> answers = quizAttemptAnswerRepository
                .findByQuizAttemptIdOrderByCreatedAtAsc(attempt.getId());
        Map<Long, List<QuizOption>> optionsByQuestionId = loadOptions(answers.stream()
                .map(QuizAttemptAnswer::getQuestion)
                .toList());
        List<QuizAttemptResultQuestionResponse> answerResponses = answers.stream()
                .map(answer -> toResultQuestion(
                        answer.getQuestion(),
                        optionsByQuestionId.getOrDefault(answer.getQuestion().getId(), List.of()),
                        Objects.requireNonNullElse(answer.getAnswerText(), ""),
                        answer.getSelectedOption(),
                        answer.isCorrect(),
                        answer.isHintUsed()))
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
                PageRequest.of(safePage, safeSize));
        Map<Long, List<QuizOption>> optionsByQuestionId = loadOptions(wrongAnswerPage.getContent().stream()
                .map(QuizAttemptAnswer::getQuestion)
                .filter(QuizScoring::isOptionBased)
                .toList());
        List<WrongAnswerResponse> items = wrongAnswerPage.getContent().stream()
                .map(answer -> toWrongAnswerResponse(answer, optionsByQuestionId))
                .toList();
        return new PageResponse<>(items, safePage, safeSize, wrongAnswerPage.getTotalElements(), wrongAnswerPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<WrongAnswerResponse> getWrongAnswersForAttempt(SecurityUserDetails currentUser, Long attemptId) {
        QuizAttemptResultResponse attempt = getQuizAttempt(currentUser, attemptId);
        Lesson lesson = quizAttemptRepository.findByIdAndStudentUserId(attemptId, currentUser.getUserId())
                .map(found -> found.getQuiz().getLesson())
                .orElse(null);
        return attempt.answers().stream()
                .filter(answer -> !answer.correct())
                .map(answer -> toWrongAnswerResponse(attempt, answer, lesson))
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

    private void ensureQuizAttemptStillAccessible(QuizAttempt attempt) {
        if (!isQuizAttemptStillAccessible(attempt)) {
            throw new ResponseStatusException(FORBIDDEN, QUIZ_ATTEMPT_UNAVAILABLE);
        }
    }

    private static boolean isQuizAttemptStillAccessible(QuizAttempt attempt) {
        Quiz quiz = attempt == null ? null : attempt.getQuiz();
        Lesson lesson = quiz == null ? null : quiz.getLesson();
        if (lesson == null) {
            return false;
        }
        return lesson.getContentStatus() == ContentStatus.PUBLISHED;
    }

    private List<LiveClassResponse> buildLessonLiveClasses(
            StudentProfile student,
            Lesson lesson,
            Set<Long> relatedPackageIds) {
        return liveClassRepository.findRelatedToLessonOrPackages(lesson.getId(), relatedPackageIds).stream()
                .filter(liveClass -> liveClass.getLiveClassStatus() != LiveClassStatus.CANCELLED
                        && liveClass.getLiveClassStatus() != LiveClassStatus.COMPLETED)
                .map(liveClass -> {
                    // Same seat and enrolment rules as the Classes page (FR-LIVE-05).
                    long enrolledCount = liveLearningAccessService.countSeatsTaken(liveClass);
                    boolean enrolled = liveLearningAccessService.isStudentEnrolled(liveClass.getId(), student.getId());
                    boolean accessAllowed = hasLiveClassAccess(liveClass);
                    boolean hasSeat = liveClass.getMaxParticipants() == null || enrolledCount < liveClass.getMaxParticipants();
                    boolean canEnrol = !enrolled && accessAllowed && hasSeat && !LiveLearningAccessService.hasEnded(liveClass);
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

    private List<RecordedLessonVideoResponse> buildRecordedVideos(Lesson lesson, Set<Long> relatedPackageIds) {
        return recordedLessonVideoRepository.findActiveRelatedToLessonOrPackages(lesson.getId(), relatedPackageIds).stream()
                .filter(StudentLearningService::canAccessVideo)
                .map(video -> liveLearningMapper.toRecordedVideoResponse(
                        video,
                        studentRecordedVideoStreamUrl(video),
                        externalVideoUrl(video)))
                .sorted(Comparator.comparing(RecordedLessonVideoResponse::sortOrder)
                        .thenComparing(RecordedLessonVideoResponse::createdAt))
                .toList();
    }

    private static boolean hasLiveClassAccess(LiveClass liveClass) {
        return (liveClass.getLesson() == null || ContentVisibilityPolicy.isLessonVisible(liveClass.getLesson()))
                && (liveClass.getCoursePackage() == null || ContentVisibilityPolicy.isPackageVisible(liveClass.getCoursePackage()));
    }

    private static boolean canAccessVideo(RecordedLessonVideo video) {
        return ContentVisibilityPolicy.isLessonVisible(video.getLesson())
                && (video.getCoursePackage() == null || ContentVisibilityPolicy.isPackageVisible(video.getCoursePackage()));
    }

    /** Options of all the given questions in one query, grouped by question id in display order. */
    private Map<Long, List<QuizOption>> loadOptions(List<QuizQuestion> questions) {
        List<Long> questionIds = questions.stream()
                .filter(Objects::nonNull)
                .map(QuizQuestion::getId)
                .distinct()
                .toList();
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return quizMapper.groupOptionsByQuestionId(
                quizOptionRepository.findByQuestionIdInOrderBySortOrderAscCreatedAtAsc(questionIds));
    }

    private static QuizAttemptResultQuestionResponse toResultQuestion(
            QuizQuestion question,
            List<QuizOption> options,
            String studentAnswer,
            QuizOption selectedOption,
            boolean correct,
            boolean hintUsed) {
        QuizOption correctOption = QuizScoring.isOptionBased(question) ? QuizScoring.correctOption(options) : null;
        return new QuizAttemptResultQuestionResponse(
                question.getId(),
                question.getQuestionType(),
                question.getQuestionText(),
                Objects.requireNonNullElse(studentAnswer, ""),
                correctOption != null ? correctOption.getOptionText() : question.getCorrectAnswerText(),
                correct,
                question.getPoints(),
                question.getExplanation(),
                selectedOption == null ? null : selectedOption.getId(),
                correctOption == null ? null : correctOption.getId(),
                hintUsed,
                options.stream()
                        .map(option -> new QuizOptionResponse(option.getId(), option.getOptionText(), option.getSortOrder()))
                        .toList());
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
        int percentage = QuizScoring.percentage(score, maxScore);
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
                answerResponses,
                percentage,
                attempt.getQuiz().getLearningStage());
    }

    private static WrongAnswerResponse toWrongAnswerResponse(
            QuizAttemptResultResponse attempt,
            QuizAttemptResultQuestionResponse answer,
            Lesson lesson) {
        return new WrongAnswerResponse(
                attempt.attemptId(),
                answer.questionId(),
                attempt.quizTitle(),
                answer.questionText(),
                answer.questionType(),
                answer.studentAnswer(),
                answer.correctAnswer(),
                answer.explanation(),
                attempt.submittedAt(),
                attempt.quizId(),
                lesson == null ? null : lesson.getId(),
                lesson == null ? null : lesson.getTitle());
    }

    private static WrongAnswerResponse toWrongAnswerResponse(
            QuizAttemptAnswer answer,
            Map<Long, List<QuizOption>> optionsByQuestionId) {
        QuizAttempt attempt = answer.getQuizAttempt();
        QuizQuestion question = answer.getQuestion();
        Quiz quiz = question.getQuiz();
        Lesson lesson = quiz.getLesson();
        String correctAnswer = question.getCorrectAnswerText();
        if (QuizScoring.isOptionBased(question)) {
            QuizOption correctOption = QuizScoring.correctOption(
                    optionsByQuestionId.getOrDefault(question.getId(), List.of()));
            if (correctOption != null) {
                correctAnswer = correctOption.getOptionText();
            }
        }
        return new WrongAnswerResponse(
                attempt.getId(),
                question.getId(),
                quiz.getTitle(),
                question.getQuestionText(),
                question.getQuestionType(),
                Objects.requireNonNullElse(answer.getAnswerText(), ""),
                correctAnswer,
                question.getExplanation(),
                attempt.getCompletedAt(),
                quiz.getId(),
                lesson == null ? null : lesson.getId(),
                lesson == null ? null : lesson.getTitle());
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
}
