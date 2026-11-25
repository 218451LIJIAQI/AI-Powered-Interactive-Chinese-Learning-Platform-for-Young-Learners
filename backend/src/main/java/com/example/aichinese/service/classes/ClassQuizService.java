package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.LessonViews;
import com.example.aichinese.dto.classes.QuizSubmissionAnswerRequest;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizAttemptAnswer;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.repository.QuizQuestionRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.service.QuizScoring;
import com.example.aichinese.service.RewardBadge;
import com.example.aichinese.service.RewardRules;
import com.example.aichinese.service.learn.HintPolicyService;
import com.example.aichinese.service.learn.XpService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/**
 * Class lesson quizzes keep saved answers on the server until the learner submits or discards the attempt.
 * Scoring is server-side and gives no XP.
 */
@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ClassQuizService {

    private static final String ATTEMPT_NOT_FOUND = "Quiz attempt was not found.";
    private static final String ALREADY_SUBMITTED = "This quiz attempt has already been submitted.";

    private final ClassAccessService access;
    private final StudentLearningAccessService studentAccess;
    private final ClassContentSupport content;
    private final ClassProgressService progress;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAttemptAnswerRepository answerRepository;
    private final QuizOptionRepository optionRepository;
    private final HintPolicyService hintPolicyService;
    private final XpService xpService;
    private final EntityManager entityManager;

    // During an attempt

    /** Resumes the open attempt, or starts one. */
    public LessonViews.QuizSession start(SecurityUserDetails currentUser, Long quizId) {
        StudentProfile student = studentAccess.requireLockedStudent(currentUser);
        Quiz quiz = quizRepository.findById(quizId)
                .filter(ClassContentSupport::isLive)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Quiz was not found."));
        ClassAccessService.StudentLesson found = access.requireOpenLesson(student, quiz.getLesson().getId());
        // Share-lock the quiz row (student_profiles -> quizzes) so concurrent lesson publishing waits before replacing questions.
        entityManager.lock(quiz, LockModeType.PESSIMISTIC_READ);
        List<QuizQuestion> questions = content.questions(quizId);
        if (questions.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "This quiz has no questions yet.");
        }
        QuizAttempt attempt = attemptRepository
                .findFirstByStudentIdAndQuizIdAndSubmittedFalseOrderByIdDesc(student.getId(), quizId)
                .orElseGet(() -> {
                    QuizAttempt created = new QuizAttempt();
                    created.setQuiz(quiz);
                    created.setStudent(student);
                    return attemptRepository.save(created);
                });
        return session(found, quiz, attempt, questions);
    }

    /** Saves (or replaces) the answer to one question of the open attempt. */
    public LessonViews.SavedAnswer saveAnswer(
            SecurityUserDetails currentUser,
            Long attemptId,
            QuizSubmissionAnswerRequest request) {
        QuizAttempt attempt = requireOpenAttempt(studentAccess.requireLockedStudent(currentUser), attemptId);
        entityManager.lock(attempt.getQuiz(), LockModeType.PESSIMISTIC_READ);
        QuizQuestion question = requireQuestion(attempt, request.questionId());
        List<QuizOption> options = request.optionId() == null ? List.of()
                : optionRepository.findInDisplayOrder(List.of(question.getId()));
        QuizOption selected = QuizScoring.validate(question, options, request);
        QuizAttemptAnswer answer = answerFor(attempt, question);
        answer.setSelectedOption(selected);
        answer.setAnswerText(selected == null ? request.answerText() : selected.getOptionText());
        answerRepository.save(answer);
        return savedAnswer(answer);
    }

    public void clearAnswer(SecurityUserDetails currentUser, Long attemptId, Long questionId) {
        QuizAttempt attempt = requireOpenAttempt(studentAccess.requireLockedStudent(currentUser), attemptId);
        entityManager.lock(attempt.getQuiz(), LockModeType.PESSIMISTIC_READ);
        QuizQuestion question = requireQuestion(attempt, questionId);
        answerRepository.findByQuizAttemptAndQuestion(attempt, question).ifPresent(answerRepository::delete);
    }

    /** Shows the hint of a question, in a quiz that allows hints. */
    @Transactional(readOnly = true)
    public LessonViews.QuizHint hint(SecurityUserDetails currentUser, Long attemptId, Long questionId) {
        StudentProfile student = studentAccess.requireStudent(currentUser);
        QuizAttempt attempt = requireOpenAttempt(student, attemptId);
        if (!attempt.getQuiz().isHintsAllowed()) {
            throw new ResponseStatusException(BAD_REQUEST, "Hints are not available in this quiz.");
        }
        hintPolicyService.requireHintsOn(student);
        QuizQuestion question = requireQuestion(attempt, questionId);
        if (question.getHint() == null || question.getHint().isBlank()) {
            throw new ResponseStatusException(NOT_FOUND, "This question has no hint.");
        }
        return new LessonViews.QuizHint(question.getHint());
    }

    /**
     * Scores and submits the attempt; submitting it again returns the same result. Read committed, one lesson step of
     * the student at a time.
     */
    public LessonViews.QuizResult submit(SecurityUserDetails currentUser, Long attemptId) {
        QuizAttempt attempt = requireOwnAttempt(studentAccess.requireLockedStudent(currentUser), attemptId);
        if (attempt.isSubmitted()) {
            return submittedResult(attempt);
        }
        Quiz quiz = attempt.getQuiz();
        Lesson lesson = quiz.getLesson();
        ClassAccessService.StudentLesson found = access.requireOpenLesson(attempt.getStudent(), lesson.getId());
        // Share-lock the quiz row so a concurrent lesson publish waits before replacing questions or pruning open answers.
        entityManager.lock(quiz, LockModeType.PESSIMISTIC_READ);
        AttemptContent contents = attemptContent(attempt);
        return result(attempt, found, finish(attempt, contents), contents);
    }

    /** A submitted attempt with its review, for the result screen and an earlier attempt's page. */
    @Transactional(readOnly = true)
    public LessonViews.QuizResult result(SecurityUserDetails currentUser, Long attemptId) {
        return submittedResult(requireOwnAttempt(studentAccess.requireStudent(currentUser), attemptId));
    }

    private LessonViews.QuizResult submittedResult(QuizAttempt attempt) {
        Lesson lesson = attempt.getQuiz().getLesson();
        ClassAccessService.StudentLesson found = access.requireStudentLesson(attempt.getStudent(), lesson.getId());
        if (!attempt.isSubmitted()) {
            throw new ResponseStatusException(CONFLICT, "This quiz attempt has not been submitted yet.");
        }
        return result(attempt, found, progress.lessonState(attempt.getStudent(), lesson), attemptContent(attempt));
    }

    /**
     * Discards the learner's open attempt with its saved answers ("Leave quiz": the answers of this attempt are not
     * kept), so the next start begins a new attempt; submitted attempts and the best score stay. Missing or non-owned
     * attempt ids are ignored idempotently without probing whether another student owns that id.
     */
    public void discard(SecurityUserDetails currentUser, Long attemptId) {
        StudentProfile student = studentAccess.requireLockedStudent(currentUser);
        QuizAttempt attempt = attemptRepository.findByIdAndStudentId(attemptId, student.getId()).orElse(null);
        if (attempt == null) {
            return;
        }
        if (attempt.isSubmitted()) {
            throw new ResponseStatusException(CONFLICT, ALREADY_SUBMITTED);
        }
        entityManager.lock(attempt.getQuiz(), LockModeType.PESSIMISTIC_READ);
        answerRepository.deleteByQuizAttempt(attempt);
        answerRepository.flush();
        attemptRepository.delete(attempt);
    }

    private QuizAttempt requireOpenAttempt(StudentProfile student, Long attemptId) {
        QuizAttempt attempt = requireOwnAttempt(student, attemptId);
        if (attempt.isSubmitted()) {
            throw new ResponseStatusException(CONFLICT, ALREADY_SUBMITTED);
        }
        access.requireOpenLesson(attempt.getStudent(), attempt.getQuiz().getLesson().getId());
        return attempt;
    }

    /** An attempt of the signed-in student, so the attempt's student is the caller's student profile. */
    private QuizAttempt requireOwnAttempt(StudentProfile student, Long attemptId) {
        return attemptRepository.findByIdAndStudentId(attemptId, student.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ATTEMPT_NOT_FOUND));
    }

    private record AttemptContent(
            List<QuizQuestion> questions,
            Map<Long, List<QuizOption>> options,
            Map<Long, QuizAttemptAnswer> answers) {
    }

    private AttemptContent attemptContent(QuizAttempt attempt) {
        List<QuizQuestion> questions = content.questions(attempt.getQuiz().getId());
        return new AttemptContent(questions, optionRepository.findForQuestions(questions), answersByQuestion(attempt));
    }

    /**
     * Scores every question, submits the attempt and returns the lesson's state after it. Like the quiz page (its Submit
     * waits for "Answer N more"), an attempt is submitted with an answer to every question: a submitted attempt is the
     * quiz's lesson step, so an empty one must not complete the lesson.
     */
    private ClassProgressService.LessonState finish(QuizAttempt attempt, AttemptContent contents) {
        if (contents.questions().isEmpty()
                || contents.questions().stream().anyMatch(question -> !contents.answers().containsKey(question.getId()))) {
            throw new ResponseStatusException(BAD_REQUEST, "Answer every question before you submit.");
        }
        long score = 0;
        long maxScore = 0;
        for (QuizQuestion question : contents.questions()) {
            maxScore += question.getPoints();
            QuizAttemptAnswer answer = contents.answers().get(question.getId());
            boolean correct = QuizScoring.isCorrect(question, contents.options().getOrDefault(question.getId(), List.of()),
                    answer.getSelectedOption() == null ? null : answer.getSelectedOption().getId(), answer.getAnswerText());
            answer.setCorrect(correct);
            if (correct) {
                score += question.getPoints();
            }
        }
        attempt.setScore(score);
        attempt.setMaxScore(maxScore);
        attempt.setSubmitted(true);
        attempt.setCompletedAt(LocalDateTime.now());
        ClassProgressService.LessonState state = progress.recordProgress(attempt.getStudent(), attempt.getQuiz().getLesson());
        // Classes give no XP, but a lesson quiz of 80% or more still earns the Quiz Star badge.
        if (ClassProgressService.attemptPercent(attempt) >= RewardRules.QUIZ_STAR_PERCENT) {
            xpService.awardBadge(attempt.getStudent(), RewardBadge.QUIZ_STAR);
        }
        return state;
    }

    // Views

    private static LessonViews.QuizIntro intro(CoursePackage coursePackage, Lesson lesson, Quiz quiz) {
        return new LessonViews.QuizIntro(
                quiz.getId(),
                coursePackage.getId(),
                coursePackage.getTitle(),
                lesson.getId(),
                lesson.getTitle());
    }

    private LessonViews.QuizSession session(
            ClassAccessService.StudentLesson found, Quiz quiz, QuizAttempt attempt, List<QuizQuestion> questions) {
        Map<Long, List<QuizOption>> options = optionRepository.findForQuestions(questions);
        boolean hints = quiz.isHintsAllowed();
        return new LessonViews.QuizSession(
                attempt.getId(),
                intro(found.coursePackage(), found.lesson(), quiz),
                questions.stream().map(question -> new LessonViews.QuizQuestion(
                        question.getId(),
                        question.getQuestionType().name(),
                        question.getQuestionText(),
                        question.getAudioUrl(),
                        question.getImageUrl(),
                        hints && question.getHint() != null && !question.getHint().isBlank(),
                        options.getOrDefault(question.getId(), List.of()).stream()
                                .map(option -> new LessonViews.QuizOption(option.getId(), option.getOptionText()))
                                .toList()))
                        .toList(),
                answerRepository.findByQuizAttemptIdOrderByCreatedAtAscIdAsc(attempt.getId()).stream()
                        .map(ClassQuizService::savedAnswer).toList());
    }

    private LessonViews.QuizResult result(
            QuizAttempt attempt,
            ClassAccessService.StudentLesson found,
            ClassProgressService.LessonState state,
            AttemptContent contents) {
        Quiz quiz = attempt.getQuiz();
        List<LessonViews.ReviewedQuestion> reviewed = new ArrayList<>();
        int number = 1;
        int correctCount = 0;
        for (QuizQuestion question : contents.questions()) {
            QuizAttemptAnswer answer = contents.answers().get(question.getId());
            boolean correct = answer != null && answer.isCorrect();
            if (correct) {
                correctCount++;
            }
            reviewed.add(new LessonViews.ReviewedQuestion(
                    question.getId(),
                    number++,
                    question.getQuestionText(),
                    answer == null ? null : answer.getAnswerText(),
                    correctAnswer(question, contents.options().getOrDefault(question.getId(), List.of())),
                    correct,
                    question.getExplanation(),
                    // The feedback of the answer the learner picked, on a wrong pick ("That's 面").
                    correct || answer == null || answer.getSelectedOption() == null
                            ? null
                            : answer.getSelectedOption().getFeedback()));
        }
        // The best of the quiz's submitted attempts, this one on a tie, and its right answers out of its questions (every
        // question had an answer when it was submitted).
        int percent = ClassProgressService.attemptPercent(attempt);
        QuizAttempt best = state.bestAttempt(quiz)
                .filter(other -> ClassProgressService.attemptPercent(other) > percent)
                .orElse(attempt);
        int bestCorrect = correctCount;
        int bestTotal = contents.questions().size();
        if (best != attempt) {
            var counts = answerRepository.countAnswers(best.getId());
            bestCorrect = counts.getCorrect();
            bestTotal = counts.getTotal();
        }
        return new LessonViews.QuizResult(
                attempt.getId(),
                intro(found.coursePackage(), found.lesson(), quiz),
                attempt.getCompletedAt(),
                percent,
                ClassProgressService.attemptPercent(best),
                bestCorrect,
                bestTotal,
                correctCount,
                contents.questions().size() - correctCount,
                reviewed,
                state.status(),
                state.activities().size() - state.activitiesDone());
    }

    private static String correctAnswer(QuizQuestion question, List<QuizOption> options) {
        if (QuizScoring.isOptionBased(question)) {
            QuizOption correct = QuizScoring.correctOption(options);
            return correct == null ? null : correct.getOptionText();
        }
        return question.getCorrectAnswerText();
    }

    private QuizQuestion requireQuestion(QuizAttempt attempt, Long questionId) {
        return questionRepository.findByIdAndQuizId(questionId, attempt.getQuiz().getId())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Question does not belong to this quiz."));
    }

    private QuizAttemptAnswer answerFor(QuizAttempt attempt, QuizQuestion question) {
        return answerRepository.findByQuizAttemptAndQuestion(attempt, question)
                .orElseGet(() -> {
                    QuizAttemptAnswer created = new QuizAttemptAnswer();
                    created.setQuizAttempt(attempt);
                    created.setQuestion(question);
                    return created;
                });
    }

    private Map<Long, QuizAttemptAnswer> answersByQuestion(QuizAttempt attempt) {
        return answerRepository.findByQuizAttemptIdOrderByCreatedAtAscIdAsc(attempt.getId()).stream()
                .collect(Collectors.toMap(answer -> answer.getQuestion().getId(), answer -> answer));
    }

    private static LessonViews.SavedAnswer savedAnswer(QuizAttemptAnswer answer) {
        return new LessonViews.SavedAnswer(
                answer.getQuestion().getId(),
                answer.getSelectedOption() == null ? null : answer.getSelectedOption().getId(),
                answer.getSelectedOption() == null ? answer.getAnswerText() : null);
    }
}
