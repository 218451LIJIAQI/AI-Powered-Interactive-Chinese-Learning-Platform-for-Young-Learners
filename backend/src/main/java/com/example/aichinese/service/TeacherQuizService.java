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
import com.example.aichinese.util.TextUtils;

/**
 * Teacher-side authoring of quizzes attached to a lesson the teacher owns.
 */
@Service
public class TeacherQuizService {

    private final TeacherContentAccessService teacherContentAccessService;
    private final TeacherLessonService teacherLessonService;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizMapper quizMapper;
    private final AuditLogService auditLogService;
    private final StudentLearningAccessService studentLearningAccessService;

    public TeacherQuizService(
            TeacherContentAccessService teacherContentAccessService,
            TeacherLessonService teacherLessonService,
            QuizRepository quizRepository,
            QuizQuestionRepository quizQuestionRepository,
            QuizOptionRepository quizOptionRepository,
            QuizAttemptRepository quizAttemptRepository,
            QuizMapper quizMapper,
            AuditLogService auditLogService,
            StudentLearningAccessService studentLearningAccessService) {
        this.teacherContentAccessService = teacherContentAccessService;
        this.teacherLessonService = teacherLessonService;
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizOptionRepository = quizOptionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.quizMapper = quizMapper;
        this.auditLogService = auditLogService;
        this.studentLearningAccessService = studentLearningAccessService;
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
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = requireEditableOwnedLesson(currentUser, lessonId);

        Quiz quiz = new Quiz();
        quiz.setLesson(lesson);
        quiz.setTeacher(teacherProfile);
        quiz.setTitle(request.title().trim());
        quiz.setDescription(TextUtils.trimToNull(request.description()));
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
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Quiz quiz = findOwnedQuiz(currentUser, quizId);
        teacherContentAccessService.validateEditableStatus(quiz.getLesson().getContentStatus(), "Lesson");

        quiz.setTitle(request.title().trim());
        quiz.setDescription(TextUtils.trimToNull(request.description()));
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
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Quiz quiz = findOwnedQuiz(currentUser, quizId);
        teacherContentAccessService.validateEditableStatus(quiz.getLesson().getContentStatus(), "Lesson");
        ensureQuizQuestionsEditable(quiz.getId());
        applyQuizQuestion(createQuestion(quiz), request.questionType(), request.questionText(), request.correctAnswerText(),
                request.points(), request.displayOrder(), request.options(), request.explanation());
        recomputeQuizTotalPoints(quiz);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_QUIZ_QUESTION_CREATE", "Quiz", quiz.getId(),
                "Teacher added a quiz question.");
        return toTeacherQuizResponse(quiz);
    }

    @Transactional
    public TeacherQuizResponse updateQuizQuestion(
            SecurityUserDetails currentUser, Long questionId, UpdateQuizQuestionRequest request) {
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

    private QuizQuestion createQuestion(Quiz quiz) {
        QuizQuestion question = new QuizQuestion();
        question.setQuiz(quiz);
        return question;
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
        question.setQuestionText(questionText.trim());
        question.setPoints(points);
        question.setSortOrder(displayOrder == null ? 0 : displayOrder);
        question.setExplanation(TextUtils.trimToNull(explanation));

        List<QuizOptionRequest> safeOptions = options == null ? List.of() : options;
        if (questionType.isOptionBased()) {
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
                    .map(String::trim)
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Correct answer option text is required.")));
        } else if (correctAnswerText == null || correctAnswerText.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Correct answer text is required for this question type.");
        } else {
            question.setCorrectAnswerText(correctAnswerText.trim());
        }

        QuizQuestion savedQuestion = quizQuestionRepository.save(question);
        quizOptionRepository.deleteByQuestionId(savedQuestion.getId());
        if (!safeOptions.isEmpty()) {
            safeOptions.forEach(option -> {
                QuizOption quizOption = new QuizOption();
                quizOption.setQuestion(savedQuestion);
                quizOption.setOptionText(option.optionText().trim());
                quizOption.setCorrectOption(option.correctOption());
                quizOption.setSortOrder(option.displayOrder() == null ? 0 : option.displayOrder());
                quizOptionRepository.save(quizOption);
            });
        }
    }

    private void recomputeQuizTotalPoints(Quiz quiz) {
        int totalPoints = quizQuestionRepository.findByQuizIdOrderBySortOrderAscCreatedAtAsc(quiz.getId()).stream()
                .map(QuizQuestion::getPoints)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        quiz.setTotalPoints(totalPoints);
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
