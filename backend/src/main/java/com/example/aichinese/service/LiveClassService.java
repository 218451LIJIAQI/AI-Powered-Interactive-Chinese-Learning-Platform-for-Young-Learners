package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.PlatformProperties;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.learning.CreateLiveClassRequest;
import com.example.aichinese.dto.learning.JoinLiveClassResponse;
import com.example.aichinese.dto.learning.LiveClassEnrolmentResponse;
import com.example.aichinese.dto.learning.LiveClassResponse;
import com.example.aichinese.dto.learning.RescheduleLiveClassRequest;
import com.example.aichinese.dto.learning.UpdateLiveClassRequest;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.LiveClassEnrolment;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.repository.LiveClassEnrolmentRepository;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.PaginationUtils;

@Service
public class LiveClassService {

    private static final Set<LiveClassStatus> STUDENT_VISIBLE_STATUSES = LiveClassStatus.STUDENT_VISIBLE;
    private static final int MAX_LIVE_CLASS_TITLE_LENGTH = 200;
    private static final int MAX_LIVE_CLASS_DESCRIPTION_LENGTH = 1000;

    private final LiveClassRepository liveClassRepository;
    private final LiveClassEnrolmentRepository liveClassEnrolmentRepository;
    private final LiveLearningAccessService liveLearningAccessService;
    private final LiveLearningMapper liveLearningMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final ContentSafetyService contentSafetyService;
    private final PlatformProperties platformProperties;

    public LiveClassService(
            LiveClassRepository liveClassRepository,
            LiveClassEnrolmentRepository liveClassEnrolmentRepository,
            LiveLearningAccessService liveLearningAccessService,
            LiveLearningMapper liveLearningMapper,
            NotificationService notificationService,
            AuditLogService auditLogService,
            ContentSafetyService contentSafetyService,
            PlatformProperties platformProperties) {
        this.liveClassRepository = Objects.requireNonNull(liveClassRepository, "liveClassRepository");
        this.liveClassEnrolmentRepository = Objects.requireNonNull(liveClassEnrolmentRepository, "liveClassEnrolmentRepository");
        this.liveLearningAccessService = Objects.requireNonNull(liveLearningAccessService, "liveLearningAccessService");
        this.liveLearningMapper = Objects.requireNonNull(liveLearningMapper, "liveLearningMapper");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
        this.contentSafetyService = Objects.requireNonNull(contentSafetyService, "contentSafetyService");
        this.platformProperties = Objects.requireNonNull(platformProperties, "platformProperties");
    }

    @Transactional
    public LiveClassResponse createLiveClass(SecurityUserDetails currentUser, CreateLiveClassRequest request) {
        TeacherProfile teacher = liveLearningAccessService.requireApprovedTeacher(currentUser);
        LiveClass liveClass = new LiveClass();
        applyLiveClassUpdate(currentUser, liveClass, request.title(), request.description(), request.lessonId(), request.packageId(),
                request.startTime(), request.endTime(), request.timeZone(), request.meetingLink(), request.meetingPlatform(),
                request.status(), request.maxParticipants());
        liveClass.setTeacher(teacher);
        LiveClass saved = liveClassRepository.save(liveClass);

        auditLogService.record(
                teacher.getUser(),
                "TEACHER_LIVE_CLASS_CREATE",
                "LiveClass",
                saved.getId(),
                "Teacher created a live class.");

        return toTeacherResponse(saved);
    }

    @Transactional
    public LiveClassResponse updateLiveClass(SecurityUserDetails currentUser, Long liveClassId, UpdateLiveClassRequest request) {
        LiveClass liveClass = liveLearningAccessService.requireTeacherOwnedLiveClass(currentUser, liveClassId);
        ensureLiveClassEditable(liveClass);
        applyLiveClassUpdate(currentUser, liveClass, request.title(), request.description(), request.lessonId(), request.packageId(),
                request.startTime(), request.endTime(), request.timeZone(), request.meetingLink(), request.meetingPlatform(),
                request.status(), request.maxParticipants());
        LiveClass saved = liveClassRepository.save(liveClass);

        auditLogService.record(
                saved.getTeacher().getUser(),
                "TEACHER_LIVE_CLASS_UPDATE",
                "LiveClass",
                saved.getId(),
                "Teacher updated a live class.");

        return toTeacherResponse(saved);
    }

