package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.practice.PracticeSubmissionFeedbackRequest;
import com.example.aichinese.dto.practice.PracticeSubmissionResponse;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.PracticeSubmission;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherFeedback;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.PracticeAiFeedbackStatus;
import com.example.aichinese.entity.enums.PracticeReviewStatus;
import com.example.aichinese.entity.enums.PracticeSubmissionType;
import com.example.aichinese.entity.enums.TeacherFeedbackType;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.LiveClassEnrolmentRepository;
import com.example.aichinese.repository.PracticeSubmissionRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherFeedbackRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

/**
 * Practice submissions (D7 / D7a, FR-AIFB-05..07): pronunciation recordings and handwriting images
 * kept for teacher review, with the optional AI-assisted feedback stored alongside (never a score).
 *
 * <p>Access rules: a student sees only their own submissions; an approved teacher may review a
 * submission when the student is on the teacher's roster (lesson progress on, quiz attempt on, or
 * non-cancelled enrolment in the teacher's content) or when the submission's lesson belongs to the
 * teacher - any other id is reported as not found; admins may view every submission for
 * moderation (read-only, file access audited).</p>
 */
@Service
public class PracticeSubmissionService {

    static final String SUBMISSION_NOT_FOUND = "Practice submission was not found.";
    private static final int MAX_FEEDBACK_MESSAGE_LENGTH = 2000;
    private static final int MAX_TARGET_TEXT_LENGTH = 200;
    private static final String TARGET_PRACTICE_SUBMISSION = "PRACTICE_SUBMISSION";
    private static final String STUDENT_SUBMISSIONS_PATH = "/student/practice-submissions";
    private static final String STUDENT_FILE_PATH = "/api/v1/students/practice-submissions/%d/file";
    private static final String TEACHER_FILE_PATH = "/api/v1/teachers/practice-submissions/%d/file";
    private static final String ADMIN_FILE_PATH = "/api/v1/admin/practice-submissions/%d/file";
    private static final Long NO_STUDENT_ID = -1L;

    private final PracticeSubmissionRepository practiceSubmissionRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherFeedbackRepository teacherFeedbackRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final LiveClassEnrolmentRepository liveClassEnrolmentRepository;
    private final StudentLearningAccessService studentLearningAccessService;
    private final TeacherContentAccessService teacherContentAccessService;
    private final AiFileStorageService aiFileStorageService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final ContentSafetyService contentSafetyService;

