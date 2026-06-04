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
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.QuizQuestionType;
import com.example.aichinese.mapper.QuizMapper;
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
 *   <li>Every {@link QuizQuestion} of type
 *       {@link QuizQuestionType#MULTIPLE_CHOICE} must have at least
 *       one option flagged {@code isCorrect=true}; the create / update
 *       paths validate this before commit so the student player never
 *       sees an unanswerable question.</li>
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

    private final TeacherContentAccessService teacherContentAccessService;
    private final TeacherLessonService teacherLessonService;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizMapper quizMapper;
    private final AuditLogService auditLogService;
    private final StudentLearningAccessService studentLearningAccessService;
    private final ContentSafetyService contentSafetyService;

    public TeacherQuizService(
            TeacherContentAccessService teacherContentAccessService,
            TeacherLessonService teacherLessonService,
            QuizRepository quizRepository,
            QuizQuestionRepository quizQuestionRepository,
            QuizOptionRepository quizOptionRepository,
            QuizAttemptRepository quizAttemptRepository,
            QuizMapper quizMapper,
            AuditLogService auditLogService,
            StudentLearningAccessService studentLearningAccessService,
            ContentSafetyService contentSafetyService) {
        this.teacherContentAccessService = Objects.requireNonNull(teacherContentAccessService, "teacherContentAccessService");
        this.teacherLessonService = Objects.requireNonNull(teacherLessonService, "teacherLessonService");
        this.quizRepository = Objects.requireNonNull(quizRepository, "quizRepository");
        this.quizQuestionRepository = Objects.requireNonNull(quizQuestionRepository, "quizQuestionRepository");
        this.quizOptionRepository = Objects.requireNonNull(quizOptionRepository, "quizOptionRepository");
        this.quizAttemptRepository = Objects.requireNonNull(quizAttemptRepository, "quizAttemptRepository");
        this.quizMapper = Objects.requireNonNull(quizMapper, "quizMapper");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
        this.studentLearningAccessService = Objects.requireNonNull(studentLearningAccessService, "studentLearningAccessService");
        this.contentSafetyService = Objects.requireNonNull(contentSafetyService, "contentSafetyService");
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
            throw new ResponseStatusException(BAD_REQUEST, "Quiz request is required.");
        }
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = requireEditableOwnedLesson(currentUser, lessonId);

        Quiz quiz = new Quiz();
        quiz.setLesson(lesson);
        quiz.setTeacher(teacherProfile);
        quiz.setTitle(contentSafetyService.requireSafeText(request.title(), MAX_QUIZ_TITLE_LENGTH, "Quiz title is required."));
        quiz.setDescription(contentSafetyService.optionalSafeText(request.description(), MAX_QUIZ_DESCRIPTION_LENGTH));
        quiz.setTimeLimitMinutes(request.timeLimitMinutes());
        quiz.setContentStatus(lesson.getContentStatus());
        quiz.setTotalPoints(0);

        Quiz saved = quizRepository.save(quiz);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_QUIZ_CREATE", "Quiz", saved.getId(),
                "Teacher created a quiz.");
        return toTeacherQuizResponse(saved);
    }

    @Transactional
    public TeacherQuizResponse updateQuiz(SecurityUserDetails currentUser, Long quizId, UpdateQuizRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Quiz request is required.");
        }
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Quiz quiz = findOwnedQuiz(currentUser, quizId);
        teacherContentAccessService.validateEditableStatus(quiz.getLesson().getContentStatus(), "Lesson");

        quiz.setTitle(contentSafetyService.requireSafeText(request.title(), MAX_QUIZ_TITLE_LENGTH, "Quiz title is required."));
        quiz.setDescription(contentSafetyService.optionalSafeText(request.description(), MAX_QUIZ_DESCRIPTION_LENGTH));
        quiz.setTimeLimitMinutes(request.timeLimitMinutes());
        Quiz saved = quizRepository.save(quiz);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_QUIZ_UPDATE", "Quiz", saved.getId(),
                "Teacher updated a quiz.");
        return toTeacherQuizResponse(saved);
    }

    @Transactional
    public void deleteQuiz(SecurityUserDetails currentUser, Long quizId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Quiz quiz = findOwnedQuiz(currentUser, quizId);
        teacherContentAccessService.validateEditableStatus(quiz.getLesson().getContentStatus(), "Lesson");
        if (quiz.getContentStatus() == ContentStatus.ARCHIVED) {
            return;
        }
        quiz.setContentStatus(ContentStatus.ARCHIVED);
        quizRepository.save(quiz);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_QUIZ_ARCHIVE", "Quiz", quiz.getId(),
                "Teacher archived a quiz.");
    }

    @Transactional
    public TeacherQuizResponse addQuizQuestion(
            SecurityUserDetails currentUser, Long quizId, CreateQuizQuestionRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Quiz question request is required.");
        }
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Quiz quiz = findOwnedQuiz(currentUser, quizId);
        teacherContentAccessService.validateEditableStatus(quiz.getLesson().getContentStatus(), "Lesson");
        ensureQuizQuestionsEditable(quiz.getId());
        QuizQuestion question = new QuizQuestion();
        question.setQuiz(quiz);
        applyQuizQuestion(question, request.questionType(), request.questionText(), request.correctAnswerText(),
                request.points(), request.displayOrder(), request.options(), request.explanation());
        recomputeQuizTotalPoints(quiz);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_QUIZ_QUESTION_CREATE", "Quiz", quiz.getId(),
                "Teacher added a quiz question.");
        return toTeacherQuizResponse(quiz);
    }

    @Transactional
    public TeacherQuizResponse updateQuizQuestion(
            SecurityUserDetails currentUser, Long questionId, UpdateQuizQuestionRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Quiz question request is required.");
        }
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        QuizQuestion question = quizQuestionRepository.findByIdAndQuizTeacherUserId(questionId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested quiz question was not found."));
        teacherContentAccessService.validateEditableStatus(question.getQuiz().getLesson().getContentStatus(), "Lesson");
        ensureQuizQuestionsEditable(question.getQuiz().getId());
        applyQuizQuestion(question, request.questionType(), request.questionText(), request.correctAnswerText(), request.points(),
                request.displayOrder(), request.options(), request.explanation());
        recomputeQuizTotalPoints(question.getQuiz());
        auditLogService.record(teacherProfile.getUser(), "TEACHER_QUIZ_QUESTION_UPDATE", "QuizQuestion", question.getId(),
                "Teacher updated a quiz question.");
        return toTeacherQuizResponse(question.getQuiz());
    }

    @Transactional
    public TeacherQuizResponse deleteQuizQuestion(SecurityUserDetails currentUser, Long questionId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        QuizQuestion question = quizQuestionRepository.findByIdAndQuizTeacherUserId(questionId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested quiz question was not found."));
        teacherContentAccessService.validateEditableStatus(question.getQuiz().getLesson().getContentStatus(), "Lesson");
        Quiz quiz = question.getQuiz();
        ensureQuizQuestionsEditable(quiz.getId());
        quizOptionRepository.deleteByQuestionId(question.getId());
        quizQuestionRepository.delete(question);
        recomputeQuizTotalPoints(quiz);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_QUIZ_QUESTION_DELETE", "QuizQuestion", questionId,
                "Teacher deleted a quiz question.");
        return toTeacherQuizResponse(quiz);
    }

    @Transactional(readOnly = true)
    public PageResponse<TeacherQuizAttemptSummaryResponse> getQuizAttempts(SecurityUserDetails currentUser, Long quizId, int page, int size) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        Quiz quiz = findOwnedQuiz(currentUser, quizId);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        List<QuizAttempt> accessibleAttempts = quizAttemptRepository.findByQuizIdAndSubmittedTrueOrderByCompletedAtDescCreatedAtDesc(quiz.getId()).stream()
                .filter(this::hasCurrentAttemptAccess)
                .toList();
        int startIndex = PaginationUtils.startIndex(safePage, safeSize, accessibleAttempts.size());
        int endIndex = PaginationUtils.endIndex(startIndex, safeSize, accessibleAttempts.size());
        List<TeacherQuizAttemptSummaryResponse> items = accessibleAttempts.subList(startIndex, endIndex).stream()
                .map(this::toTeacherQuizAttemptSummaryResponse)
                .toList();
        return new PageResponse<>(
                items,
                safePage,
                safeSize,
                accessibleAttempts.size(),
                PaginationUtils.totalPages(accessibleAttempts.size(), safeSize));
    }

    private boolean hasCurrentAttemptAccess(QuizAttempt attempt) {
        return attempt.getStudent() == null
                || attempt.getQuiz() == null
                || attempt.getQuiz().getLesson() == null
                || studentLearningAccessService.hasLessonAccess(attempt.getStudent(), attempt.getQuiz().getLesson());
    }

    private TeacherQuizAttemptSummaryResponse toTeacherQuizAttemptSummaryResponse(QuizAttempt attempt) {
        return new TeacherQuizAttemptSummaryResponse(
                attempt.getId(),
                attempt.getStudent().getId(),
                attempt.getStudent().getUser() != null
                        ? attempt.getStudent().getUser().getDisplayName()
                        : "Student",
                attempt.getScore(),
                attempt.getMaxScore(),
                attempt.getCompletedAt());
    }

    private Lesson requireEditableOwnedLesson(SecurityUserDetails currentUser, Long lessonId) {
        Lesson lesson = teacherLessonService.findOwnedLesson(currentUser, lessonId);
        teacherContentAccessService.validateEditableStatus(lesson.getContentStatus(), "Lesson");
        return lesson;
    }

    private Quiz findOwnedQuiz(SecurityUserDetails currentUser, Long quizId) {
        return quizRepository.findByIdAndTeacherUserId(quizId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested quiz was not found."));
    }

    private void ensureQuizQuestionsEditable(Long quizId) {
        if (quizAttemptRepository.existsByQuizIdAndSubmittedTrue(quizId)) {
            throw new ResponseStatusException(BAD_REQUEST, "Quiz questions cannot be changed after students have submitted attempts.");
        }
    }

    private void applyQuizQuestion(
            QuizQuestion question,
            String questionTypeRaw,
            String questionText,
            String correctAnswerText,
            Integer points,
            Integer displayOrder,
            List<QuizOptionRequest> options,
            String explanation) {
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
        question.setSortOrder(displayOrder == null ? 0 : displayOrder);
        question.setExplanation(contentSafetyService.optionalSafeText(explanation, MAX_EXPLANATION_LENGTH));

        List<QuizOptionRequest> safeOptions = options == null ? List.of() : options;
        if (safeOptions.size() > 200) {
            throw new ResponseStatusException(BAD_REQUEST, "A quiz question cannot contain more than 200 options.");
        }
        if (safeOptions.stream().anyMatch(Objects::isNull)) {
            throw new ResponseStatusException(BAD_REQUEST, "Quiz option must not be null.");
        }
        boolean optionBased = questionType.isOptionBased();
        if (optionBased) {
            if (safeOptions.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "At least one answer option is required.");
            }
            long correctCount = safeOptions.stream().filter(QuizOptionRequest::correctOption).count();
            if (correctCount != 1) {
                throw new ResponseStatusException(BAD_REQUEST, "Exactly one correct answer option is required.");
            }
            question.setCorrectAnswerText(safeOptions.stream()
                    .filter(QuizOptionRequest::correctOption)
                    .findFirst()
                    .map(QuizOptionRequest::optionText)
                    .map(optionText -> contentSafetyService.requireSafeText(
                            optionText,
                            MAX_OPTION_TEXT_LENGTH,
                            "Correct answer option text is required."))
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Correct answer option text is required.")));
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
            safeOptions.forEach(option -> {
                if (option.displayOrder() != null && option.displayOrder() < 0) {
                    throw new ResponseStatusException(BAD_REQUEST, "Option display order must be greater than or equal to 0.");
                }
                QuizOption quizOption = new QuizOption();
                quizOption.setQuestion(savedQuestion);
                quizOption.setOptionText(contentSafetyService.requireSafeText(
                        option.optionText(),
                        MAX_OPTION_TEXT_LENGTH,
                        "Option text is required."));
                quizOption.setCorrectOption(option.correctOption());
                quizOption.setSortOrder(option.displayOrder() == null ? 0 : option.displayOrder());
                quizOptionRepository.save(quizOption);
            });
        }
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
        List<QuizOption> options = questions.stream()
                .flatMap(question -> quizOptionRepository.findByQuestionIdOrderBySortOrderAscCreatedAtAsc(question.getId()).stream())
                .toList();
        return quizMapper.toTeacherQuizResponse(
                quiz,
                questions,
                quizMapper.groupOptionsByQuestionId(options));
    }
}