    @Transactional
    public LiveClassResponse updateStatus(SecurityUserDetails currentUser, Long liveClassId, String statusValue) {
        LiveClass liveClass = liveLearningAccessService.requireTeacherOwnedLiveClass(currentUser, liveClassId);
        LiveClassStatus requestedStatus = liveLearningAccessService.parseLiveClassStatus(statusValue, liveClass.getLiveClassStatus());
        boolean changed = applyLiveClassStatusTransition(liveClass, requestedStatus);
        if (!changed) {
            return toTeacherResponse(liveClass);
        }
        LiveClass saved = liveClassRepository.save(liveClass);

        auditLogService.record(
                saved.getTeacher().getUser(),
                "TEACHER_LIVE_CLASS_STATUS",
                "LiveClass",
                saved.getId(),
                "Teacher updated a live class status to " + saved.getLiveClassStatus().name() + ".");

        return toTeacherResponse(saved);
    }

    @Transactional
    public LiveClassResponse cancelLiveClass(SecurityUserDetails currentUser, Long liveClassId) {
        LiveClass liveClass = liveLearningAccessService.requireTeacherOwnedLiveClass(currentUser, liveClassId);
        boolean changed = applyLiveClassStatusTransition(liveClass, LiveClassStatus.CANCELLED);
        if (!changed) {
            return toTeacherResponse(liveClass);
        }
        LiveClass saved = liveClassRepository.save(liveClass);
        notifyEnrolledStudents(saved, "Live class cancelled", saved.getTitle() + " has been cancelled.", "/student/classes");

        auditLogService.record(
                saved.getTeacher().getUser(),
                "TEACHER_LIVE_CLASS_CANCEL",
                "LiveClass",
                saved.getId(),
                "Teacher cancelled a live class.");

        return toTeacherResponse(saved);
    }

