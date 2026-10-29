package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.dto.classes.LessonViews;
import com.example.aichinese.dto.learning.QuizSubmissionAnswerRequest;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.enums.ClassActivityType;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.QuizQuestionType;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.TeacherFeedbackRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.LessonMasteryCalculator;
import com.example.aichinese.service.QuizScoring;
import com.example.aichinese.service.RewardBadge;
import com.example.aichinese.service.RewardRules;
import com.example.aichinese.service.learn.XpService;

/**
 * Lesson quizzes of classes. An attempt starts on the server; answers are saved one by one (a student who leaves
 * keeps them) and a timed attempt past its time is submitted with what was saved. Hints are shown on request and
 * noted with the answer (never in Independent quizzes). Scoring is server-side; classes give no XP.
 */
@Service
public class ClassQuizService {

    private static final String TIME_IS_UP = "Time is up. Your quiz was submitted with the answers you saved.";
    private static final String QUIZ_NOT_FOUND = "Quiz was not found.";
    private static final String ATTEMPT_NOT_FOUND = "Quiz attempt was not found.";

    private final ClassAccessService access;
    private final ClassContentSupport content;
    private final ClassProgressService progress;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAttemptAnswerRepository answerRepository;
    private final QuizOptionRepository optionRepository;
    private final TeacherFeedbackRepository feedbackRepository;
    private final XpService xpService;

    public ClassQuizService(
            ClassAccessService access,
            ClassContentSupport content,
            ClassProgressService progress,
            QuizRepository quizRepository,
            QuizAttemptRepository attemptRepository,
            QuizAttemptAnswerRepository answerRepository,
            QuizOptionRepository optionRepository,
            TeacherFeedbackRepository feedbackRepository,
            XpService xpService) {
        this.access = access;
        this.content = content;
        this.progress = progress;
        this.quizRepository = quizRepository;
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.optionRepository = optionRepository;
        this.feedbackRepository = feedbackRepository;
        this.xpService = xpService;
    }

    private record OpenQuiz(StudentProfile student, CoursePackage coursePackage, Lesson lesson, Quiz quiz) {
    }

