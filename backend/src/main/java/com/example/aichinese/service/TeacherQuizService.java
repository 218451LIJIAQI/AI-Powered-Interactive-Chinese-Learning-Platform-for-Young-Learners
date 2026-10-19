package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.learning.CreateQuizQuestionRequest;
import com.example.aichinese.dto.learning.CreateQuizRequest;
import com.example.aichinese.dto.learning.QuizOptionRequest;
import com.example.aichinese.dto.learning.TeacherQuizAttemptSummaryResponse;
import com.example.aichinese.dto.learning.TeacherQuizResponse;
import com.example.aichinese.dto.learning.UpdateQuizQuestionRequest;
import com.example.aichinese.dto.learning.UpdateQuizRequest;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.QuizQuestionType;
import com.example.aichinese.entity.enums.ReviewContentType;
import com.example.aichinese.mapper.QuizMapper;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.repository.QuizQuestionRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.PaginationUtils;

/**
 * Teacher-side authoring of quizzes attached to a lesson the teacher
 * owns: quiz CRUD plus question / option / answer authoring and the
 * read-side view onto past student attempts.
 *
 * <h2>Authoring invariants</h2>
 * <ul>
 *   <li>Every option-based {@link QuizQuestion} (see
 *       {@link QuizQuestionType#isOptionBased()}) must have exactly
 *       one option flagged {@code correctOption=true}; short-answer
 *       types need a correct answer text and keep no options. The
 *       create / update paths validate this before commit so the
 *       student player never sees an unanswerable question.</li>
 *   <li>Length caps ({@code MAX_QUIZ_*} / {@code MAX_QUESTION_*} /
 *       {@code MAX_OPTION_*} constants below) match the underlying
 *       column widths so a successful service-layer validation
 *       guarantees a successful DB write.</li>
 * </ul>
 *
 * <h2>Security boundary</h2>
 * <p>The teacher-facing response maps include {@code correctOption} /
 * {@code correctAnswerText} for review purposes; the student-facing
 * path uses {@link QuizMapper}'s student variants which strip those
 * fields, so a student cannot read the answer key by hitting the
 * teacher endpoint via a forged token.</p>
 */
@Service
public class TeacherQuizService {

    private static final int MAX_QUIZ_TITLE_LENGTH = 200;
    private static final int MAX_QUIZ_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_QUESTION_TEXT_LENGTH = 2000;
    private static final int MAX_ANSWER_TEXT_LENGTH = 1000;
    private static final int MAX_OPTION_TEXT_LENGTH = 1000;
    private static final int MAX_EXPLANATION_LENGTH = 2000;
    private static final int MAX_HINT_LENGTH = 1000;
    private static final int MAX_OPTIONS_PER_QUESTION = 200;
    private static final int DEFAULT_SORT_ORDER = 0;
    private static final String QUIZ_REQUEST_REQUIRED = "Quiz request is required.";
    private static final String QUESTION_REQUEST_REQUIRED = "Quiz question request is required.";
    private static final String QUIZ_NOT_FOUND = "Requested quiz was not found.";
    private static final String QUESTION_NOT_FOUND = "Requested quiz question was not found.";
    private static final String AUDIT_TARGET_QUIZ = "Quiz";
    private static final String AUDIT_TARGET_QUESTION = "QuizQuestion";
    private static final String ACTION_QUIZ_CREATE = "TEACHER_QUIZ_CREATE";
    private static final String ACTION_QUIZ_UPDATE = "TEACHER_QUIZ_UPDATE";
    private static final String ACTION_QUIZ_ARCHIVE = "TEACHER_QUIZ_ARCHIVE";
    private static final String ACTION_QUESTION_CREATE = "TEACHER_QUIZ_QUESTION_CREATE";
    private static final String ACTION_QUESTION_UPDATE = "TEACHER_QUIZ_QUESTION_UPDATE";
    private static final String ACTION_QUESTION_DELETE = "TEACHER_QUIZ_QUESTION_DELETE";
    private static final ReviewContentType REVIEW_TYPE = ReviewContentType.QUIZ;

    private final TeacherContentAccessService teacherContentAccessService;
    private final TeacherLessonService teacherLessonService;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final QuizMapper quizMapper;
    private final AuditLogService auditLogService;
    private final ContentSafetyService contentSafetyService;
    private final ContentReviewService contentReviewService;