    @Transactional
    public LiveClassResponse rescheduleLiveClass(SecurityUserDetails currentUser, Long liveClassId, RescheduleLiveClassRequest request) {
        LiveClass liveClass = liveLearningAccessService.requireTeacherOwnedLiveClass(currentUser, liveClassId);
        liveLearningAccessService.validateLiveClassTiming(request.startTime(), request.endTime());
        applyLiveClassStatusTransition(liveClass, LiveClassStatus.RESCHEDULED);
        liveClass.setScheduledStartAt(request.startTime());
        liveClass.setScheduledEndAt(request.endTime());
        liveClass.setTimeZone(resolveTimeZone(request.timeZone()));
        LiveClass saved = liveClassRepository.save(liveClass);
        notifyEnrolledStudents(saved, "Live class rescheduled", saved.getTitle() + " has a new schedule.", "/student/classes");

        auditLogService.record(
                saved.getTeacher().getUser(),
                "TEACHER_LIVE_CLASS_RESCHEDULE",
                "LiveClass",
                saved.getId(),
                "Teacher rescheduled a live class.");

        return toTeacherResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<LiveClassResponse> getTeacherLiveClasses(SecurityUserDetails currentUser, int page, int size) {
        liveLearningAccessService.requireApprovedTeacher(currentUser);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<LiveClass> liveClassPage = liveClassRepository.findByTeacherUserIdOrderByScheduledStartAtAsc(
                currentUser.getUserId(),
                PageRequest.of(safePage, safeSize));
        return mapLiveClassPage(liveClassPage, safePage, safeSize, this::toTeacherResponse);
    }

    @Transactional(readOnly = true)
    public LiveClassResponse getTeacherLiveClass(SecurityUserDetails currentUser, Long liveClassId) {
        return toTeacherResponse(liveLearningAccessService.requireTeacherOwnedLiveClass(currentUser, liveClassId));
    }

    @Transactional(readOnly = true)
    public PageResponse<LiveClassEnrolmentResponse> getTeacherEnrolments(
            SecurityUserDetails currentUser,
            Long liveClassId,
            int page,
            int size) {
        liveLearningAccessService.requireTeacherOwnedLiveClass(currentUser, liveClassId);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        List<LiveClassEnrolment> accessibleEnrolments = liveClassEnrolmentRepository.findByLiveClassIdOrderByEnrolledAtAsc(liveClassId).stream()
                .filter(this::hasCurrentEnrolmentAccess)
                .toList();
        int startIndex = PaginationUtils.startIndex(safePage, safeSize, accessibleEnrolments.size());
        int endIndex = PaginationUtils.endIndex(startIndex, safeSize, accessibleEnrolments.size());
        return new PageResponse<>(
                accessibleEnrolments.subList(startIndex, endIndex).stream()
                        .map(liveLearningMapper::toEnrolmentResponse)
                        .toList(),
                safePage,
                safeSize,
                accessibleEnrolments.size(),
                PaginationUtils.totalPages(accessibleEnrolments.size(), safeSize));
    }

    @Transactional(readOnly = true)
    public PageResponse<LiveClassResponse> getStudentLiveClasses(SecurityUserDetails currentUser, int page, int size) {
        StudentProfile student = liveLearningAccessService.requireStudent(currentUser);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<LiveClass> liveClassPage = liveClassRepository.findStudentVisibleClasses(
                ContentStatus.PUBLISHED,
                STUDENT_VISIBLE_STATUSES,
                PageRequest.of(safePage, safeSize));
        return mapLiveClassPage(liveClassPage, safePage, safeSize, liveClass -> toStudentResponse(liveClass, student));
    }

    @Transactional(readOnly = true)
    public LiveClassResponse getStudentLiveClass(SecurityUserDetails currentUser, Long liveClassId) {
        StudentProfile student = liveLearningAccessService.requireStudent(currentUser);
        LiveClass liveClass = liveLearningAccessService.requireLiveClass(liveClassId);
        if (!isStudentVisibleClass(liveClass)) {
            throw new ResponseStatusException(FORBIDDEN, "This live class is not available.");
        }
        return toStudentResponse(liveClass, student);
    }

    @Transactional
    public LiveClassResponse enrol(SecurityUserDetails currentUser, Long liveClassId) {
        StudentProfile student = liveLearningAccessService.requireStudent(currentUser);
        LiveClass liveClass = liveLearningAccessService.requireLockedLiveClass(liveClassId);
        liveLearningAccessService.ensureStudentCanEnrol(student, liveClass);

        LiveClassEnrolment enrolment = liveClassEnrolmentRepository.findByLiveClassIdAndStudentId(liveClassId, student.getId())
                .orElseGet(LiveClassEnrolment::new);
        if (enrolment.getId() != null && enrolment.getEnrolmentStatus() == LiveClassEnrolmentStatus.ENROLLED) {
            throw new ResponseStatusException(BAD_REQUEST, "You are already enrolled in this live class.");
        }

        enrolment.setLiveClass(liveClass);
        enrolment.setStudent(student);
        enrolment.setEnrolledAt(LocalDateTime.now());
        enrolment.setCancelledAt(null);
        enrolment.setAttendedAt(null);
        enrolment.setEnrolmentStatus(LiveClassEnrolmentStatus.ENROLLED);
        liveClassEnrolmentRepository.save(enrolment);

        notificationService.safeCreateNotification(
                liveClass.getTeacher().getUser(),
                NotificationType.LIVE_CLASS_REMINDER,
                "Student enrolled in live class",
                student.getUser().getDisplayName() + " enrolled in " + liveClass.getTitle() + ".",
                "LIVE_CLASS",
                liveClass.getId(),
                "/teacher/classes",
                student.getUser());

        auditLogService.record(
                student.getUser(),
                "STUDENT_LIVE_CLASS_ENROL",
                "LiveClass",
                liveClass.getId(),
                "Student enrolled in a live class.");

        LiveClass refreshedLiveClass = liveClassRepository.findById(liveClassId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Live class was not found."));
        return toStudentResponse(refreshedLiveClass, student);
    }

    @Transactional
    public LiveClassResponse cancelEnrolment(SecurityUserDetails currentUser, Long liveClassId) {
        StudentProfile student = liveLearningAccessService.requireStudent(currentUser);
        LiveClassEnrolment enrolment = liveLearningAccessService.requireCancellableEnrolment(liveClassId, student.getId());
        enrolment.setEnrolmentStatus(LiveClassEnrolmentStatus.CANCELLED);
        enrolment.setCancelledAt(LocalDateTime.now());
        liveClassEnrolmentRepository.save(enrolment);

        notificationService.safeCreateNotification(
                enrolment.getLiveClass().getTeacher().getUser(),
                NotificationType.LIVE_CLASS_REMINDER,
                "Student cancelled enrolment",
                student.getUser().getDisplayName() + " cancelled enrolment for " + enrolment.getLiveClass().getTitle() + ".",
                "LIVE_CLASS",
                liveClassId,
                "/teacher/classes",
                student.getUser());

        auditLogService.record(
                student.getUser(),
                "STUDENT_LIVE_CLASS_CANCEL_ENROLMENT",
                "LiveClass",
                liveClassId,
                "Student cancelled live class enrolment.");

        return toStudentResponse(enrolment.getLiveClass(), student);
    }

    @Transactional(readOnly = true)
    public JoinLiveClassResponse join(SecurityUserDetails currentUser, Long liveClassId) {
        StudentProfile student = liveLearningAccessService.requireStudent(currentUser);
        LiveClass liveClass = liveLearningAccessService.requireLiveClass(liveClassId);
        liveLearningAccessService.ensureStudentCanJoin(student, liveClass);

        return new JoinLiveClassResponse(
                liveClass.getId(),
                liveClass.getTitle(),
                liveClass.getMeetingPlatform() == null ? null : liveClass.getMeetingPlatform().name(),
                liveClass.getMeetingLink());
    }

    @Transactional(readOnly = true)
    public PageResponse<LiveClassResponse> getAdminLiveClasses(SecurityUserDetails currentUser, int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<LiveClass> liveClassPage = liveClassRepository.findAllByOrderByScheduledStartAtAsc(PageRequest.of(safePage, safeSize));
        return mapLiveClassPage(liveClassPage, safePage, safeSize, this::toAdminResponse);
    }

    @Transactional(readOnly = true)
    public LiveClassResponse getAdminLiveClass(SecurityUserDetails currentUser, Long liveClassId) {
        return toAdminResponse(liveLearningAccessService.requireLiveClass(liveClassId));
    }

    private void applyLiveClassUpdate(
            SecurityUserDetails currentUser,
            LiveClass liveClass,
            String title,
            String description,
            Long lessonId,
            Long packageId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String timeZone,
            String meetingLink,
            String meetingPlatform,
            String statusValue,
            Integer maxParticipants) {
        liveLearningAccessService.validateLiveClassTiming(startTime, endTime);
        var parsedMeetingPlatform = liveLearningAccessService.parseMeetingPlatform(meetingPlatform);
        String normalizedMeetingLink = liveLearningAccessService.validateMeetingLink(meetingLink, parsedMeetingPlatform);

        Lesson lesson = lessonId == null ? null : liveLearningAccessService.requireOwnedLesson(currentUser, lessonId);
        CoursePackage coursePackage = packageId == null ? null : liveLearningAccessService.requireOwnedPackage(currentUser, packageId);

        if (lesson == null && coursePackage == null) {
            throw new ResponseStatusException(BAD_REQUEST, "A live class must be linked to a lesson or package.");
        }

        liveClass.setLesson(lesson);
        liveClass.setCoursePackage(coursePackage);
        liveClass.setTitle(contentSafetyService.requireSafeText(title, MAX_LIVE_CLASS_TITLE_LENGTH, "Title is required."));
        liveClass.setDescription(contentSafetyService.optionalSafeText(description, MAX_LIVE_CLASS_DESCRIPTION_LENGTH));
        liveClass.setScheduledStartAt(startTime);
        liveClass.setScheduledEndAt(endTime);
        liveClass.setTimeZone(resolveTimeZone(timeZone));
        liveClass.setMeetingLink(normalizedMeetingLink);
        liveClass.setMeetingPlatform(parsedMeetingPlatform);
        LiveClassStatus requestedStatus = liveLearningAccessService.parseLiveClassStatus(statusValue, liveClass.getLiveClassStatus());
        if (liveClass.getId() == null) {
            validateInitialLiveClassStatus(requestedStatus);
        } else {
            validateLiveClassStatusTransition(liveClass.getLiveClassStatus(), requestedStatus);
        }
        liveClass.setLiveClassStatus(requestedStatus);
        liveClass.setMaxParticipants(maxParticipants);
    }

    private boolean applyLiveClassStatusTransition(LiveClass liveClass, LiveClassStatus requestedStatus) {
        LiveClassStatus currentStatus = liveClass.getLiveClassStatus();
        validateLiveClassStatusTransition(currentStatus, requestedStatus);
        if (currentStatus == requestedStatus) {
            return false;
        }
        liveClass.setLiveClassStatus(requestedStatus);
        return true;
    }

    private void validateInitialLiveClassStatus(LiveClassStatus requestedStatus) {
        if (requestedStatus != LiveClassStatus.SCHEDULED) {
            throw new ResponseStatusException(BAD_REQUEST, "Live classes must be created in SCHEDULED status.");
        }
    }

    private void ensureLiveClassEditable(LiveClass liveClass) {
        if (isTerminalStatus(liveClass.getLiveClassStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Completed or cancelled live classes cannot be edited.");
        }
    }

    private void validateLiveClassStatusTransition(LiveClassStatus currentStatus, LiveClassStatus requestedStatus) {
        if (currentStatus == requestedStatus) {
            return;
        }
        if (isTerminalStatus(currentStatus)) {
            throw new ResponseStatusException(BAD_REQUEST, "Completed or cancelled live classes cannot change status.");
        }
        if (!isAllowedLiveClassStatusTransition(currentStatus, requestedStatus)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Live class status transition from " + currentStatus + " to " + requestedStatus + " is not allowed.");
        }
    }

    private boolean isAllowedLiveClassStatusTransition(LiveClassStatus currentStatus, LiveClassStatus requestedStatus) {
        return switch (currentStatus) {
            case SCHEDULED -> requestedStatus == LiveClassStatus.ONGOING
                    || requestedStatus == LiveClassStatus.RESCHEDULED
                    || requestedStatus == LiveClassStatus.COMPLETED
                    || requestedStatus == LiveClassStatus.CANCELLED;
            case RESCHEDULED -> requestedStatus == LiveClassStatus.SCHEDULED
                    || requestedStatus == LiveClassStatus.ONGOING
                    || requestedStatus == LiveClassStatus.COMPLETED
                    || requestedStatus == LiveClassStatus.CANCELLED;
            case ONGOING -> requestedStatus == LiveClassStatus.COMPLETED
                    || requestedStatus == LiveClassStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    private boolean isTerminalStatus(LiveClassStatus status) {
        return status == LiveClassStatus.COMPLETED || status == LiveClassStatus.CANCELLED;
    }

    private String resolveTimeZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return platformProperties.defaultTimeZone();
        }
        try {
            return ZoneId.of(timeZone.strip()).getId();
        } catch (ZoneRulesException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Time zone must be a valid IANA time zone.");
        }
    }

    private boolean isStudentVisibleClass(LiveClass liveClass) {
        boolean hasLinkedContent = liveClass.getLesson() != null || liveClass.getCoursePackage() != null;
        boolean lessonVisible = liveClass.getLesson() == null
                || liveClass.getLesson().getContentStatus() == ContentStatus.PUBLISHED;
        boolean packageVisible = liveClass.getCoursePackage() == null
                || liveClass.getCoursePackage().getContentStatus() == ContentStatus.PUBLISHED;
        return hasLinkedContent && lessonVisible && packageVisible
                && STUDENT_VISIBLE_STATUSES.contains(liveClass.getLiveClassStatus());
    }

    private PageResponse<LiveClassResponse> mapLiveClassPage(
            Page<LiveClass> liveClassPage,
            int page,
            int size,
            Function<LiveClass, LiveClassResponse> mapper) {
        return new PageResponse<>(
                liveClassPage.getContent().stream()
                        .map(mapper)
                        .toList(),
                page,
                size,
                liveClassPage.getTotalElements(),
                PaginationUtils.totalPages(liveClassPage.getTotalElements(), size));
    }

    private LiveClassResponse toTeacherResponse(LiveClass liveClass) {
        long enrolledCount = countCurrentEnrolledStudents(liveClass);
        return liveLearningMapper.toLiveClassResponse(liveClass, enrolledCount, false, false, true, false, true);
    }

    private LiveClassResponse toAdminResponse(LiveClass liveClass) {
        long enrolledCount = liveClassEnrolmentRepository.countByLiveClassIdAndEnrolmentStatus(
                liveClass.getId(),
                LiveClassEnrolmentStatus.ENROLLED);
        return liveLearningMapper.toLiveClassResponse(liveClass, enrolledCount, false, false, true, false, true);
    }

    private LiveClassResponse toStudentResponse(LiveClass liveClass, StudentProfile student) {
        long enrolledCount = countCurrentEnrolledStudents(liveClass);
        boolean enrolled = liveLearningAccessService.isStudentEnrolled(liveClass.getId(), student.getId());
        boolean accessAllowed = hasAccess(student, liveClass);
        boolean canEnrol = !enrolled
                && accessAllowed
                && STUDENT_VISIBLE_STATUSES.contains(liveClass.getLiveClassStatus());
        boolean canJoin = enrolled
                && accessAllowed
                && liveLearningAccessService.isJoinWindowOpen(liveClass);
        return liveLearningMapper.toLiveClassResponse(liveClass, enrolledCount, enrolled, canEnrol, accessAllowed, canJoin, false);
    }

    private boolean hasAccess(StudentProfile student, LiveClass liveClass) {
        Lesson lesson = liveClass.getLesson();
        if (lesson != null) {
            if (lesson.getContentStatus() != ContentStatus.PUBLISHED) {
                return false;
            }
            if (!liveLearningAccessService.hasStudentLessonAccess(student, lesson)) {
                return false;
            }
        }

        CoursePackage coursePackage = liveClass.getCoursePackage();
        if (coursePackage != null) {
            if (coursePackage.getContentStatus() != ContentStatus.PUBLISHED) {
                return false;
            }
            if (!liveLearningAccessService.hasStudentPackageAccess(student, coursePackage)) {
                return false;
            }
        }

        return true;
    }

    private long countCurrentEnrolledStudents(LiveClass liveClass) {
        return liveClassEnrolmentRepository.findByLiveClassIdOrderByEnrolledAtAsc(liveClass.getId()).stream()
                .filter(enrolment -> enrolment.getEnrolmentStatus() == LiveClassEnrolmentStatus.ENROLLED)
                .filter(this::hasCurrentEnrolmentAccess)
                .count();
    }

    private boolean hasCurrentEnrolmentAccess(LiveClassEnrolment enrolment) {
        if (enrolment.getStudent() == null || enrolment.getLiveClass() == null) {
            return false;
        }
        return hasAccess(enrolment.getStudent(), enrolment.getLiveClass());
    }

    private void notifyEnrolledStudents(LiveClass liveClass, String title, String message, String targetPath) {
        for (LiveClassEnrolment enrolment : liveClassEnrolmentRepository.findByLiveClassIdOrderByEnrolledAtAsc(liveClass.getId())) {
            if (enrolment.getEnrolmentStatus() == LiveClassEnrolmentStatus.ENROLLED
                    && enrolment.getStudent().getUser() != null
                    && hasCurrentEnrolmentAccess(enrolment)) {
                notificationService.safeCreateNotification(
                        enrolment.getStudent().getUser(),
                        NotificationType.LIVE_CLASS_REMINDER,
                        title,
                        message,
                        "LIVE_CLASS",
                        liveClass.getId(),
                        targetPath,
                        liveClass.getTeacher().getUser());
            }
        }
    }
}