    private OpenQuiz openQuiz(SecurityUserDetails currentUser, Long quizId) {
        StudentProfile student = access.requireStudent(currentUser);
        Quiz quiz = quizRepository.findById(quizId)
                .filter(ClassContentSupport::isLive)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, QUIZ_NOT_FOUND));
        ClassAccessService.StudentLesson found = access.requireOpenLesson(student, quiz.getLesson().getId());
        return new OpenQuiz(student, found.coursePackage(), found.lesson(), quiz);
    }

    // ----- before and during an attempt -----

    @Transactional
    public LessonViews.QuizIntro intro(SecurityUserDetails currentUser, Long quizId) {
        OpenQuiz open = openQuiz(currentUser, quizId);
        finishExpiredAttempts(open.student(), open.lesson());
        return intro(open);
    }

    /** Resumes the open attempt, or starts one (with its end time when the quiz is timed). */
    @Transactional
    public LessonViews.QuizSession start(SecurityUserDetails currentUser, Long quizId) {
        OpenQuiz open = openQuiz(currentUser, quizId);
        finishExpiredAttempts(open.student(), open.lesson());
        if (content.questions(quizId).isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "This quiz has no questions yet.");
        }
        QuizAttempt attempt = attemptRepository
                .findByStudentIdAndQuizIdAndSubmittedFalseOrderByIdDesc(open.student().getId(), quizId).stream()
                .findFirst()
                .orElseGet(() -> {
                    QuizAttempt created = new QuizAttempt();
                    created.setQuiz(open.quiz());
                    created.setStudent(open.student());
                    LocalDateTime now = LocalDateTime.now();
                    created.setStartedAt(now);
                    Integer limit = open.quiz().getTimeLimitMinutes();
                    created.setExpiresAt(limit == null || limit <= 0 ? null : now.plusMinutes(limit));
                    return attemptRepository.save(created);
                });
        return session(open, attempt);
    }

    /**
     * Saves (or replaces) the answer to one question of the open attempt. An answer arriving after the time limit
     * submits the attempt instead (and says so): that submit is kept although the request fails.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LessonViews.SavedAnswer saveAnswer(
            SecurityUserDetails currentUser,
            Long attemptId,
            QuizSubmissionAnswerRequest request) {
        QuizAttempt attempt = requireOpenAttempt(currentUser, attemptId);
        QuizQuestion question = requireQuestion(attempt, request.questionId());
        List<QuizOption> options = optionRepository.findForQuestions(List.of(question)).getOrDefault(question.getId(), List.of());
        QuizScoring.validate(question, options, request);
        QuizAttemptAnswer answer = answerFor(attempt, question);
        if (QuizScoring.isOptionBased(question)) {
            answer.setSelectedOption(options.stream()
                    .filter(option -> option.getId().equals(request.optionId()))
                    .findFirst()
                    .orElseThrow());
            answer.setAnswerText(answer.getSelectedOption().getOptionText());
        } else {
            answer.setSelectedOption(null);
            answer.setAnswerText(request.answerText());
        }
        answerRepository.save(answer);
        return savedAnswer(answer);
    }

    /** Shows the hint of a question and notes that it was used (no hints in Independent quizzes). */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LessonViews.QuizHint hint(SecurityUserDetails currentUser, Long attemptId, Long questionId) {
        QuizAttempt attempt = requireOpenAttempt(currentUser, attemptId);
        if (attempt.getQuiz().getLearningStage() == LearningStage.INDEPENDENT) {
            throw new ResponseStatusException(BAD_REQUEST, "Hints are not available in this quiz.");
        }
        QuizQuestion question = requireQuestion(attempt, questionId);
        if (question.getHint() == null || question.getHint().isBlank()) {
            throw new ResponseStatusException(NOT_FOUND, "This question has no hint.");
        }
        QuizAttemptAnswer answer = answerFor(attempt, question);
        answer.setHintUsed(true);
        answerRepository.save(answer);
        return new LessonViews.QuizHint(questionId, question.getHint());
    }

    @Transactional
    public LessonViews.QuizResult submit(SecurityUserDetails currentUser, Long attemptId) {
        StudentProfile student = access.requireStudent(currentUser);
        QuizAttempt attempt = attemptRepository.findByIdAndStudentUserId(attemptId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ATTEMPT_NOT_FOUND));
        if (!attempt.isSubmitted()) {
            access.requireOpenLesson(student, attempt.getQuiz().getLesson().getId());
            LocalDateTime now = LocalDateTime.now();
            finish(attempt, isExpired(attempt, now) ? attempt.getExpiresAt() : now);
        }
        return result(student, attempt);
    }

    /** A submitted attempt with its review, for the result screen and Quiz results. */
    @Transactional
    public LessonViews.QuizResult result(SecurityUserDetails currentUser, Long attemptId) {
        StudentProfile student = access.requireStudent(currentUser);
        QuizAttempt attempt = attemptRepository.findByIdAndStudentUserId(attemptId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ATTEMPT_NOT_FOUND));
        access.requireStudentLesson(student, attempt.getQuiz().getLesson().getId());
        if (!attempt.isSubmitted()) {
            if (!isExpired(attempt, LocalDateTime.now())) {
                throw new ResponseStatusException(CONFLICT, "This quiz attempt has not been submitted yet.");
            }
            finish(attempt, attempt.getExpiresAt());
        }
        return result(student, attempt);
    }

    // ----- expiry -----

    /** Submits the student's attempts on the lesson's quizzes whose time ran out, with the answers they saved. */
    @Transactional
    public void finishExpiredAttempts(StudentProfile student, Lesson lesson) {
        LocalDateTime now = LocalDateTime.now();
        for (Quiz quiz : content.quizzes(lesson.getId())) {
            for (QuizAttempt attempt : attemptRepository.findByStudentIdAndQuizIdAndSubmittedFalseOrderByIdDesc(student.getId(), quiz.getId())) {
                if (isExpired(attempt, now)) {
                    finish(attempt, attempt.getExpiresAt());
                }
            }
        }
    }

    public boolean hasOpenAttempt(StudentProfile student, Quiz quiz) {
        LocalDateTime now = LocalDateTime.now();
        return attemptRepository.findByStudentIdAndQuizIdAndSubmittedFalseOrderByIdDesc(student.getId(), quiz.getId()).stream()
                .anyMatch(attempt -> !isExpired(attempt, now));
    }

    private static boolean isExpired(QuizAttempt attempt, LocalDateTime now) {
        return attempt.getExpiresAt() != null && !now.isBefore(attempt.getExpiresAt());
    }

    private QuizAttempt requireOpenAttempt(SecurityUserDetails currentUser, Long attemptId) {
        StudentProfile student = access.requireStudent(currentUser);
        QuizAttempt attempt = attemptRepository.findByIdAndStudentUserId(attemptId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ATTEMPT_NOT_FOUND));
        if (attempt.isSubmitted()) {
            throw new ResponseStatusException(CONFLICT, "This quiz attempt has already been submitted.");
        }
        access.requireOpenLesson(student, attempt.getQuiz().getLesson().getId());
        if (isExpired(attempt, LocalDateTime.now())) {
            finish(attempt, attempt.getExpiresAt());
            throw new ResponseStatusException(CONFLICT, TIME_IS_UP);
        }
        return attempt;
    }

    /** Scores every question (unanswered ones get no marks) and submits the attempt. */
    private void finish(QuizAttempt attempt, LocalDateTime completedAt) {
        List<QuizQuestion> questions = content.questions(attempt.getQuiz().getId());
        Map<Long, List<QuizOption>> options = optionRepository.findForQuestions(questions);
        Map<Long, QuizAttemptAnswer> answers = answersByQuestion(attempt);
        int score = 0;
        int maxScore = 0;
        for (QuizQuestion question : questions) {
            maxScore += question.getPoints();
            QuizAttemptAnswer answer = answers.get(question.getId());
            if (answer == null) {
                continue;
            }
            boolean correct = QuizScoring.isCorrect(question, options.getOrDefault(question.getId(), List.of()),
                    answer.getSelectedOption() == null ? null : answer.getSelectedOption().getId(), answer.getAnswerText());
            answer.setCorrect(correct);
            answerRepository.save(answer);
            if (correct) {
                score += question.getPoints();
            }
        }
        attempt.setScore(score);
        attempt.setMaxScore(maxScore);
        attempt.setSubmitted(true);
        attempt.setCompletedAt(completedAt);
        attemptRepository.save(attempt);
        progress.recordProgress(attempt.getStudent(), attempt.getQuiz().getLesson());
        // Classes give no XP, but a lesson quiz of 80% or more still earns the Quiz Star badge.
        if (maxScore > 0 && score * 100 / maxScore >= RewardRules.QUIZ_STAR_PERCENT) {
            xpService.awardBadge(attempt.getStudent(), RewardBadge.QUIZ_STAR);
        }
    }

    // ----- views -----

    private LessonViews.QuizIntro intro(OpenQuiz open) {
        List<QuizQuestion> questions = content.questions(open.quiz().getId());
        List<QuizAttempt> attempts = attemptRepository
                .findByStudentIdAndQuizIdAndSubmittedTrueOrderByCompletedAtDescIdDesc(open.student().getId(), open.quiz().getId());
        Long openAttemptId = attemptRepository
                .findByStudentIdAndQuizIdAndSubmittedFalseOrderByIdDesc(open.student().getId(), open.quiz().getId()).stream()
                .map(QuizAttempt::getId)
                .findFirst()
                .orElse(null);
        return new LessonViews.QuizIntro(
                open.quiz().getId(),
                open.quiz().getTitle(),
                stage(open.quiz()),
                questions.size(),
                questions.stream().mapToInt(QuizQuestion::getPoints).sum(),
                open.quiz().getTimeLimitMinutes(),
                hintsAvailable(open.quiz(), questions),
                open.coursePackage().getId(),
                open.coursePackage().getTitle(),
                open.lesson().getId(),
                open.lesson().getTitle(),
                best(attempts),
                openAttemptId);
    }

    private LessonViews.QuizSession session(OpenQuiz open, QuizAttempt attempt) {
        List<QuizQuestion> questions = content.questions(open.quiz().getId());
        Map<Long, List<QuizOption>> options = optionRepository.findForQuestions(questions);
        boolean hints = open.quiz().getLearningStage() != LearningStage.INDEPENDENT;
        List<LessonViews.QuizQuestion> questionViews = new ArrayList<>();
        for (QuizQuestion question : questions) {
            questionViews.add(new LessonViews.QuizQuestion(
                    question.getId(),
                    question.getQuestionType(),
                    question.getQuestionText(),
                    question.getAudioUrl(),
                    question.getImageUrl(),
                    hints && question.getHint() != null && !question.getHint().isBlank(),
                    options.getOrDefault(question.getId(), List.of()).stream()
                            .map(option -> new LessonViews.QuizOption(option.getId(), option.getOptionText()))
                            .toList()));
        }
        Long secondsLeft = attempt.getExpiresAt() == null
                ? null
                : Math.max(0, Duration.between(LocalDateTime.now(), attempt.getExpiresAt()).getSeconds());
        return new LessonViews.QuizSession(
                attempt.getId(),
                intro(open),
                attempt.getExpiresAt(),
                secondsLeft,
                questionViews,
                answersByQuestion(attempt).values().stream().map(ClassQuizService::savedAnswer).toList());
    }

    private LessonViews.QuizResult result(StudentProfile student, QuizAttempt attempt) {
        Quiz quiz = attempt.getQuiz();
        Lesson lesson = quiz.getLesson();
        CoursePackage coursePackage = content.requireClassOfLesson(lesson);
        List<QuizQuestion> questions = content.questions(quiz.getId());
        Map<Long, List<QuizOption>> options = optionRepository.findForQuestions(questions);
        Map<Long, QuizAttemptAnswer> answers = answersByQuestion(attempt);
        List<LessonViews.ReviewedQuestion> reviewed = new ArrayList<>();
        int number = 1;
        int correctCount = 0;
        int hintsUsed = 0;
        Map<String, Integer> wrongBySkill = new HashMap<>();
        for (QuizQuestion question : questions) {
            QuizAttemptAnswer answer = answers.get(question.getId());
            boolean correct = answer != null && answer.isCorrect();
            if (correct) {
                correctCount++;
            } else {
                wrongBySkill.merge(skill(question), 1, Integer::sum);
            }
            if (answer != null && answer.isHintUsed()) {
                hintsUsed++;
            }
            reviewed.add(new LessonViews.ReviewedQuestion(
                    question.getId(),
                    number++,
                    question.getQuestionText(),
                    answer == null ? null : answer.getAnswerText(),
                    correctAnswer(question, options.getOrDefault(question.getId(), List.of())),
                    correct,
                    question.getExplanation(),
                    answer != null && answer.isHintUsed()));
        }
        ClassProgressService.LessonState state = progress.lessonStates(student, List.of(lesson)).get(lesson.getId());
        LessonViews.QuizIntro intro = intro(new OpenQuiz(student, coursePackage, lesson, quiz));
        List<ClassViews.FeedbackItem> feedback = feedbackRepository
                .findByStudentIdAndCoursePackageIdOrderByCreatedAtDescIdDesc(student.getId(), coursePackage.getId()).stream()
                .filter(item -> item.getQuizAttempt() != null && item.getQuizAttempt().getId().equals(attempt.getId()))
                .map(StudentClassService::feedbackItem)
                .toList();
        return new LessonViews.QuizResult(
                attempt.getId(),
                intro,
                attempt.getCompletedAt(),
                Objects.requireNonNullElse(attempt.getScore(), 0),
                Objects.requireNonNullElse(attempt.getMaxScore(), 0),
                ClassProgressService.attemptPercent(attempt),
                intro.bestPercent(),
                hintsUsed,
                correctCount,
                questions.size() - correctCount,
                reviewed,
                ClassProgressService.status(state),
                state == null ? 0 : state.activityCount() - state.activitiesDone(),
                suggestion(lesson, wrongBySkill),
                feedback);
    }

    /** The lesson activity to practise for the skill most wrong answers are about. */
    private LessonViews.PracticeSuggestion suggestion(Lesson lesson, Map<String, Integer> wrongBySkill) {
        return wrongBySkill.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(entry -> {
                    List<LessonActivity> activities = content.activities(lesson.getId()).stream()
                            .filter(ClassContentSupport::isLive)
                            .toList();
                    ClassActivityType preferred = entry.getKey().equals("WORD_MEANINGS")
                            ? ClassActivityType.MATCHING
                            : ClassActivityType.FLASHCARDS;
                    LessonActivity activity = activities.stream()
                            .filter(candidate -> ClassContentSupport.typeOf(candidate) == preferred)
                            .findFirst()
                            .orElse(activities.isEmpty() ? null : activities.get(0));
                    return new LessonViews.PracticeSuggestion(entry.getKey(), activity == null
                            ? null
                            : new LessonViews.ActivityRef(ClassContentSupport.typeOf(activity).name(), activity.getId(), activity.getTitle()));
                })
                .orElse(null);
    }

    /** What a question practises: word meanings, pinyin and tones, listening or sentences. */
    static String skill(QuizQuestion question) {
        QuizQuestionType type = QuizQuestionType.tryFrom(question.getQuestionType()).orElse(QuizQuestionType.MULTIPLE_CHOICE);
        return switch (type) {
            case CHOOSE_PINYIN, CHOOSE_TONE -> "PINYIN_TONES";
            case LISTEN_CHOOSE -> "LISTENING";
            case FILL_BLANK, REARRANGE_SENTENCE -> "SENTENCES";
            default -> "WORD_MEANINGS";
        };
    }

    private static String correctAnswer(QuizQuestion question, List<QuizOption> options) {
        if (QuizScoring.isOptionBased(question)) {
            QuizOption correct = QuizScoring.correctOption(options);
            return correct == null ? null : correct.getOptionText();
        }
        return question.getCorrectAnswerText();
    }

    static ClassViews.AttemptRow attemptRow(QuizAttempt attempt) {
        return new ClassViews.AttemptRow(
                attempt.getId(),
                attempt.getQuiz().getId(),
                attempt.getQuiz().getTitle(),
                attempt.getQuiz().getLesson().getTitle(),
                attempt.getCompletedAt(),
                Objects.requireNonNullElse(attempt.getScore(), 0),
                Objects.requireNonNullElse(attempt.getMaxScore(), 0),
                ClassProgressService.attemptPercent(attempt));
    }

    static boolean hintsAvailable(Quiz quiz, List<QuizQuestion> questions) {
        return quiz.getLearningStage() != LearningStage.INDEPENDENT
                && questions.stream().anyMatch(question -> question.getHint() != null && !question.getHint().isBlank());
    }

    /** The best percentage among the attempts (null when there are none). */
    static Integer best(List<QuizAttempt> attempts) {
        return attempts.stream()
                .map(LessonMasteryCalculator::attemptPercentage)
                .filter(Objects::nonNull)
                .max(Integer::compare)
                .orElse(null);
    }

    private static String stage(Quiz quiz) {
        return quiz.getLearningStage() == null ? null : quiz.getLearningStage().name();
    }

    private QuizQuestion requireQuestion(QuizAttempt attempt, Long questionId) {
        return content.questions(attempt.getQuiz().getId()).stream()
                .filter(question -> question.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Question does not belong to this quiz."));
    }

    private QuizAttemptAnswer answerFor(QuizAttempt attempt, QuizQuestion question) {
        return answerRepository.findByQuizAttemptIdAndQuestionId(attempt.getId(), question.getId())
                .orElseGet(() -> {
                    QuizAttemptAnswer created = new QuizAttemptAnswer();
                    created.setQuizAttempt(attempt);
                    created.setQuestion(question);
                    return created;
                });
    }

    private Map<Long, QuizAttemptAnswer> answersByQuestion(QuizAttempt attempt) {
        return answerRepository.findByQuizAttemptIdOrderByCreatedAtAsc(attempt.getId()).stream()
                .collect(Collectors.toMap(answer -> answer.getQuestion().getId(), answer -> answer, (first, second) -> second));
    }

    private static LessonViews.SavedAnswer savedAnswer(QuizAttemptAnswer answer) {
        return new LessonViews.SavedAnswer(
                answer.getQuestion().getId(),
                answer.getSelectedOption() == null ? null : answer.getSelectedOption().getId(),
                answer.getSelectedOption() == null ? answer.getAnswerText() : null);
    }
}