    public TeacherQuizService(
            TeacherContentAccessService teacherContentAccessService,
            TeacherLessonService teacherLessonService,
            QuizRepository quizRepository,
            QuizQuestionRepository quizQuestionRepository,
            QuizOptionRepository quizOptionRepository,
            QuizAttemptRepository quizAttemptRepository,
            QuizAttemptAnswerRepository quizAttemptAnswerRepository,
            QuizMapper quizMapper,
            AuditLogService auditLogService,
            ContentSafetyService contentSafetyService,
            ContentReviewService contentReviewService) {
        this.teacherContentAccessService = Objects.requireNonNull(teacherContentAccessService, "teacherContentAccessService");
        this.teacherLessonService = Objects.requireNonNull(teacherLessonService, "teacherLessonService");
        this.quizRepository = Objects.requireNonNull(quizRepository, "quizRepository");
        this.quizQuestionRepository = Objects.requireNonNull(quizQuestionRepository, "quizQuestionRepository");
        this.quizOptionRepository = Objects.requireNonNull(quizOptionRepository, "quizOptionRepository");
        this.quizAttemptRepository = Objects.requireNonNull(quizAttemptRepository, "quizAttemptRepository");
        this.quizAttemptAnswerRepository = Objects.requireNonNull(quizAttemptAnswerRepository, "quizAttemptAnswerRepository");
        this.quizMapper = Objects.requireNonNull(quizMapper, "quizMapper");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
        this.contentSafetyService = Objects.requireNonNull(contentSafetyService, "contentSafetyService");
        this.contentReviewService = Objects.requireNonNull(contentReviewService, "contentReviewService");
    }

