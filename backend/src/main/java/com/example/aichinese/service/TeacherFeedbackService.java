package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.communication.CreateTeacherFeedbackRequest;
import com.example.aichinese.dto.communication.StudentTeacherFeedbackResponse;
import com.example.aichinese.dto.communication.TeacherFeedbackResponse;
import com.example.aichinese.dto.communication.UpdateTeacherFeedbackRequest;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.LiveClassEnrolment;
import com.example.aichinese.entity.PracticeSubmission;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherFeedback;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.TeacherFeedbackType;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.LiveClassEnrolmentRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherFeedbackRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.PaginationUtils;

@Service
public class TeacherFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(TeacherFeedbackService.class);

    private static final int MAX_FEEDBACK_MESSAGE_LENGTH = 2000;
    private static final String FEEDBACK_NOT_FOUND = "Feedback was not found.";
    private static final String TEACHER_PROFILE_NOT_FOUND = "Teacher profile could not be found.";
    private static final String STUDENT_PROFILE_NOT_FOUND = "Student profile could not be found.";
    private static final String LESSON_NOT_FOUND = "Lesson was not found.";
    private static final String QUIZ_ATTEMPT_NOT_FOUND = "Quiz attempt was not found.";
    private static final String TEACHER_FEEDBACK_TARGET = "TEACHER_FEEDBACK";
    private static final String ACTION_TEACHER_FEEDBACK_CREATED = "TEACHER_FEEDBACK_CREATED";
    private static final String ACTION_TEACHER_FEEDBACK_UPDATED = "TEACHER_FEEDBACK_UPDATED";
    private static final Set<LiveClassEnrolmentStatus> FEEDBACK_ELIGIBLE_ENROLMENT_STATUSES =
            EnumSet.of(LiveClassEnrolmentStatus.ENROLLED, LiveClassEnrolmentStatus.ATTENDED);

    private final TeacherFeedbackRepository teacherFeedbackRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final LiveClassEnrolmentRepository liveClassEnrolmentRepository;
    private final CommunicationMapper communicationMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final StudentLearningAccessService studentLearningAccessService;
    private final ContentSafetyService contentSafetyService;
    private final PracticeSubmissionService practiceSubmissionService;

    public TeacherFeedbackService(
            TeacherFeedbackRepository teacherFeedbackRepository,
            TeacherProfileRepository teacherProfileRepository,
            StudentProfileRepository studentProfileRepository,
            QuizAttemptRepository quizAttemptRepository,
            LessonRepository lessonRepository,
            LessonProgressRepository lessonProgressRepository,
            LiveClassEnrolmentRepository liveClassEnrolmentRepository,
            CommunicationMapper communicationMapper,
            NotificationService notificationService,
            AuditLogService auditLogService,
            StudentLearningAccessService studentLearningAccessService,
            ContentSafetyService contentSafetyService,
            PracticeSubmissionService practiceSubmissionService) {
        this.teacherFeedbackRepository = Objects.requireNonNull(teacherFeedbackRepository, "teacherFeedbackRepository");
        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "teacherProfileRepository");
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "studentProfileRepository");
        this.quizAttemptRepository = Objects.requireNonNull(quizAttemptRepository, "quizAttemptRepository");
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository");
        this.lessonProgressRepository = Objects.requireNonNull(lessonProgressRepository, "lessonProgressRepository");
        this.liveClassEnrolmentRepository = Objects.requireNonNull(liveClassEnrolmentRepository, "liveClassEnrolmentRepository");
        this.communicationMapper = Objects.requireNonNull(communicationMapper, "communicationMapper");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
        this.studentLearningAccessService = Objects.requireNonNull(studentLearningAccessService, "studentLearningAccessService");
        this.contentSafetyService = Objects.requireNonNull(contentSafetyService, "contentSafetyService");
        this.practiceSubmissionService = Objects.requireNonNull(practiceSubmissionService, "practiceSubmissionService");
    }

    @Transactional
    public TeacherFeedbackResponse createFeedback(SecurityUserDetails currentUser, CreateTeacherFeedbackRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Teacher feedback request is required.");
        }
        TeacherProfile teacher = requireTeacher(currentUser.getUserId());
        StudentProfile student = requireStudent(request.studentId());
        if (request.practiceSubmissionId() != null) {
            return createPracticeSubmissionFeedback(teacher, student, request);
        }
        Lesson lesson = request.lessonId() != null ? requireTeacherLesson(request.lessonId(), currentUser.getUserId()) : null;
        QuizAttempt quizAttempt = request.quizAttemptId() != null ? requireTeacherQuizAttempt(request.quizAttemptId(), currentUser.getUserId()) : null;
        Lesson linkedLesson = linkedLesson(lesson, quizAttempt);

        validateRelationship(student, currentUser.getUserId(), lesson, quizAttempt);
        ensureStudentCanReceiveFeedback(student, currentUser.getUserId(), linkedLesson);

        TeacherFeedback feedback = new TeacherFeedback();
        feedback.setTeacher(teacher);
        feedback.setStudent(student);
        feedback.setLesson(linkedLesson);
        feedback.setQuizAttempt(quizAttempt);
        feedback.setFeedbackType(parseFeedbackType(request.feedbackType()).name());
        feedback.setFeedbackMessage(contentSafetyService.requireSafeText(
                request.message(),
                MAX_FEEDBACK_MESSAGE_LENGTH,
                "Feedback message is required."));
        TeacherFeedback saved = teacherFeedbackRepository.save(feedback);
        log.info("Teacher feedback created: feedbackId={}, teacherId={}, studentId={}, type={}",
                saved.getId(), teacher.getId(), student.getId(), saved.getFeedbackType());

        notificationService.safeCreateNotification(
                student.getUser(),
                NotificationType.TEACHER_FEEDBACK,
                "New teacher feedback",
                "Your teacher has shared new learning feedback.",
                TEACHER_FEEDBACK_TARGET,
                saved.getId(),
                "/student/profile",
                teacher.getUser());

        auditLogService.safeRecord(
                teacher.getUser(),
                ACTION_TEACHER_FEEDBACK_CREATED,
                TEACHER_FEEDBACK_TARGET,
                saved.getId(),
                "Teacher created student feedback.",
                null);

        return communicationMapper.toTeacherFeedbackResponse(saved);
    }

    /**
     * Feedback linked to a practice submission (D7a): the teacher must be allowed to review it
     * (student on the roster or the submission's lesson is the teacher's); the submission becomes
     * REVIEWED and the student is notified.
     */
    private TeacherFeedbackResponse createPracticeSubmissionFeedback(
            TeacherProfile teacher,
            StudentProfile student,
            CreateTeacherFeedbackRequest request) {
        PracticeSubmission submission = practiceSubmissionService.requireReviewableForFeedback(
                teacher, request.practiceSubmissionId(), student);
        TeacherFeedback feedback = new TeacherFeedback();
        feedback.setTeacher(teacher);
        feedback.setStudent(submission.getStudent());
        feedback.setLesson(submission.getLesson());
        feedback.setPracticeSubmission(submission);
        feedback.setFeedbackType(parseFeedbackType(request.feedbackType()).name());
        feedback.setFeedbackMessage(contentSafetyService.requireSafeText(
                request.message(),
                MAX_FEEDBACK_MESSAGE_LENGTH,
                "Feedback message is required."));
        TeacherFeedback saved = teacherFeedbackRepository.save(feedback);
        practiceSubmissionService.markReviewed(submission);
        practiceSubmissionService.afterFeedback(teacher, submission, saved);
        return communicationMapper.toTeacherFeedbackResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<TeacherFeedbackResponse> getTeacherFeedback(SecurityUserDetails currentUser, int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<TeacherFeedback> feedbackPage = teacherFeedbackRepository.findByTeacherUserIdOrderByCreatedAtDesc(
                currentUser.getUserId(),
                PageRequest.of(safePage, safeSize));
        List<TeacherFeedbackResponse> items = feedbackPage.getContent().stream()
                .map(communicationMapper::toTeacherFeedbackResponse)
                .toList();
        return new PageResponse<>(items, safePage, safeSize, feedbackPage.getTotalElements(), feedbackPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentTeacherFeedbackResponse> getStudentFeedback(SecurityUserDetails currentUser, int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        // The relationship check loads the student's progress, attempts and enrolments, so it runs
        // once per teacher instead of once per feedback item.
        Map<Long, Boolean> relationshipByTeacherUserId = new HashMap<>();
        List<TeacherFeedback> accessibleFeedback = teacherFeedbackRepository.findByStudentUserIdOrderByCreatedAtDesc(
                currentUser.getUserId()).stream()
                .filter(feedback -> isStudentFeedbackCurrentlyAccessible(student, feedback, relationshipByTeacherUserId))
                .toList();
        List<StudentTeacherFeedbackResponse> items = accessibleFeedback.stream()
                .skip((long) safePage * safeSize)
                .limit(safeSize)
                .map(communicationMapper::toStudentTeacherFeedbackResponse)
                .toList();
        return new PageResponse<>(
                items,
                safePage,
                safeSize,
                accessibleFeedback.size(),
                PaginationUtils.totalPages(accessibleFeedback.size(), safeSize));
    }

    @Transactional(readOnly = true)
    public StudentTeacherFeedbackResponse getStudentFeedbackById(SecurityUserDetails currentUser, Long feedbackId) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        TeacherFeedback feedback = teacherFeedbackRepository.findByIdAndStudentUserId(feedbackId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, FEEDBACK_NOT_FOUND));
        if (!isStudentFeedbackCurrentlyAccessible(student, feedback, new HashMap<>())) {
            throw new ResponseStatusException(NOT_FOUND, FEEDBACK_NOT_FOUND);
        }
        return communicationMapper.toStudentTeacherFeedbackResponse(feedback);
    }

    @Transactional
    public TeacherFeedbackResponse updateFeedback(SecurityUserDetails currentUser, Long feedbackId, UpdateTeacherFeedbackRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Teacher feedback update request is required.");
        }
        TeacherFeedback feedback = teacherFeedbackRepository.findByIdAndTeacherUserId(feedbackId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, FEEDBACK_NOT_FOUND));
        feedback.setFeedbackType(parseFeedbackType(request.feedbackType()).name());
        feedback.setFeedbackMessage(contentSafetyService.requireSafeText(
                request.message(),
                MAX_FEEDBACK_MESSAGE_LENGTH,
                "Feedback message is required."));
        auditLogService.safeRecord(
                feedback.getTeacher() != null ? feedback.getTeacher().getUser() : null,
                ACTION_TEACHER_FEEDBACK_UPDATED,
                TEACHER_FEEDBACK_TARGET,
                feedback.getId(),
                "Teacher updated feedback content.",
                null);
        return communicationMapper.toTeacherFeedbackResponse(feedback);
    }

    private void validateRelationship(StudentProfile student, Long teacherUserId, Lesson lesson, QuizAttempt quizAttempt) {
        if (!hasFeedbackRelationship(student, teacherUserId, lesson, quizAttempt)) {
            throw new ResponseStatusException(BAD_REQUEST, "Feedback can only be created for students linked to your content.");
        }
    }

    private boolean hasFeedbackRelationship(StudentProfile student, Long teacherUserId, Lesson lesson, QuizAttempt quizAttempt) {
        if (quizAttempt != null) {
            return isQuizAttemptForStudentAndLesson(quizAttempt, student, lesson);
        }
        if (lesson != null) {
            return hasLessonRelationship(student.getId(), lesson.getId(), teacherUserId);
        }
        return hasAnyTeacherRelationship(student.getId(), teacherUserId);
    }

    private boolean isQuizAttemptForStudentAndLesson(QuizAttempt quizAttempt, StudentProfile student, Lesson lesson) {
        if (quizAttempt.getStudent() == null || !isSameId(quizAttempt.getStudent().getId(), student.getId())) {
            return false;
        }
        Lesson quizLesson = linkedLesson(null, quizAttempt);
        return lesson == null || (quizLesson != null && isSameId(quizLesson.getId(), lesson.getId()));
    }

    private boolean hasLessonRelationship(Long studentId, Long lessonId, Long teacherUserId) {
        return lessonProgressRepository.existsByStudentIdAndLessonIdAndLessonTeacherUserId(studentId, lessonId, teacherUserId)
                || liveClassEnrolmentRepository.existsByStudentIdAndLiveClassLessonIdAndLiveClassTeacherUserId(
                        studentId, lessonId, teacherUserId)
                || quizAttemptRepository.existsByStudentIdAndQuizLessonIdAndQuizTeacherUserId(
                        studentId, lessonId, teacherUserId);
    }

    private boolean hasAnyTeacherRelationship(Long studentId, Long teacherUserId) {
        return lessonProgressRepository.existsByStudentIdAndLessonTeacherUserId(studentId, teacherUserId)
                || liveClassEnrolmentRepository.existsByStudentIdAndLiveClassTeacherUserId(studentId, teacherUserId)
                || quizAttemptRepository.existsByStudentIdAndQuizTeacherUserId(studentId, teacherUserId);
    }

    private void ensureStudentCanReceiveFeedback(StudentProfile student, Long teacherUserId, Lesson lesson) {
        if (lesson == null && !hasCurrentAccessibleTeacherRelationship(student, teacherUserId)) {
            throw new ResponseStatusException(BAD_REQUEST, "Feedback can only be created for students with current access to your content.");
        }
    }

    private boolean isStudentFeedbackCurrentlyAccessible(
            StudentProfile student,
            TeacherFeedback feedback,
            Map<Long, Boolean> relationshipByTeacherUserId) {
        Lesson lesson = linkedLesson(feedback.getLesson(), feedback.getQuizAttempt());
        if (lesson != null) {
            return true;
        }
        Long teacherUserId = teacherUserId(feedback);
        if (teacherUserId == null) {
            return false;
        }
        return relationshipByTeacherUserId.computeIfAbsent(
                teacherUserId,
                id -> hasCurrentAccessibleTeacherRelationship(student, id));
    }

    private boolean hasCurrentAccessibleTeacherRelationship(StudentProfile student, Long teacherUserId) {
        if (teacherUserId == null) {
            return false;
        }
        boolean lessonProgressAccess = lessonProgressRepository.findByStudentIdOrderByUpdatedAtDesc(student.getId()).stream()
                .map(LessonProgress::getLesson)
                .anyMatch(lesson -> isTeacherLesson(lesson, teacherUserId));
        boolean quizAttemptAccess = quizAttemptRepository.findByStudentIdOrderByCompletedAtDescCreatedAtDesc(student.getId()).stream()
                .map(quizAttempt -> linkedLesson(null, quizAttempt))
                .anyMatch(lesson -> isTeacherLesson(lesson, teacherUserId));
        return lessonProgressAccess
                || quizAttemptAccess
                || hasAccessibleLiveClassRelationship(student, teacherUserId);
    }

    private boolean hasAccessibleLiveClassRelationship(
            StudentProfile student,
            Long teacherUserId) {
        return FEEDBACK_ELIGIBLE_ENROLMENT_STATUSES.stream()
                .anyMatch(status -> liveClassEnrolmentRepository
                        .findByStudentIdAndEnrolmentStatusOrderByLiveClassScheduledStartAtAsc(student.getId(), status).stream()
                        .map(LiveClassEnrolment::getLiveClass)
                        .anyMatch(liveClass -> isTeacherLiveClass(liveClass, teacherUserId)));
    }

    private static Lesson linkedLesson(Lesson lesson, QuizAttempt quizAttempt) {
        if (lesson != null) {
            return lesson;
        }
        if (quizAttempt != null && quizAttempt.getQuiz() != null) {
            return quizAttempt.getQuiz().getLesson();
        }
        return null;
    }

    private static boolean isTeacherLesson(Lesson lesson, Long teacherUserId) {
        return lesson != null
                && lesson.getTeacher() != null
                && lesson.getTeacher().getUser() != null
                && teacherUserId.equals(lesson.getTeacher().getUser().getId());
    }

    private static boolean isTeacherLiveClass(LiveClass liveClass, Long teacherUserId) {
        return liveClass != null
                && liveClass.getTeacher() != null
                && liveClass.getTeacher().getUser() != null
                && teacherUserId.equals(liveClass.getTeacher().getUser().getId());
    }

    private static Long teacherUserId(TeacherFeedback feedback) {
        return feedback.getTeacher() != null && feedback.getTeacher().getUser() != null
                ? feedback.getTeacher().getUser().getId()
                : null;
    }

    private static boolean isSameId(Long left, Long right) {
        return left != null && left.equals(right);
    }

    private TeacherProfile requireTeacher(Long teacherUserId) {
        return teacherProfileRepository.findDetailedByUserId(teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, TEACHER_PROFILE_NOT_FOUND));
    }

    private StudentProfile requireStudent(Long studentId) {
        return studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, STUDENT_PROFILE_NOT_FOUND));
    }

    private Lesson requireTeacherLesson(Long lessonId, Long teacherUserId) {
        return lessonRepository.findByIdAndTeacherUserId(lessonId, teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, LESSON_NOT_FOUND));
    }

    private QuizAttempt requireTeacherQuizAttempt(Long attemptId, Long teacherUserId) {
        return quizAttemptRepository.findByIdAndQuizTeacherUserId(attemptId, teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, QUIZ_ATTEMPT_NOT_FOUND));
    }

    private static TeacherFeedbackType parseFeedbackType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return TeacherFeedbackType.GENERAL;
        }
        return TeacherFeedbackType.tryFrom(rawType)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid feedback type."));
    }

}
