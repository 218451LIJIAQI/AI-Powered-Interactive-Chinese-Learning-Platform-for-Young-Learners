package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
import com.example.aichinese.repository.LiveClassEnrolmentRepository;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class LiveClassService {

    private final LiveClassRepository liveClassRepository;
    private final LiveClassEnrolmentRepository liveClassEnrolmentRepository;
    private final LiveLearningAccessService liveLearningAccessService;
    private final LiveLearningMapper liveLearningMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public LiveClassService(
            LiveClassRepository liveClassRepository,
            LiveClassEnrolmentRepository liveClassEnrolmentRepository,
            LiveLearningAccessService liveLearningAccessService,
            LiveLearningMapper liveLearningMapper,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.liveClassRepository = liveClassRepository;
        this.liveClassEnrolmentRepository = liveClassEnrolmentRepository;
        this.liveLearningAccessService = liveLearningAccessService;
        this.liveLearningMapper = liveLearningMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public LiveClassResponse createLiveClass(SecurityUserDetails currentUser, CreateLiveClassRequest request) {
        TeacherProfile teacher = liveLearningAccessService.requireApprovedTeacher(currentUser);
        LiveClass liveClass = new LiveClass();
        applyLiveClassUpdate(currentUser, liveClass, request.title(), request.description(), request.lessonId(), request.packageId(),
                request.startTime(), request.endTime(), request.meetingLink(), request.meetingPlatform(),
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
        applyLiveClassUpdate(currentUser, liveClass, request.title(), request.description(), request.lessonId(), request.packageId(),
                request.startTime(), request.endTime(), request.meetingLink(), request.meetingPlatform(),
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
        liveClass.setLiveClassStatus(
                liveLearningAccessService.parseLiveClassStatus(statusValue, liveClass.getLiveClassStatus()));
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
        liveClass.setLiveClassStatus(LiveClassStatus.CANCELLED);
        LiveClass saved = liveClassRepository.save(liveClass);
        notifyEnrolledStudents(saved, "Live class cancelled", saved.getTitle() + " has been cancelled.", "/student/live-classes/" + saved.getId());

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
        liveClass.setScheduledStartAt(request.startTime());
        liveClass.setScheduledEndAt(request.endTime());
        liveClass.setLiveClassStatus(LiveClassStatus.RESCHEDULED);
        LiveClass saved = liveClassRepository.save(liveClass);
        notifyEnrolledStudents(saved, "Live class rescheduled", saved.getTitle() + " has a new schedule.", "/student/live-classes/" + saved.getId());

        auditLogService.record(
                saved.getTeacher().getUser(),
                "TEACHER_LIVE_CLASS_RESCHEDULE",
                "LiveClass",
                saved.getId(),
                "Teacher rescheduled a live class.");

        return toTeacherResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<LiveClassResponse> getTeacherLiveClasses(SecurityUserDetails currentUser) {
        liveLearningAccessService.requireApprovedTeacher(currentUser);
        return liveClassRepository.findByTeacherUserIdOrderByScheduledStartAtAsc(currentUser.getUserId()).stream()
                .map(this::toTeacherResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LiveClassResponse getTeacherLiveClass(SecurityUserDetails currentUser, Long liveClassId) {
        return toTeacherResponse(liveLearningAccessService.requireTeacherOwnedLiveClass(currentUser, liveClassId));
    }

    @Transactional(readOnly = true)
    public List<LiveClassEnrolmentResponse> getTeacherEnrolments(SecurityUserDetails currentUser, Long liveClassId) {
        liveLearningAccessService.requireTeacherOwnedLiveClass(currentUser, liveClassId);
        return liveClassEnrolmentRepository.findByLiveClassIdOrderByEnrolledAtAsc(liveClassId).stream()
                .map(liveLearningMapper::toEnrolmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LiveClassResponse> getStudentLiveClasses(SecurityUserDetails currentUser) {
        StudentProfile student = liveLearningAccessService.requireStudent(currentUser);
        return liveClassRepository.findAll().stream()
                .filter(this::isStudentVisibleClass)
                .map(liveClass -> toStudentResponse(liveClass, student))
                .sorted(java.util.Comparator.comparing(
                        LiveClassResponse::startTime,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
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
        LiveClass liveClass = liveLearningAccessService.requireLiveClass(liveClassId);
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

        notificationService.createNotification(
                liveClass.getTeacher().getUser(),
                "Student enrolled in live class",
                student.getUser().getDisplayName() + " enrolled in " + liveClass.getTitle() + ".",
                "/teacher/live-classes");

        auditLogService.record(
                student.getUser(),
                "STUDENT_LIVE_CLASS_ENROL",
                "LiveClass",
                liveClass.getId(),
                "Student enrolled in a live class.");

        return toStudentResponse(liveClassRepository.findById(liveClassId).orElseThrow(), student);
    }

    @Transactional
    public LiveClassResponse cancelEnrolment(SecurityUserDetails currentUser, Long liveClassId) {
        StudentProfile student = liveLearningAccessService.requireStudent(currentUser);
        LiveClassEnrolment enrolment = liveLearningAccessService.requireActiveEnrolment(liveClassId, student.getId());
        enrolment.setEnrolmentStatus(LiveClassEnrolmentStatus.CANCELLED);
        enrolment.setCancelledAt(LocalDateTime.now());
        liveClassEnrolmentRepository.save(enrolment);

        notificationService.createNotification(
                enrolment.getLiveClass().getTeacher().getUser(),
                "Student cancelled enrolment",
                student.getUser().getDisplayName() + " cancelled enrolment for " + enrolment.getLiveClass().getTitle() + ".",
                "/teacher/live-classes");

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
    public List<LiveClassResponse> getAdminLiveClasses(SecurityUserDetails currentUser) {
        return liveClassRepository.findAll().stream()
                .map(this::toAdminResponse)
                .toList();
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
            String meetingLink,
            String meetingPlatform,
            String statusValue,
            Integer maxParticipants) {
        liveLearningAccessService.validateLiveClassTiming(startTime, endTime);
        liveLearningAccessService.validateMeetingLink(meetingLink);

        Lesson lesson = lessonId == null ? null : liveLearningAccessService.requireOwnedLesson(currentUser, lessonId);
        CoursePackage coursePackage = packageId == null ? null : liveLearningAccessService.requireOwnedPackage(currentUser, packageId);

        if (lesson == null && coursePackage == null) {
            throw new ResponseStatusException(BAD_REQUEST, "A live class must be linked to a lesson or package.");
        }

        liveClass.setLesson(lesson);
        liveClass.setCoursePackage(coursePackage);
        liveClass.setTitle(title.trim());
        liveClass.setDescription(description == null || description.isBlank() ? null : description.trim());
        liveClass.setScheduledStartAt(startTime);
        liveClass.setScheduledEndAt(endTime);
        liveClass.setMeetingLink(meetingLink.trim());
        liveClass.setMeetingPlatform(liveLearningAccessService.parseMeetingPlatform(meetingPlatform));
        liveClass.setLiveClassStatus(
                liveLearningAccessService.parseLiveClassStatus(statusValue, LiveClassStatus.SCHEDULED));
        liveClass.setMaxParticipants(maxParticipants);
    }

    private boolean isStudentVisibleClass(LiveClass liveClass) {
        boolean hasLinkedContent = liveClass.getLesson() != null || liveClass.getCoursePackage() != null;
        boolean lessonVisible = liveClass.getLesson() == null
                || liveClass.getLesson().getContentStatus() == ContentStatus.PUBLISHED;
        boolean packageVisible = liveClass.getCoursePackage() == null
                || liveClass.getCoursePackage().getContentStatus() == ContentStatus.PUBLISHED;
        return hasLinkedContent && lessonVisible && packageVisible;
    }

    private LiveClassResponse toTeacherResponse(LiveClass liveClass) {
        long enrolledCount = liveClassEnrolmentRepository.countByLiveClassIdAndEnrolmentStatus(
                liveClass.getId(),
                LiveClassEnrolmentStatus.ENROLLED);
        return liveLearningMapper.toLiveClassResponse(liveClass, enrolledCount, false, false, true, false, true);
    }

    private LiveClassResponse toAdminResponse(LiveClass liveClass) {
        long enrolledCount = liveClassEnrolmentRepository.countByLiveClassIdAndEnrolmentStatus(
                liveClass.getId(),
                LiveClassEnrolmentStatus.ENROLLED);
        return liveLearningMapper.toLiveClassResponse(liveClass, enrolledCount, false, false, true, false, true);
    }

    private LiveClassResponse toStudentResponse(LiveClass liveClass, StudentProfile student) {
        long enrolledCount = liveClassEnrolmentRepository.countByLiveClassIdAndEnrolmentStatus(
                liveClass.getId(),
                LiveClassEnrolmentStatus.ENROLLED);
        boolean enrolled = liveLearningAccessService.isStudentEnrolled(liveClass.getId(), student.getId());
        boolean accessAllowed = hasAccess(student, liveClass);
        boolean canEnrol = !enrolled
                && accessAllowed
                && liveClass.getLiveClassStatus() != LiveClassStatus.CANCELLED
                && liveClass.getLiveClassStatus() != LiveClassStatus.COMPLETED;
        boolean canJoin = enrolled
                && accessAllowed
                && (liveClass.getLiveClassStatus() == LiveClassStatus.SCHEDULED
                        || liveClass.getLiveClassStatus() == LiveClassStatus.ONGOING);
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

    private void notifyEnrolledStudents(LiveClass liveClass, String title, String message, String targetPath) {
        for (LiveClassEnrolment enrolment : liveClassEnrolmentRepository.findByLiveClassIdOrderByEnrolledAtAsc(liveClass.getId())) {
            if (enrolment.getEnrolmentStatus() == LiveClassEnrolmentStatus.ENROLLED && enrolment.getStudent().getUser() != null) {
                notificationService.createNotification(enrolment.getStudent().getUser(), title, message, targetPath);
            }
        }
    }
}
