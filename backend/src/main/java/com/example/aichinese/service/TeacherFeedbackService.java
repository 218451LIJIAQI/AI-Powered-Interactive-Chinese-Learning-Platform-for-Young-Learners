package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.LiveClassEnrolment;
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
    private final LiveLearningAccessService liveLearningAccessService;
    private final ContentSafetyService contentSafetyService;

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
            LiveLearningAccessService liveLearningAccessService,
            ContentSafetyService contentSafetyService) {
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
        this.liveLearningAccessService = Objects.requireNonNull(liveLearningAccessService, "liveLearningAccessService");
        this.contentSafetyService = Objects.requireNonNull(contentSafetyService, "contentSafetyService");
    }

    @Transactional
    public TeacherFeedbackResponse createFeedback(SecurityUserDetails currentUser, CreateTeacherFeedbackRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Teacher feedback request is required.");
        }
        TeacherProfile teacher = requireTeacher(currentUser.getUserId());
        StudentProfile student = requireStudent(request.studentId());
        Lesson lesson = request.lessonId() != null ? requireTeacherLesson(request.lessonId(), currentUser.getUserId()) : null;
        QuizAttempt quizAttempt = request.quizAttemptId() != null ? requireTeacherQuizAttempt(request.quizAttemptId(), currentUser.getUserId()) : null;

        validateRelationship(student, currentUser.getUserId(), lesson, quizAttempt);
        ensureStudentCanReceiveFeedback(student, currentUser.getUserId(), linkedLesson(lesson, quizAttempt));

        TeacherFeedback feedback = new TeacherFeedback();
        feedback.setTeacher(teacher);
        feedback.setStudent(student);
        feedback.setLesson(lesson != null ? lesson : quizAttempt != null && quizAttempt.getQuiz() != null ? quizAttempt.getQuiz().getLesson() : null);
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
                "TEACHER_FEEDBACK",
                saved.getId(),
                "/student/profile",
                teacher.getUser());

        auditLogService.safeRecord(
                teacher.getUser(),
                "TEACHER_FEEDBACK_CREATED",
                "TEACHER_FEEDBACK",
                saved.getId(),
                "Teacher created student feedback.",
                null);

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
        List<TeacherFeedback> accessibleFeedback = teacherFeedbackRepository.findByStudentUserIdOrderByCreatedAtDesc(
                currentUser.getUserId()).stream()
                .filter(feedback -> isStudentFeedbackCurrentlyAccessible(student, feedback))
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
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Feedback was not found."));
        if (!isStudentFeedbackCurrentlyAccessible(student, feedback)) {
            throw new ResponseStatusException(NOT_FOUND, "Feedback was not found.");
        }
        return communicationMapper.toStudentTeacherFeedbackResponse(feedback);
    }

    @Transactional
    public TeacherFeedbackResponse updateFeedback(SecurityUserDetails currentUser, Long feedbackId, UpdateTeacherFeedbackRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Teacher feedback update request is required.");
        }
        TeacherFeedback feedback = teacherFeedbackRepository.findByIdAndTeacherUserId(feedbackId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Feedback was not found."));
        feedback.setFeedbackType(parseFeedbackType(request.feedbackType()).name());
        feedback.setFeedbackMessage(contentSafetyService.requireSafeText(
                request.message(),
                MAX_FEEDBACK_MESSAGE_LENGTH,
                "Feedback message is required."));
        auditLogService.safeRecord(
                feedback.getTeacher() != null ? feedback.getTeacher().getUser() : null,
                "TEACHER_FEEDBACK_UPDATED",
                "TEACHER_FEEDBACK",
                feedback.getId(),
                "Teacher updated feedback content.",
                null);
        return communicationMapper.toTeacherFeedbackResponse(feedback);
    }

    private void validateRelationship(StudentProfile student, Long teacherUserId, Lesson lesson, QuizAttempt quizAttempt) {
        boolean allowed = quizAttempt != null
                ? isSameId(quizAttempt.getStudent().getId(), student.getId())
                        && (lesson == null || (quizAttempt.getQuiz() != null
                                && quizAttempt.getQuiz().getLesson() != null
                                && isSameId(quizAttempt.getQuiz().getLesson().getId(), lesson.getId())))
                : lesson != null
                        ? lessonProgressRepository.existsByStudentIdAndLessonIdAndLessonTeacherUserId(student.getId(), lesson.getId(), teacherUserId)
                                || liveClassEnrolmentRepository.existsByStudentIdAndLiveClassLessonIdAndLiveClassTeacherUserId(student.getId(), lesson.getId(), teacherUserId)
                                || quizAttemptRepository.existsByStudentIdAndQuizLessonIdAndQuizTeacherUserId(student.getId(), lesson.getId(), teacherUserId)
                        : lessonProgressRepository.existsByStudentIdAndLessonTeacherUserId(student.getId(), teacherUserId)
                                || liveClassEnrolmentRepository.existsByStudentIdAndLiveClassTeacherUserId(student.getId(), teacherUserId)
                                || quizAttemptRepository.existsByStudentIdAndQuizTeacherUserId(student.getId(), teacherUserId);
        if (!allowed) {
            throw new ResponseStatusException(BAD_REQUEST, "Feedback can only be created for students linked to your content.");
        }
    }

    private void ensureStudentCanReceiveFeedback(StudentProfile student, Long teacherUserId, Lesson lesson) {
        if (lesson != null && !studentLearningAccessService.hasLessonAccess(student, lesson)) {
            throw new ResponseStatusException(BAD_REQUEST, "Feedback can only be created for students with current access to the linked lesson.");
        }
        if (lesson == null && !hasCurrentAccessibleTeacherRelationship(student, teacherUserId)) {
            throw new ResponseStatusException(BAD_REQUEST, "Feedback can only be created for students with current access to your content.");
        }
    }

    private boolean isStudentFeedbackCurrentlyAccessible(StudentProfile student, TeacherFeedback feedback) {
        Lesson lesson = linkedLesson(feedback.getLesson(), feedback.getQuizAttempt());
        if (lesson != null) {
            return studentLearningAccessService.hasLessonAccess(student, lesson);
        }
        return hasCurrentAccessibleTeacherRelationship(student, teacherUserId(feedback));
    }

    private boolean hasCurrentAccessibleTeacherRelationship(StudentProfile student, Long teacherUserId) {
        if (teacherUserId == null) {
            return false;
        }
        boolean lessonProgressAccess = lessonProgressRepository.findByStudentIdOrderByUpdatedAtDesc(student.getId()).stream()
                .map(LessonProgress::getLesson)
                .anyMatch(lesson -> isTeacherLesson(lesson, teacherUserId) && studentLearningAccessService.hasLessonAccess(student, lesson));
        boolean quizAttemptAccess = quizAttemptRepository.findByStudentIdOrderByCompletedAtDescCreatedAtDesc(student.getId()).stream()
                .map(quizAttempt -> linkedLesson(null, quizAttempt))
                .anyMatch(lesson -> isTeacherLesson(lesson, teacherUserId) && studentLearningAccessService.hasLessonAccess(student, lesson));
        boolean enrolledLiveClassAccess = liveClassEnrolmentRepository
                .findByStudentIdAndEnrolmentStatusOrderByLiveClassScheduledStartAtAsc(student.getId(), LiveClassEnrolmentStatus.ENROLLED).stream()
                .map(LiveClassEnrolment::getLiveClass)
                .anyMatch(liveClass -> isTeacherLiveClass(liveClass, teacherUserId) && hasCurrentLiveClassAccess(student, liveClass));
        boolean attendedLiveClassAccess = liveClassEnrolmentRepository
                .findByStudentIdAndEnrolmentStatusOrderByLiveClassScheduledStartAtAsc(student.getId(), LiveClassEnrolmentStatus.ATTENDED).stream()
                .map(LiveClassEnrolment::getLiveClass)
                .anyMatch(liveClass -> isTeacherLiveClass(liveClass, teacherUserId) && hasCurrentLiveClassAccess(student, liveClass));
        return lessonProgressAccess || quizAttemptAccess || enrolledLiveClassAccess || attendedLiveClassAccess;
    }

    private boolean hasCurrentLiveClassAccess(StudentProfile student, LiveClass liveClass) {
        if (liveClass == null) {
            return false;
        }
        Lesson lesson = liveClass.getLesson();
        CoursePackage coursePackage = liveClass.getCoursePackage();
        boolean lessonAccess = lesson == null || liveLearningAccessService.hasStudentLessonAccess(student, lesson);
        boolean packageAccess = coursePackage == null || liveLearningAccessService.hasStudentPackageAccess(student, coursePackage);
        return lessonAccess && packageAccess;
    }

    private Lesson linkedLesson(Lesson lesson, QuizAttempt quizAttempt) {
        if (lesson != null) {
            return lesson;
        }
        if (quizAttempt != null && quizAttempt.getQuiz() != null) {
            return quizAttempt.getQuiz().getLesson();
        }
        return null;
    }

    private boolean isTeacherLesson(Lesson lesson, Long teacherUserId) {
        return lesson != null
                && lesson.getTeacher() != null
                && lesson.getTeacher().getUser() != null
                && teacherUserId.equals(lesson.getTeacher().getUser().getId());
    }

    private boolean isTeacherLiveClass(LiveClass liveClass, Long teacherUserId) {
        return liveClass != null
                && liveClass.getTeacher() != null
                && liveClass.getTeacher().getUser() != null
                && teacherUserId.equals(liveClass.getTeacher().getUser().getId());
    }

    private Long teacherUserId(TeacherFeedback feedback) {
        return feedback.getTeacher() != null && feedback.getTeacher().getUser() != null
                ? feedback.getTeacher().getUser().getId()
                : null;
    }

    private boolean isSameId(Long left, Long right) {
        return left != null && left.equals(right);
    }

    private TeacherProfile requireTeacher(Long teacherUserId) {
        return teacherProfileRepository.findDetailedByUserId(teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));
    }

    private StudentProfile requireStudent(Long studentId) {
        return studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student profile could not be found."));
    }

    private Lesson requireTeacherLesson(Long lessonId, Long teacherUserId) {
        return lessonRepository.findByIdAndTeacherUserId(lessonId, teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lesson was not found."));
    }

    private QuizAttempt requireTeacherQuizAttempt(Long attemptId, Long teacherUserId) {
        return quizAttemptRepository.findByIdAndQuizTeacherUserId(attemptId, teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Quiz attempt was not found."));
    }

    private TeacherFeedbackType parseFeedbackType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return TeacherFeedbackType.GENERAL;
        }
        try {
            return TeacherFeedbackType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid feedback type.");
        }
    }

}