    public PracticeSubmissionService(
            PracticeSubmissionRepository practiceSubmissionRepository,
            StudentProfileRepository studentProfileRepository,
            TeacherProfileRepository teacherProfileRepository,
            TeacherFeedbackRepository teacherFeedbackRepository,
            LessonProgressRepository lessonProgressRepository,
            QuizAttemptRepository quizAttemptRepository,
            LiveClassEnrolmentRepository liveClassEnrolmentRepository,
            StudentLearningAccessService studentLearningAccessService,
            TeacherContentAccessService teacherContentAccessService,
            AiFileStorageService aiFileStorageService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            ContentSafetyService contentSafetyService) {
        this.practiceSubmissionRepository = Objects.requireNonNull(practiceSubmissionRepository, "practiceSubmissionRepository");
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "studentProfileRepository");
        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "teacherProfileRepository");
        this.teacherFeedbackRepository = Objects.requireNonNull(teacherFeedbackRepository, "teacherFeedbackRepository");
        this.lessonProgressRepository = Objects.requireNonNull(lessonProgressRepository, "lessonProgressRepository");
        this.quizAttemptRepository = Objects.requireNonNull(quizAttemptRepository, "quizAttemptRepository");
        this.liveClassEnrolmentRepository = Objects.requireNonNull(liveClassEnrolmentRepository, "liveClassEnrolmentRepository");
        this.studentLearningAccessService = Objects.requireNonNull(studentLearningAccessService, "studentLearningAccessService");
        this.teacherContentAccessService = Objects.requireNonNull(teacherContentAccessService, "teacherContentAccessService");
        this.aiFileStorageService = Objects.requireNonNull(aiFileStorageService, "aiFileStorageService");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
        this.contentSafetyService = Objects.requireNonNull(contentSafetyService, "contentSafetyService");
    }

    // ===== creation (called by the AI upload endpoints, outside the provider call) =====

    /** Validates the optional lesson of an upload before the file is stored; returns the lesson id or null. */
    @Transactional(readOnly = true)
    public Long requireSubmittableLesson(Long lessonId) {
        if (lessonId == null) {
            return null;
        }
        try {
            return studentLearningAccessService.requirePublishedLesson(lessonId).getId();
        } catch (ResponseStatusException exception) {
            throw new ResponseStatusException(NOT_FOUND, "Lesson was not found.");
        }
    }

    /** Persists the submission row (review status SUBMITTED) and returns its id. */
    @Transactional
    public Long recordSubmission(
            User studentUser,
            PracticeSubmissionType type,
            UploadedFile uploadedFile,
            String targetText,
            Long lessonId,
            AiRequestStatus aiStatus,
            String aiFeedbackText) {
        StudentProfile student = studentProfileRepository.findByUserId(studentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student profile could not be found."));
        PracticeSubmission submission = new PracticeSubmission();
        submission.setStudent(student);
        submission.setSubmissionType(type);
        submission.setUploadedFile(uploadedFile);
        submission.setTargetText(TextUtils.safeTruncate(TextUtils.trimToNull(targetText), MAX_TARGET_TEXT_LENGTH));
        if (lessonId != null) {
            Lesson lesson = studentLearningAccessService.requirePublishedLesson(lessonId);
            submission.setLesson(lesson);
        }
        submission.setAiFeedbackStatus(toFeedbackStatus(aiStatus));
        submission.setAiFeedbackText(TextUtils.trimToNull(aiFeedbackText));
        submission.setReviewStatus(PracticeReviewStatus.SUBMITTED);
        submission.setSubmittedAt(LocalDateTime.now());
        return practiceSubmissionRepository.save(submission).getId();
    }

    /** AVAILABLE when the provider produced feedback, UNAVAILABLE when not supported, FAILED otherwise. */
    public static PracticeAiFeedbackStatus toFeedbackStatus(AiRequestStatus status) {
        if (status == AiRequestStatus.SUCCESS) {
            return PracticeAiFeedbackStatus.AVAILABLE;
        }
        return status == AiRequestStatus.UNAVAILABLE ? PracticeAiFeedbackStatus.UNAVAILABLE : PracticeAiFeedbackStatus.FAILED;
    }

    // ===== student =====

    @Transactional(readOnly = true)
    public PageResponse<PracticeSubmissionResponse> getStudentSubmissions(
            SecurityUserDetails currentUser, String type, int page, int size) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<PracticeSubmission> result = practiceSubmissionRepository.findForStudent(
                student.getId(), parseType(type), PageRequest.of(safePage, safeSize));
        return toPage(result, safePage, safeSize, STUDENT_FILE_PATH);
    }

    @Transactional(readOnly = true)
    public PracticeSubmissionResponse getStudentSubmission(SecurityUserDetails currentUser, Long id) {
        return toResponse(requireStudentSubmission(currentUser, id), STUDENT_FILE_PATH);
    }

    @Transactional(readOnly = true)
    public StoredFileDownload loadStudentFile(SecurityUserDetails currentUser, Long id) {
        return aiFileStorageService.loadAsDownload(requireStudentSubmission(currentUser, id).getUploadedFile());
    }

    private PracticeSubmission requireStudentSubmission(SecurityUserDetails currentUser, Long id) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        return practiceSubmissionRepository.findByIdAndStudentId(id, student.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, SUBMISSION_NOT_FOUND));
    }

    // ===== teacher =====

    @Transactional(readOnly = true)
    public PageResponse<PracticeSubmissionResponse> getTeacherSubmissions(
            SecurityUserDetails currentUser, String reviewStatus, String type, int page, int size) {
        TeacherProfile teacher = requireApprovedTeacher(currentUser);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<PracticeSubmission> result = practiceSubmissionRepository.findReviewableByTeacher(
                teacher.getId(),
                rosterStudentIds(teacher),
                parseReviewStatus(reviewStatus, PracticeReviewStatus.SUBMITTED),
                parseType(type),
                PageRequest.of(safePage, safeSize));
        return toPage(result, safePage, safeSize, TEACHER_FILE_PATH);
    }

    @Transactional(readOnly = true)
    public PracticeSubmissionResponse getTeacherSubmission(SecurityUserDetails currentUser, Long id) {
        TeacherProfile teacher = requireApprovedTeacher(currentUser);
        return toResponse(requireReviewable(teacher, id), TEACHER_FILE_PATH);
    }

    @Transactional(readOnly = true)
    public StoredFileDownload loadTeacherFile(SecurityUserDetails currentUser, Long id) {
        TeacherProfile teacher = requireApprovedTeacher(currentUser);
        return aiFileStorageService.loadAsDownload(requireReviewable(teacher, id).getUploadedFile());
    }

    /** Writes teacher feedback linked to the submission, marks it REVIEWED and notifies the student. */
    @Transactional
    public PracticeSubmissionResponse reviewSubmission(
            SecurityUserDetails currentUser, Long id, PracticeSubmissionFeedbackRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Teacher feedback request is required.");
        }
        TeacherProfile teacher = requireApprovedTeacher(currentUser);
        PracticeSubmission submission = requireReviewable(teacher, id);
        recordSubmissionFeedback(teacher, submission, request.feedbackType(), request.message());
        return toResponse(submission, TEACHER_FILE_PATH);
    }

    /**
     * Saves teacher feedback linked to a reviewable submission, marks it REVIEWED and notifies the
     * student. Shared by the review endpoint and {@code POST /teachers/feedback}.
     */
    @Transactional
    public TeacherFeedback recordSubmissionFeedback(
            TeacherProfile teacher, PracticeSubmission submission, String feedbackType, String message) {
        TeacherFeedback feedback = new TeacherFeedback();
        feedback.setTeacher(teacher);
        feedback.setStudent(submission.getStudent());
        feedback.setLesson(submission.getLesson());
        feedback.setPracticeSubmission(submission);
        feedback.setFeedbackType(parseFeedbackType(feedbackType).name());
        feedback.setFeedbackMessage(contentSafetyService.requireSafeText(
                message, MAX_FEEDBACK_MESSAGE_LENGTH, "Feedback message is required."));
        TeacherFeedback saved = teacherFeedbackRepository.save(feedback);
        markReviewed(submission);
        afterFeedback(teacher, submission, saved);
        return saved;
    }

    /**
     * Shared by {@code POST /teachers/feedback} with a {@code practiceSubmissionId}: checks that the
     * teacher may review the submission and that it belongs to the given student.
     */
    @Transactional(readOnly = true)
    public PracticeSubmission requireReviewableForFeedback(TeacherProfile teacher, Long submissionId, StudentProfile student) {
        PracticeSubmission submission = requireReviewable(teacher, submissionId);
        if (student != null && !Objects.equals(submission.getStudent().getId(), student.getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "The practice submission belongs to another student.");
        }
        return submission;
    }

    private void markReviewed(PracticeSubmission submission) {
        submission.setReviewStatus(PracticeReviewStatus.REVIEWED);
        if (submission.getReviewedAt() == null) {
            submission.setReviewedAt(LocalDateTime.now());
        }
        practiceSubmissionRepository.save(submission);
    }

    /** Student notification + audit after feedback on a submission. */
    private void afterFeedback(TeacherProfile teacher, PracticeSubmission submission, TeacherFeedback feedback) {
        notificationService.safeCreateNotification(
                submission.getStudent().getUser(),
                NotificationType.TEACHER_FEEDBACK,
                "Teacher feedback on your practice",
                "Your teacher reviewed your " + typeLabel(submission.getSubmissionType()) + " and shared feedback.",
                "TEACHER_FEEDBACK",
                feedback.getId(),
                STUDENT_SUBMISSIONS_PATH,
                teacher.getUser());
        auditLogService.safeRecord(
                teacher.getUser(),
                "PRACTICE_SUBMISSION_REVIEWED",
                TARGET_PRACTICE_SUBMISSION,
                submission.getId(),
                "Teacher reviewed a practice submission.");
    }

    private PracticeSubmission requireReviewable(TeacherProfile teacher, Long id) {
        PracticeSubmission submission = practiceSubmissionRepository.findDetailedById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, SUBMISSION_NOT_FOUND));
        if (!isReviewableBy(teacher, submission)) {
            throw new ResponseStatusException(NOT_FOUND, SUBMISSION_NOT_FOUND);
        }
        return submission;
    }

    /** D7a: the student is on the teacher's roster, or the submission's lesson is the teacher's. */
    boolean isReviewableBy(TeacherProfile teacher, PracticeSubmission submission) {
        Lesson lesson = submission.getLesson();
        if (lesson != null && lesson.getTeacher() != null && Objects.equals(lesson.getTeacher().getId(), teacher.getId())) {
            return true;
        }
        return isOnRoster(submission.getStudent().getId(), teacher.getUser().getId());
    }

    /** FR-TEACH-02 roster: viewed or attempted the teacher's lessons or quizzes, or a non-cancelled live-class enrolment. */
    boolean isOnRoster(Long studentId, Long teacherUserId) {
        return lessonProgressRepository.existsByStudentIdAndLessonTeacherUserId(studentId, teacherUserId)
                || quizAttemptRepository.existsByStudentIdAndQuizTeacherUserId(studentId, teacherUserId)
                || liveClassEnrolmentRepository.existsByStudentIdAndLiveClassTeacherUserIdAndEnrolmentStatusNot(
                        studentId, teacherUserId, LiveClassEnrolmentStatus.CANCELLED);
    }

    private Collection<Long> rosterStudentIds(TeacherProfile teacher) {
        List<Long> ids = new ArrayList<>(studentProfileRepository.findAllLinkedStudentIdsByTeacherId(teacher.getId()));
        if (ids.isEmpty()) {
            ids.add(NO_STUDENT_ID);
        }
        return ids;
    }

    private TeacherProfile requireApprovedTeacher(SecurityUserDetails currentUser) {
        teacherContentAccessService.requireApprovedTeacherAccess(currentUser);
        return teacherProfileRepository.findDetailedByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));
    }

    // ===== admin =====

    @Transactional(readOnly = true)
    public PageResponse<PracticeSubmissionResponse> getAdminSubmissions(
            String reviewStatus, String type, Long studentId, int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<PracticeSubmission> result = practiceSubmissionRepository.findForAdmin(
                parseReviewStatus(reviewStatus, null), parseType(type), studentId, PageRequest.of(safePage, safeSize));
        return toPage(result, safePage, safeSize, ADMIN_FILE_PATH);
    }

    @Transactional(readOnly = true)
    public PracticeSubmissionResponse getAdminSubmission(Long id) {
        return toResponse(requireAny(id), ADMIN_FILE_PATH);
    }

    /** Read-write transaction: the audit row is inserted here and must not run on a read-only connection. */
    @Transactional
    public StoredFileDownload loadAdminFile(SecurityUserDetails currentUser, Long id) {
        PracticeSubmission submission = requireAny(id);
        auditLogService.record(currentUser, "PRACTICE_SUBMISSION_FILE_VIEWED", TARGET_PRACTICE_SUBMISSION, id,
                "Admin viewed a practice submission file for moderation.");
        return aiFileStorageService.loadAsDownload(submission.getUploadedFile());
    }

    private PracticeSubmission requireAny(Long id) {
        return practiceSubmissionRepository.findDetailedById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, SUBMISSION_NOT_FOUND));
    }

    // ===== mapping =====

    private PageResponse<PracticeSubmissionResponse> toPage(
            Page<PracticeSubmission> result, int page, int size, String filePathPattern) {
        Map<Long, List<PracticeSubmissionResponse.PracticeSubmissionFeedbackResponse>> feedback =
                feedbackBySubmission(result.getContent().stream().map(PracticeSubmission::getId).toList());
        List<PracticeSubmissionResponse> items = result.getContent().stream()
                .map(submission -> toResponse(submission, filePathPattern, feedback.getOrDefault(submission.getId(), List.of())))
                .toList();
        return new PageResponse<>(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    private PracticeSubmissionResponse toResponse(PracticeSubmission submission, String filePathPattern) {
        return toResponse(submission, filePathPattern,
                feedbackBySubmission(List.of(submission.getId())).getOrDefault(submission.getId(), List.of()));
    }

    private Map<Long, List<PracticeSubmissionResponse.PracticeSubmissionFeedbackResponse>> feedbackBySubmission(
            List<Long> submissionIds) {
        Map<Long, List<PracticeSubmissionResponse.PracticeSubmissionFeedbackResponse>> result = new LinkedHashMap<>();
        if (submissionIds.isEmpty()) {
            return result;
        }
        for (TeacherFeedback feedback : teacherFeedbackRepository.findByPracticeSubmissionIdInOrderByCreatedAtAsc(submissionIds)) {
            result.computeIfAbsent(feedback.getPracticeSubmission().getId(), ignored -> new ArrayList<>())
                    .add(new PracticeSubmissionResponse.PracticeSubmissionFeedbackResponse(
                            feedback.getId(),
                            feedback.getTeacher() != null && feedback.getTeacher().getUser() != null
                                    ? feedback.getTeacher().getUser().getDisplayName()
                                    : null,
                            feedback.getFeedbackType(),
                            feedback.getFeedbackMessage(),
                            feedback.getCreatedAt()));
        }
        return result;
    }

    private static PracticeSubmissionResponse toResponse(
            PracticeSubmission submission,
            String filePathPattern,
            List<PracticeSubmissionResponse.PracticeSubmissionFeedbackResponse> feedback) {
        StudentProfile student = submission.getStudent();
        Lesson lesson = submission.getLesson();
        UploadedFile file = submission.getUploadedFile();
        return new PracticeSubmissionResponse(
                submission.getId(),
                submission.getSubmissionType().name(),
                student == null ? null : student.getId(),
                student == null || student.getUser() == null ? null : student.getUser().getDisplayName(),
                lesson == null ? null : lesson.getId(),
                lesson == null ? null : lesson.getTitle(),
                submission.getTargetText(),
                submission.getAiFeedbackStatus().name(),
                submission.getAiFeedbackText(),
                AiAvailabilityService.MEDIA_FEEDBACK_NOTICE,
                submission.getReviewStatus().name(),
                submission.getSubmittedAt(),
                submission.getReviewedAt(),
                file == null ? null : file.getFileName(),
                file == null ? null : file.getContentType(),
                file == null ? null : file.getFileSize(),
                String.format(Locale.ROOT, filePathPattern, submission.getId()),
                feedback);
    }

    private static String typeLabel(PracticeSubmissionType type) {
        return type == PracticeSubmissionType.PRONUNCIATION_AUDIO ? "pronunciation recording" : "handwriting practice";
    }

    private static PracticeSubmissionType parseType(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.strip())) {
            return null;
        }
        return PracticeSubmissionType.tryFrom(value)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST,
                        "Submission type must be PRONUNCIATION_AUDIO or HANDWRITING_IMAGE."));
    }

    private static PracticeReviewStatus parseReviewStatus(String value, PracticeReviewStatus defaultStatus) {
        if (value == null || value.isBlank()) {
            return defaultStatus;
        }
        if ("ALL".equalsIgnoreCase(value.strip())) {
            return null;
        }
        return PracticeReviewStatus.tryFrom(value)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST,
                        "Review status must be SUBMITTED, REVIEWED or ALL."));
    }

    static TeacherFeedbackType parseFeedbackType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return TeacherFeedbackType.GENERAL;
        }
        return TeacherFeedbackType.tryFrom(rawType)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid feedback type."));
    }
}