    @Transactional(readOnly = true)
    public TeacherQuizResponse getQuiz(SecurityUserDetails currentUser, Long quizId) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        return toTeacherQuizResponse(findOwnedQuiz(currentUser, quizId));
    }

    /** DRAFT/REJECTED -> SUBMITTED; a quiz needs at least one valid question (FR-QUIZ-01). */
    @Transactional
    public TeacherQuizResponse submitQuiz(SecurityUserDetails currentUser, Long quizId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Quiz quiz = findOwnedQuiz(currentUser, quizId);
        contentReviewService.submitForReview(teacherProfile.getUser(), REVIEW_TYPE, quiz);
        return toTeacherQuizResponse(quizRepository.save(quiz));
    }

    @Transactional(readOnly = true)
    public PageResponse<TeacherQuizResponse> getQuizzes(SecurityUserDetails currentUser, Long lessonId, int page, int size) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = teacherLessonService.findOwnedLesson(currentUser, lessonId);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<Quiz> quizPage = quizRepository.findByLessonIdAndContentStatusNotOrderByCreatedAtAsc(
                lesson.getId(),
                ContentStatus.ARCHIVED,
                PageRequest.of(safePage, safeSize));
        List<TeacherQuizResponse> items = quizPage.getContent().stream()
                .map(this::toTeacherQuizResponse)
                .toList();
        return new PageResponse<>(items, safePage, safeSize, quizPage.getTotalElements(), quizPage.getTotalPages());
    }

    @Transactional
    public TeacherQuizResponse createQuiz(SecurityUserDetails currentUser, Long lessonId, CreateQuizRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, QUIZ_REQUEST_REQUIRED);
        }
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = requireEditableOwnedLesson(currentUser, lessonId);

        Quiz quiz = new Quiz();
        quiz.setLesson(lesson);
        quiz.setTeacher(teacherProfile);
        applyQuizMetadata(quiz, QuizMetadata.from(request));
        // Review workflow (D4): a new quiz is a draft until it has questions and an admin approves it.
        quiz.setContentStatus(ContentStatus.DRAFT);
        quiz.setRejectionReason(null);
        quiz.setTotalPoints(0);

        Quiz saved = quizRepository.save(quiz);
        auditLogService.record(teacherProfile.getUser(), ACTION_QUIZ_CREATE, AUDIT_TARGET_QUIZ, saved.getId(),
                "Teacher created a quiz.");
        return toTeacherQuizResponse(saved);
    }

    @Transactional
    public TeacherQuizResponse updateQuiz(SecurityUserDetails currentUser, Long quizId, UpdateQuizRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, QUIZ_REQUEST_REQUIRED);
        }
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Quiz quiz = findOwnedQuiz(currentUser, quizId);
        requireEditableQuiz(quiz);

        applyQuizMetadata(quiz, QuizMetadata.from(request));
        contentReviewService.recordTeacherEdit(teacherProfile.getUser(), REVIEW_TYPE, quiz);
        Quiz saved = quizRepository.save(quiz);
        auditLogService.record(teacherProfile.getUser(), ACTION_QUIZ_UPDATE, AUDIT_TARGET_QUIZ, saved.getId(),
                "Teacher updated a quiz.");
        return toTeacherQuizResponse(saved);
    }

    @Transactional
    public void archiveQuiz(SecurityUserDetails currentUser, Long quizId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Quiz quiz = findOwnedQuiz(currentUser, quizId);
        requireLessonNotArchived(quiz.getLesson());
        if (quiz.getContentStatus() == ContentStatus.ARCHIVED) {
            return;
        }
        // Same rule as modules, lessons and courses: nothing is pulled out of the admin review queue.
        teacherContentAccessService.validateArchivableStatus(quiz.getContentStatus(), "Quiz");
        quiz.setContentStatus(ContentStatus.ARCHIVED);
        quizRepository.save(quiz);
        auditLogService.record(teacherProfile.getUser(), ACTION_QUIZ_ARCHIVE, AUDIT_TARGET_QUIZ, quiz.getId(),
                "Teacher archived a quiz.");
    }

    @Transactional
    public TeacherQuizResponse addQuizQuestion(
            SecurityUserDetails currentUser, Long quizId, CreateQuizQuestionRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, QUESTION_REQUEST_REQUIRED);
        }
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Quiz quiz = findOwnedQuiz(currentUser, quizId);
        requireEditableQuiz(quiz);
        ensureQuizQuestionsEditable(quiz.getId());
        QuizQuestion question = new QuizQuestion();
        question.setQuiz(quiz);
        applyQuizQuestion(question, request.questionType(), request.questionText(), request.correctAnswerText(),
                request.points(), request.displayOrder(), request.options(), request.explanation(), request.hint());
        contentReviewService.recordTeacherEdit(teacherProfile.getUser(), REVIEW_TYPE, quiz);
        recomputeQuizTotalPoints(quiz);
        auditLogService.record(teacherProfile.getUser(), ACTION_QUESTION_CREATE, AUDIT_TARGET_QUIZ, quiz.getId(),
                "Teacher added a quiz question.");
        return toTeacherQuizResponse(quiz);
    }

    @Transactional
    public TeacherQuizResponse updateQuizQuestion(
            SecurityUserDetails currentUser, Long questionId, UpdateQuizQuestionRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, QUESTION_REQUEST_REQUIRED);
        }
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        QuizQuestion question = findOwnedQuestion(currentUser, questionId);
        requireEditableQuiz(question.getQuiz());
        ensureQuizQuestionsEditable(question.getQuiz().getId());
        applyQuizQuestion(question, request.questionType(), request.questionText(), request.correctAnswerText(), request.points(),
                request.displayOrder(), request.options(), request.explanation(), request.hint());
        contentReviewService.recordTeacherEdit(teacherProfile.getUser(), REVIEW_TYPE, question.getQuiz());
        recomputeQuizTotalPoints(question.getQuiz());
        auditLogService.record(teacherProfile.getUser(), ACTION_QUESTION_UPDATE, AUDIT_TARGET_QUESTION, question.getId(),
                "Teacher updated a quiz question.");
        return toTeacherQuizResponse(question.getQuiz());
    }

    @Transactional
    public TeacherQuizResponse deleteQuizQuestion(SecurityUserDetails currentUser, Long questionId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        QuizQuestion question = findOwnedQuestion(currentUser, questionId);
        requireEditableQuiz(question.getQuiz());
        Quiz quiz = question.getQuiz();
        ensureQuizQuestionsEditable(quiz.getId());
        quizOptionRepository.deleteByQuestionId(question.getId());
        quizQuestionRepository.delete(question);
        contentReviewService.recordTeacherEdit(teacherProfile.getUser(), REVIEW_TYPE, quiz);
        recomputeQuizTotalPoints(quiz);
        auditLogService.record(teacherProfile.getUser(), ACTION_QUESTION_DELETE, AUDIT_TARGET_QUESTION, questionId,
                "Teacher deleted a quiz question.");
        return toTeacherQuizResponse(quiz);
    }

    @Transactional(readOnly = true)
    public PageResponse<TeacherQuizAttemptSummaryResponse> getQuizAttempts(SecurityUserDetails currentUser, Long quizId, int page, int size) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        Quiz quiz = findOwnedQuiz(currentUser, quizId);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<QuizAttempt> attemptPage = quizAttemptRepository.findByQuizIdAndSubmittedTrueOrderByCompletedAtDescCreatedAtDesc(
                quiz.getId(),
                PageRequest.of(safePage, safeSize));
        List<TeacherQuizAttemptSummaryResponse> items = attemptPage.getContent().stream()
                .map(this::toTeacherQuizAttemptSummaryResponse)
                .toList();
        return new PageResponse<>(
                items,
                safePage,
                safeSize,
                attemptPage.getTotalElements(),
                attemptPage.getTotalPages());
    }

    private TeacherQuizAttemptSummaryResponse toTeacherQuizAttemptSummaryResponse(QuizAttempt attempt) {
        StudentProfile student = attempt.getStudent();
        return new TeacherQuizAttemptSummaryResponse(
                attempt.getId(),
                student == null ? null : student.getId(),
                student != null && student.getUser() != null
                        ? student.getUser().getDisplayName()
                        : "Student",
                attempt.getScore(),
                attempt.getMaxScore(),
                attempt.getCompletedAt(),
                Objects.requireNonNullElse(LessonMasteryCalculator.attemptPercentage(attempt), 0),
                quizAttemptAnswerRepository.countByQuizAttemptIdAndHintUsedTrue(attempt.getId()));
    }

    private Lesson requireEditableOwnedLesson(SecurityUserDetails currentUser, Long lessonId) {
        Lesson lesson = teacherLessonService.findOwnedLesson(currentUser, lessonId);
        requireLessonNotArchived(lesson);
        return lesson;
    }

    /** Quiz edits follow the quiz's own review status (D24); its lesson must not be archived. */
    private void requireEditableQuiz(Quiz quiz) {
        requireLessonNotArchived(quiz.getLesson());
        contentReviewService.requireTeacherEditable(quiz, REVIEW_TYPE);
    }

    private static void requireLessonNotArchived(Lesson lesson) {
        if (lesson == null || lesson.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "Lesson cannot be edited once it has been archived.");
        }
    }

    private Quiz findOwnedQuiz(SecurityUserDetails currentUser, Long quizId) {
        return quizRepository.findByIdAndTeacherUserId(quizId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, QUIZ_NOT_FOUND));
    }

    private QuizQuestion findOwnedQuestion(SecurityUserDetails currentUser, Long questionId) {
        return quizQuestionRepository.findByIdAndQuizTeacherUserId(questionId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, QUESTION_NOT_FOUND));
    }

    private void ensureQuizQuestionsEditable(Long quizId) {
        if (quizAttemptRepository.existsByQuizIdAndSubmittedTrue(quizId)) {
            throw new ResponseStatusException(BAD_REQUEST, "Quiz questions cannot be changed after students have submitted attempts.");
        }
    }

    private void applyQuizMetadata(Quiz quiz, QuizMetadata request) {
        quiz.setTitle(contentSafetyService.requireSafeText(
                request.title(),
                MAX_QUIZ_TITLE_LENGTH,
                "Quiz title is required."));
        quiz.setDescription(contentSafetyService.optionalSafeText(
                request.description(),
                MAX_QUIZ_DESCRIPTION_LENGTH));
        quiz.setTimeLimitMinutes(request.timeLimitMinutes());
        quiz.setLearningStage(request.learningStage());
    }

    private void applyQuizQuestion(
            QuizQuestion question,
            String questionTypeRaw,
            String questionText,
            String correctAnswerText,
            Integer points,
            Integer displayOrder,
            List<QuizOptionRequest> options,
            String explanation,
            String hint) {
        QuizQuestionType questionType = QuizQuestionType.fromValue(questionTypeRaw);
        question.setQuestionType(questionType.name());
        question.setQuestionText(contentSafetyService.requireSafeText(questionText, MAX_QUESTION_TEXT_LENGTH, "Question text is required."));
        if (points == null || points <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Question points must be greater than 0.");
        }
        question.setPoints(points);
        if (displayOrder != null && displayOrder < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Question display order must be greater than or equal to 0.");
        }
        question.setSortOrder(Objects.requireNonNullElse(displayOrder, DEFAULT_SORT_ORDER));
        question.setExplanation(contentSafetyService.optionalSafeText(explanation, MAX_EXPLANATION_LENGTH));
        // D8: optional hint, shown to students only on request (never for INDEPENDENT quizzes).
        question.setHint(contentSafetyService.optionalSafeText(hint, MAX_HINT_LENGTH));

        List<QuizOptionRequest> safeOptions = validateOptions(options);
        boolean optionBased = questionType.isOptionBased();
        if (optionBased) {
            validateOptionBasedQuestion(safeOptions);
            question.setCorrectAnswerText(correctOptionText(safeOptions));
        } else if (correctAnswerText == null || correctAnswerText.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Correct answer text is required for this question type.");
        } else {
            question.setCorrectAnswerText(contentSafetyService.requireSafeText(
                    correctAnswerText,
                    MAX_ANSWER_TEXT_LENGTH,
                    "Correct answer text is required for this question type."));
        }

        QuizQuestion savedQuestion = quizQuestionRepository.save(question);
        quizOptionRepository.deleteByQuestionId(savedQuestion.getId());
        if (optionBased) {
            safeOptions.forEach(option -> quizOptionRepository.save(newQuizOption(savedQuestion, option)));
        }
    }

    private static List<QuizOptionRequest> validateOptions(List<QuizOptionRequest> options) {
        List<QuizOptionRequest> safeOptions = options == null ? List.of() : options;
        if (safeOptions.size() > MAX_OPTIONS_PER_QUESTION) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "A quiz question cannot contain more than " + MAX_OPTIONS_PER_QUESTION + " options.");
        }
        if (safeOptions.stream().anyMatch(Objects::isNull)) {
            throw new ResponseStatusException(BAD_REQUEST, "Quiz option must not be null.");
        }
        return safeOptions;
    }

    private static void validateOptionBasedQuestion(List<QuizOptionRequest> options) {
        if (options.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one answer option is required.");
        }
        long correctCount = options.stream().filter(QuizOptionRequest::correctOption).count();
        if (correctCount != 1) {
            throw new ResponseStatusException(BAD_REQUEST, "Exactly one correct answer option is required.");
        }
    }

    private String correctOptionText(List<QuizOptionRequest> options) {
        return options.stream()
                .filter(QuizOptionRequest::correctOption)
                .findFirst()
                .map(QuizOptionRequest::optionText)
                .map(optionText -> contentSafetyService.requireSafeText(
                        optionText,
                        MAX_OPTION_TEXT_LENGTH,
                        "Correct answer option text is required."))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Correct answer option text is required."));
    }

    private QuizOption newQuizOption(QuizQuestion question, QuizOptionRequest option) {
        if (option.displayOrder() != null && option.displayOrder() < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Option display order must be greater than or equal to 0.");
        }
        QuizOption quizOption = new QuizOption();
        quizOption.setQuestion(question);
        quizOption.setOptionText(contentSafetyService.requireSafeText(
                option.optionText(),
                MAX_OPTION_TEXT_LENGTH,
                "Option text is required."));
        quizOption.setCorrectOption(option.correctOption());
        quizOption.setSortOrder(Objects.requireNonNullElse(option.displayOrder(), DEFAULT_SORT_ORDER));
        return quizOption;
    }

    private void recomputeQuizTotalPoints(Quiz quiz) {
        long totalPoints = quizQuestionRepository.findByQuizIdOrderBySortOrderAscCreatedAtAsc(quiz.getId()).stream()
                .map(QuizQuestion::getPoints)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
        if (totalPoints > Integer.MAX_VALUE) {
            throw new ResponseStatusException(BAD_REQUEST, "Quiz total points are too large.");
        }
        quiz.setTotalPoints((int) totalPoints);
        quizRepository.save(quiz);
    }

    private TeacherQuizResponse toTeacherQuizResponse(Quiz quiz) {
        List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderBySortOrderAscCreatedAtAsc(quiz.getId());
        List<QuizOption> options = questions.isEmpty()
                ? List.of()
                : quizOptionRepository.findByQuestionIdInOrderBySortOrderAscCreatedAtAsc(
                        questions.stream().map(QuizQuestion::getId).toList());
        return quizMapper.toTeacherQuizResponse(
                quiz,
                questions,
                quizMapper.groupOptionsByQuestionId(options));
    }

    private record QuizMetadata(
            String title,
            String description,
            Integer timeLimitMinutes,
            LearningStage learningStage) {

        private static QuizMetadata from(CreateQuizRequest request) {
            return new QuizMetadata(
                    request.title(),
                    request.description(),
                    request.timeLimitMinutes(),
                    request.learningStage());
        }

        private static QuizMetadata from(UpdateQuizRequest request) {
            return new QuizMetadata(
                    request.title(),
                    request.description(),
                    request.timeLimitMinutes(),
                    request.learningStage());
        }
    }
}
