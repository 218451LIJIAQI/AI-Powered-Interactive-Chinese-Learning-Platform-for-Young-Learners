package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static com.example.aichinese.service.LiveLearningAccessService.nowAt;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.PlatformProperties;
import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.LiveSessionViews;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.LiveClassEnrolment;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;
import com.example.aichinese.entity.enums.MeetingPlatform;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.LiveClassEnrolmentRepository;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.ContentSafetyService;
import com.example.aichinese.service.LiveLearningAccessService;
import com.example.aichinese.service.NotificationService;

/**
 * Live sessions of classes (stored as live classes with bookings). Teachers schedule them for their published
 * classes, edit (booked students are told; a new time marks the session rescheduled and remembers the old start),
 * cancel (bookings are cancelled and students told) and add a recording once it is over. Students see the sessions
 * of classes they joined, book a seat, cancel a booking before the start, join from 15 minutes before the start
 * (which records attendance) and watch the recording of a session they booked.
 */
@Service
public class ClassLiveSessionService {

    private static final String SESSION_NOT_FOUND = "Live session was not found.";
    private static final String AUDIT_TARGET = "LiveClass";
    private static final String STUDENT_PATH = "/student/classes/live";
    private static final String TARGET_TYPE = "LIVE_CLASS";
    private static final Set<LiveClassEnrolmentStatus> HOLDS_SEAT =
            EnumSet.of(LiveClassEnrolmentStatus.ENROLLED, LiveClassEnrolmentStatus.ATTENDED);

    private final ClassAccessService access;
    private final ClassContentSupport content;
    private final ClassProgressService progress;
    private final LiveClassRepository liveClassRepository;
    private final LiveClassEnrolmentRepository bookingRepository;
    private final ClassEnrolmentRepository classEnrolmentRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final LessonRepository lessonRepository;
    private final LiveLearningAccessService liveRules;
    private final ContentSafetyService contentSafetyService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final PlatformProperties platformProperties;

    public ClassLiveSessionService(
            ClassAccessService access,
            ClassContentSupport content,
            ClassProgressService progress,
            LiveClassRepository liveClassRepository,
            LiveClassEnrolmentRepository bookingRepository,
            ClassEnrolmentRepository classEnrolmentRepository,
            CoursePackageRepository coursePackageRepository,
            LessonRepository lessonRepository,
            LiveLearningAccessService liveRules,
            ContentSafetyService contentSafetyService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            PlatformProperties platformProperties) {
        this.access = access;
        this.content = content;
        this.progress = progress;
        this.liveClassRepository = liveClassRepository;
        this.bookingRepository = bookingRepository;
        this.classEnrolmentRepository = classEnrolmentRepository;
        this.coursePackageRepository = coursePackageRepository;
        this.lessonRepository = lessonRepository;
        this.liveRules = liveRules;
        this.contentSafetyService = contentSafetyService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.platformProperties = platformProperties;
    }

    // ----- teacher -----

    @Transactional(readOnly = true)
    public LiveSessionViews.TeacherSessions teacherSessions(SecurityUserDetails currentUser) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        List<LiveClass> sessions = liveClassRepository.findByTeacherIdOrderByScheduledStartAtAscIdAsc(teacher.getId()).stream()
                .filter(session -> session.getCoursePackage() != null)
                .toList();
        Map<Long, List<LiveClassEnrolment>> bookings = bookingsBySession(sessions);
        List<LiveSessionViews.TeacherSession> upcoming = new ArrayList<>();
        List<LiveSessionViews.TeacherSession> past = new ArrayList<>();
        for (LiveClass session : sessions) {
            LiveSessionViews.TeacherSession view = teacherView(session, bookings.getOrDefault(session.getId(), List.of()));
            if (!LiveLearningAccessService.hasEnded(session)) {
                upcoming.add(view);
            } else if (session.getLiveClassStatus() != LiveClassStatus.CANCELLED) {
                past.add(view);
            }
        }
        past.sort(Comparator.comparing(LiveSessionViews.TeacherSession::startsAt).reversed());
        return new LiveSessionViews.TeacherSessions(upcoming, past);
    }

    @Transactional(readOnly = true)
    public LiveSessionViews.TeacherSessionDetail teacherSession(SecurityUserDetails currentUser, Long sessionId) {
        access.requireTeacher(currentUser);
        LiveClass session = requireOwnedSession(currentUser, sessionId);
        List<LiveClassEnrolment> bookings = bookingRepository.findByLiveClassIdIn(List.of(sessionId));
        List<LiveClassEnrolment> active = bookings.stream()
                .filter(booking -> HOLDS_SEAT.contains(booking.getEnrolmentStatus()))
                .toList();
        Map<Long, Integer> classProgress = classProgress(session.getCoursePackage(),
                active.stream().map(booking -> booking.getStudent().getId()).toList());
        boolean ended = LiveLearningAccessService.hasEnded(session);
        List<LiveSessionViews.BookedStudent> students = active.stream()
                .map(booking -> new LiveSessionViews.BookedStudent(
                        booking.getStudent().getId(),
                        booking.getStudent().getUser().getDisplayName(),
                        xpLevel(booking.getStudent()),
                        classProgress.get(booking.getStudent().getId()),
                        booking.getEnrolmentStatus() == LiveClassEnrolmentStatus.ATTENDED
                                ? "ATTENDED"
                                : ended ? "DID_NOT_JOIN" : "BOOKED"))
                .toList();
        return new LiveSessionViews.TeacherSessionDetail(
                teacherView(session, bookings),
                instant(session, session.getScheduledStartAt().minusMinutes(LiveLearningAccessService.JOIN_WINDOW_LEAD_MINUTES)),
                students);
    }

    /** The teacher's published classes and their lessons, for the schedule and edit forms. */
    @Transactional(readOnly = true)
    public LiveSessionViews.ScheduleOptions scheduleOptions(SecurityUserDetails currentUser) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        List<LiveSessionViews.ClassOption> classes = new ArrayList<>();
        for (CoursePackage coursePackage : coursePackageRepository.findByTeacherIdAndContentStatus(
                teacher.getId(), ContentStatus.PUBLISHED)) {
            List<LiveSessionViews.LessonOption> lessons = content.lessonsInOrder(coursePackage, false).stream()
                    .filter(ClassContentSupport::isLessonLive)
                    .map(lesson -> new LiveSessionViews.LessonOption(lesson.getId(), lesson.getTitle()))
                    .toList();
            classes.add(new LiveSessionViews.ClassOption(coursePackage.getId(), coursePackage.getTitle(), lessons));
        }
        classes.sort(Comparator.comparing(LiveSessionViews.ClassOption::title, String.CASE_INSENSITIVE_ORDER));
        return new LiveSessionViews.ScheduleOptions(classes);
    }

    @Transactional
    public LiveSessionViews.TeacherSession schedule(SecurityUserDetails currentUser, ClassRequests.LiveSession request) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        LiveClass session = new LiveClass();
        session.setTeacher(teacher);
        apply(currentUser, session, request);
        if (!session.getScheduledStartAt().isAfter(LocalDateTime.now(ZoneId.of(session.getTimeZone())))) {
            throw new ResponseStatusException(BAD_REQUEST, "Start time must be in the future.");
        }
        session.setLiveClassStatus(LiveClassStatus.SCHEDULED);
        LiveClass saved = liveClassRepository.save(session);
        auditLogService.record(teacher.getUser(), "TEACHER_LIVE_SESSION_SCHEDULE",
                new AuditLogService.AuditTarget(AUDIT_TARGET, saved.getId(), saved.getTitle()), null,
                "Teacher scheduled a live session.");
        return teacherView(saved, List.of());
    }

    @Transactional
    public LiveSessionViews.TeacherSession update(
            SecurityUserDetails currentUser,
            Long sessionId,
            ClassRequests.LiveSession request) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        LiveClass session = requireOwnedSession(currentUser, sessionId);
        if (session.getLiveClassStatus() == LiveClassStatus.CANCELLED || LiveLearningAccessService.hasEnded(session)) {
            throw new ResponseStatusException(CONFLICT, "A cancelled or past session can't be edited.");
        }
        LocalDateTime startBefore = session.getScheduledStartAt();
        LocalDateTime endBefore = session.getScheduledEndAt();
        String detailsBefore = details(session);
        apply(currentUser, session, request);
        int booked = seatsTaken(bookingRepository.findByLiveClassIdIn(List.of(sessionId)));
        if (session.getMaxParticipants() != null && session.getMaxParticipants() < booked) {
            throw new ResponseStatusException(BAD_REQUEST, "Capacity can't be lower than the seats already booked.");
        }
        boolean moved = !Objects.equals(startBefore, session.getScheduledStartAt())
                || !Objects.equals(endBefore, session.getScheduledEndAt());
        if (moved) {
            if (!session.getScheduledStartAt().isAfter(LocalDateTime.now(ZoneId.of(session.getTimeZone())))) {
                throw new ResponseStatusException(BAD_REQUEST, "Start time must be in the future.");
            }
            session.setRescheduledFromStartAt(startBefore);
            session.setLiveClassStatus(LiveClassStatus.RESCHEDULED);
        }
        LiveClass saved = liveClassRepository.save(session);
        if (moved) {
            notifyBooked(saved, "Live session rescheduled", saved.getTitle() + " has a new time.");
        } else if (!detailsBefore.equals(details(saved))) {
            notifyBooked(saved, "Live session updated", saved.getTitle() + " has new details.");
        }
        auditLogService.record(teacher.getUser(), "TEACHER_LIVE_SESSION_UPDATE",
                new AuditLogService.AuditTarget(AUDIT_TARGET, sessionId, saved.getTitle()), null,
                moved ? "Teacher rescheduled a live session." : "Teacher updated a live session.");
        return teacherView(saved, bookingRepository.findByLiveClassIdIn(List.of(sessionId)));
    }

    /** Cancels the session for everyone: bookings are cancelled and the booked students told. */
    @Transactional
    public LiveSessionViews.TeacherSession cancel(SecurityUserDetails currentUser, Long sessionId) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        LiveClass session = requireOwnedSession(currentUser, sessionId);
        if (LiveLearningAccessService.hasEnded(session)) {
            throw new ResponseStatusException(CONFLICT, "A past session can't be cancelled.");
        }
        cancelSession(session, teacher.getUser());
        auditLogService.record(teacher.getUser(), "TEACHER_LIVE_SESSION_CANCEL",
                new AuditLogService.AuditTarget(AUDIT_TARGET, sessionId, session.getTitle()), null,
                "Teacher cancelled a live session.");
        return teacherView(session, bookingRepository.findByLiveClassIdIn(List.of(sessionId)));
    }

    /** Cancels every session of the class that has not started (the class was archived). */
    @Transactional
    public void cancelUpcomingSessionsOfClass(CoursePackage coursePackage, User actor) {
        for (LiveClass session : liveClassRepository.findByCoursePackageIdInOrderByScheduledStartAtAscIdAsc(
                List.of(coursePackage.getId()))) {
            if (session.getLiveClassStatus() != LiveClassStatus.CANCELLED && !LiveLearningAccessService.hasEnded(session)) {
                cancelSession(session, actor);
            }
        }
    }

    private void cancelSession(LiveClass session, User actor) {
        if (session.getLiveClassStatus() == LiveClassStatus.CANCELLED) {
            return;
        }
        List<LiveClassEnrolment> booked = bookingRepository.findByLiveClassIdIn(List.of(session.getId())).stream()
                .filter(booking -> booking.getEnrolmentStatus() == LiveClassEnrolmentStatus.ENROLLED)
                .toList();
        session.setLiveClassStatus(LiveClassStatus.CANCELLED);
        session.setCancelledAt(LocalDateTime.now());
        liveClassRepository.save(session);
        LocalDateTime now = LocalDateTime.now();
        for (LiveClassEnrolment booking : booked) {
            booking.setEnrolmentStatus(LiveClassEnrolmentStatus.CANCELLED);
            booking.setCancelledAt(now);
            bookingRepository.save(booking);
            notifyStudent(booking, session, "Live session cancelled",
                    session.getTitle() + " has been cancelled. Your booking was cancelled.", actor);
        }
    }

    @Transactional
    public LiveSessionViews.TeacherSession saveRecording(
            SecurityUserDetails currentUser,
            Long sessionId,
            ClassRequests.Recording request) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        LiveClass session = requireOwnedSession(currentUser, sessionId);
        if (session.getLiveClassStatus() == LiveClassStatus.CANCELLED || !LiveLearningAccessService.hasEnded(session)) {
            throw new ResponseStatusException(BAD_REQUEST, "A recording can be added once the session is over.");
        }
        session.setRecordingUrl(requireWebLink(request.recordingUrl(), "Recording link must be a valid http:// or https:// link."));
        LiveClass saved = liveClassRepository.save(session);
        for (LiveClassEnrolment booking : bookingRepository.findByLiveClassIdIn(List.of(sessionId))) {
            if (HOLDS_SEAT.contains(booking.getEnrolmentStatus())) {
                notifyStudent(booking, session, "Recording ready",
                        "The recording of " + session.getTitle() + " is ready to watch.", teacher.getUser());
            }
        }
        return teacherView(saved, bookingRepository.findByLiveClassIdIn(List.of(sessionId)));
    }

    private void apply(SecurityUserDetails currentUser, LiveClass session, ClassRequests.LiveSession request) {
        CoursePackage coursePackage = access.requireOwnedClass(currentUser, request.classId());
        if (coursePackage.getContentStatus() != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(BAD_REQUEST, "Live sessions can only be scheduled for published classes.");
        }
        Lesson lesson = null;
        if (request.lessonId() != null) {
            lesson = lessonRepository.findById(request.lessonId())
                    .filter(found -> found.getRevisionOf() == null && found.getModule() != null)
                    .filter(found -> content.classOfUnit(found.getModule().getId())
                            .map(owner -> owner.getId().equals(coursePackage.getId()))
                            .orElse(false))
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Choose a lesson of this class."));
        }
        liveRules.validateLiveClassTiming(request.startTime(), request.endTime());
        MeetingPlatform platform = liveRules.parseMeetingPlatform(request.meetingPlatform());
        session.setMeetingLink(liveRules.validateMeetingLink(request.meetingLink(), platform));
        session.setMeetingPlatform(platform);
        session.setCoursePackage(coursePackage);
        session.setLesson(lesson);
        session.setTitle(contentSafetyService.requireSafeText(request.title(), 200, "Title is required."));
        session.setScheduledStartAt(request.startTime());
        session.setScheduledEndAt(request.endTime());
        session.setTimeZone(timeZone(request.timeZone()));
        session.setMaxParticipants(request.capacity());
    }

    private LiveClass requireOwnedSession(SecurityUserDetails currentUser, Long sessionId) {
        return liveClassRepository.findByIdAndTeacherUserId(sessionId, currentUser.getUserId())
                .filter(session -> session.getCoursePackage() != null)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, SESSION_NOT_FOUND));
    }

    private void notifyBooked(LiveClass session, String title, String message) {
        for (LiveClassEnrolment booking : bookingRepository.findByLiveClassIdIn(List.of(session.getId()))) {
            if (booking.getEnrolmentStatus() == LiveClassEnrolmentStatus.ENROLLED) {
                notifyStudent(booking, session, title, message, session.getTeacher().getUser());
            }
        }
    }

    private void notifyStudent(LiveClassEnrolment booking, LiveClass session, String title, String message, User actor) {
        notificationService.safeCreateNotification(
                booking.getStudent().getUser(),
                NotificationType.LIVE_CLASS_REMINDER,
                title,
                message,
                TARGET_TYPE,
                session.getId(),
                STUDENT_PATH,
                actor);
    }

    /** Bookings that hold a seat: booked, or already attended. */
    private static int seatsTaken(List<LiveClassEnrolment> bookings) {
        return (int) bookings.stream().filter(booking -> HOLDS_SEAT.contains(booking.getEnrolmentStatus())).count();
    }

    // ----- students -----

    @Transactional(readOnly = true)
    public LiveSessionViews.StudentSessions studentSessions(SecurityUserDetails currentUser) {
        StudentProfile student = access.requireStudent(currentUser);
        List<Long> classIds = classEnrolmentRepository.findClassIdsByStudentId(student.getId());
        List<LiveClass> sessions = classIds.isEmpty()
                ? List.of()
                : liveClassRepository.findByCoursePackageIdInOrderByScheduledStartAtAscIdAsc(classIds).stream()
                        .filter(session -> ClassAccessService.isOpenToJoinedStudents(session.getCoursePackage()))
                        .toList();
        Map<Long, List<LiveClassEnrolment>> bookings = bookingsBySession(sessions);
        List<LiveSessionViews.StudentSession> upcoming = new ArrayList<>();
        List<LiveSessionViews.StudentSession> past = new ArrayList<>();
        long bookedCount = 0;
        for (LiveClass session : sessions) {
            LiveSessionViews.StudentSession view = studentView(session, student, bookings.getOrDefault(session.getId(), List.of()));
            if (!LiveLearningAccessService.hasEnded(session)) {
                upcoming.add(view);
                if (view.booked()) {
                    bookedCount++;
                }
            } else if (session.getLiveClassStatus() != LiveClassStatus.CANCELLED && view.booked()) {
                past.add(view);
            }
        }
        past.sort(Comparator.comparing(LiveSessionViews.StudentSession::startsAt).reversed());
        return new LiveSessionViews.StudentSessions(upcoming, past, bookedCount);
    }

    @Transactional
    public LiveSessionViews.StudentSession book(SecurityUserDetails currentUser, Long sessionId) {
        StudentProfile student = access.requireStudent(currentUser);
        requireStudentSession(student, sessionId, true);
        LiveClass session = liveClassRepository.findLockedById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, SESSION_NOT_FOUND));
        if (session.getLiveClassStatus() == LiveClassStatus.CANCELLED) {
            throw new ResponseStatusException(BAD_REQUEST, "This live session was cancelled.");
        }
        if (LiveLearningAccessService.hasEnded(session)) {
            throw new ResponseStatusException(BAD_REQUEST, "This live session has already ended.");
        }
        List<LiveClassEnrolment> bookings = bookingRepository.findByLiveClassIdIn(List.of(sessionId));
        if (activeBooking(bookings, student) != null) {
            throw new ResponseStatusException(BAD_REQUEST, "You already booked this live session.");
        }
        int taken = seatsTaken(bookings);
        if (session.getMaxParticipants() != null && taken >= session.getMaxParticipants()) {
            throw new ResponseStatusException(BAD_REQUEST, "This live session is full.");
        }
        LiveClassEnrolment booking = new LiveClassEnrolment();
        booking.setLiveClass(session);
        booking.setStudent(student);
        booking.setEnrolledAt(LocalDateTime.now());
        booking.setEnrolmentStatus(LiveClassEnrolmentStatus.ENROLLED);
        bookingRepository.save(booking);
        return studentView(session, student, bookingRepository.findByLiveClassIdIn(List.of(sessionId)));
    }

    @Transactional
    public LiveSessionViews.StudentSession cancelBooking(SecurityUserDetails currentUser, Long sessionId) {
        StudentProfile student = access.requireStudent(currentUser);
        LiveClass session = requireStudentSession(student, sessionId, false);
        LiveClassEnrolment booking = activeBooking(bookingRepository.findByLiveClassIdIn(List.of(sessionId)), student);
        boolean started = !nowAt(session).isBefore(session.getScheduledStartAt());
        if (booking == null || booking.getEnrolmentStatus() != LiveClassEnrolmentStatus.ENROLLED || started) {
            throw new ResponseStatusException(BAD_REQUEST, "Only a booking for a session that has not started can be cancelled.");
        }
        booking.setEnrolmentStatus(LiveClassEnrolmentStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        bookingRepository.save(booking);
        return studentView(session, student, bookingRepository.findByLiveClassIdIn(List.of(sessionId)));
    }

    /** The meeting link inside the join window (15 minutes before the start until the end); records attendance. */
    @Transactional
    public LiveSessionViews.JoinLink join(SecurityUserDetails currentUser, Long sessionId) {
        StudentProfile student = access.requireStudent(currentUser);
        LiveClass session = requireStudentSession(student, sessionId, false);
        LiveClassEnrolment booking = activeBooking(bookingRepository.findByLiveClassIdIn(List.of(sessionId)), student);
        if (booking == null) {
            throw new ResponseStatusException(CONFLICT, "Book a seat to join this live session.");
        }
        if (session.getLiveClassStatus() == LiveClassStatus.CANCELLED || !liveRules.isJoinWindowOpen(session)) {
            throw new ResponseStatusException(CONFLICT, "You can join from 15 minutes before the start.");
        }
        if (booking.getEnrolmentStatus() == LiveClassEnrolmentStatus.ENROLLED) {
            booking.setEnrolmentStatus(LiveClassEnrolmentStatus.ATTENDED);
            booking.setAttendedAt(LocalDateTime.now());
            bookingRepository.save(booking);
        }
        return new LiveSessionViews.JoinLink(session.getMeetingLink());
    }

    /** A session of a class the student joined (for booking, the class must still be open to new bookings). */
    private LiveClass requireStudentSession(StudentProfile student, Long sessionId, boolean forBooking) {
        LiveClass session = liveClassRepository.findById(sessionId)
                .filter(found -> found.getCoursePackage() != null)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, SESSION_NOT_FOUND));
        CoursePackage coursePackage = session.getCoursePackage();
        if (!ClassAccessService.isOpenToJoinedStudents(coursePackage)
                || !classEnrolmentRepository.existsByStudentIdAndCoursePackageId(student.getId(), coursePackage.getId())) {
            throw new ResponseStatusException(NOT_FOUND, SESSION_NOT_FOUND);
        }
        if (forBooking && coursePackage.getContentStatus() != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(BAD_REQUEST, "This live session was cancelled.");
        }
        return session;
    }

    // ----- short lists for class and lesson pages -----

    /** Upcoming, not cancelled sessions of the classes, earliest first (the student's booking marked). */
    @Transactional(readOnly = true)
    public Map<Long, List<LiveSessionViews.SessionBrief>> upcomingByClass(Collection<Long> classIds, StudentProfile student) {
        if (classIds.isEmpty()) {
            return Map.of();
        }
        List<LiveClass> sessions = liveClassRepository.findByCoursePackageIdInOrderByScheduledStartAtAscIdAsc(classIds).stream()
                .filter(session -> session.getLiveClassStatus() != LiveClassStatus.CANCELLED)
                .filter(session -> !LiveLearningAccessService.hasEnded(session))
                .toList();
        Map<Long, List<LiveClassEnrolment>> bookings = bookingsBySession(sessions);
        Map<Long, List<LiveSessionViews.SessionBrief>> result = new HashMap<>();
        for (LiveClass session : sessions) {
            List<LiveClassEnrolment> sessionBookings = bookings.getOrDefault(session.getId(), List.of());
            result.computeIfAbsent(session.getCoursePackage().getId(), ignored -> new ArrayList<>())
                    .add(brief(session, sessionBookings, student != null && activeBooking(sessionBookings, student) != null));
        }
        return result;
    }

    private LiveSessionViews.SessionBrief brief(LiveClass session, List<LiveClassEnrolment> bookings, boolean booked) {
        int taken = seatsTaken(bookings);
        return new LiveSessionViews.SessionBrief(
                session.getId(),
                session.getTitle(),
                session.getCoursePackage().getId(),
                session.getCoursePackage().getTitle(),
                session.getLesson() == null ? null : session.getLesson().getId(),
                session.getLesson() == null ? null : session.getLesson().getTitle(),
                session.getTeacher().getUser().getDisplayName(),
                instant(session, session.getScheduledStartAt()),
                instant(session, session.getScheduledEndAt()),
                session.getMeetingPlatform().name(),
                displayStatus(session, taken),
                booked,
                taken,
                session.getMaxParticipants());
    }

    // ----- views -----

    private LiveSessionViews.TeacherSession teacherView(LiveClass session, List<LiveClassEnrolment> bookings) {
        int booked = seatsTaken(bookings);
        int attended = (int) bookings.stream()
                .filter(booking -> booking.getEnrolmentStatus() == LiveClassEnrolmentStatus.ATTENDED)
                .count();
        return new LiveSessionViews.TeacherSession(
                session.getId(),
                session.getTitle(),
                session.getCoursePackage().getId(),
                session.getCoursePackage().getTitle(),
                session.getLesson() == null ? null : session.getLesson().getId(),
                session.getLesson() == null ? null : session.getLesson().getTitle(),
                instant(session, session.getScheduledStartAt()),
                instant(session, session.getScheduledEndAt()),
                session.getScheduledStartAt(),
                session.getScheduledEndAt(),
                session.getTimeZone(),
                session.getMeetingPlatform().name(),
                session.getMeetingLink(),
                displayStatus(session, booked),
                session.getRescheduledFromStartAt() == null ? null : instant(session, session.getRescheduledFromStartAt()),
                session.getCancelledAt(),
                session.getMaxParticipants(),
                booked,
                attended,
                session.getRecordingUrl());
    }

    private LiveSessionViews.StudentSession studentView(LiveClass session, StudentProfile student, List<LiveClassEnrolment> bookings) {
        int taken = seatsTaken(bookings);
        LiveClassEnrolment mine = activeBooking(bookings, student);
        boolean cancelledMine = mine == null && bookings.stream().anyMatch(booking ->
                booking.getStudent().getId().equals(student.getId())
                        && booking.getEnrolmentStatus() == LiveClassEnrolmentStatus.CANCELLED);
        boolean cancelled = session.getLiveClassStatus() == LiveClassStatus.CANCELLED;
        boolean ended = LiveLearningAccessService.hasEnded(session);
        boolean hasSeat = session.getMaxParticipants() == null || taken < session.getMaxParticipants();
        boolean canBook = mine == null && !cancelled && !ended && hasSeat
                && session.getCoursePackage().getContentStatus() == ContentStatus.PUBLISHED;
        boolean canJoin = mine != null && !cancelled && liveRules.isJoinWindowOpen(session);
        return new LiveSessionViews.StudentSession(
                session.getId(),
                session.getTitle(),
                session.getCoursePackage().getId(),
                session.getCoursePackage().getTitle(),
                session.getLesson() == null ? null : session.getLesson().getId(),
                session.getLesson() == null ? null : session.getLesson().getTitle(),
                session.getTeacher().getUser().getDisplayName(),
                instant(session, session.getScheduledStartAt()),
                instant(session, session.getScheduledEndAt()),
                instant(session, session.getScheduledStartAt().minusMinutes(LiveLearningAccessService.JOIN_WINDOW_LEAD_MINUTES)),
                session.getMeetingPlatform().name(),
                displayStatus(session, taken),
                session.getRescheduledFromStartAt() == null ? null : instant(session, session.getRescheduledFromStartAt()),
                session.getCancelledAt(),
                session.getMaxParticipants(),
                taken,
                session.getMaxParticipants() == null ? null : Math.max(0, session.getMaxParticipants() - taken),
                mine != null,
                cancelledMine,
                mine != null && mine.getEnrolmentStatus() == LiveClassEnrolmentStatus.ATTENDED,
                canBook,
                canJoin,
                mine != null && ended ? session.getRecordingUrl() : null);
    }

    /** SCHEDULED, RESCHEDULED, FULL, CANCELLED or COMPLETED as the pages show it. */
    private static String displayStatus(LiveClass session, int seatsTaken) {
        if (session.getLiveClassStatus() == LiveClassStatus.CANCELLED) {
            return "CANCELLED";
        }
        if (LiveLearningAccessService.hasEnded(session) || session.getLiveClassStatus() == LiveClassStatus.COMPLETED) {
            return "COMPLETED";
        }
        if (session.getMaxParticipants() != null && seatsTaken >= session.getMaxParticipants()) {
            return "FULL";
        }
        return session.getLiveClassStatus() == LiveClassStatus.RESCHEDULED ? "RESCHEDULED" : "SCHEDULED";
    }

    private Map<Long, List<LiveClassEnrolment>> bookingsBySession(Collection<LiveClass> sessions) {
        if (sessions.isEmpty()) {
            return Map.of();
        }
        return bookingRepository.findByLiveClassIdIn(sessions.stream().map(LiveClass::getId).toList()).stream()
                .collect(Collectors.groupingBy(booking -> booking.getLiveClass().getId()));
    }

    private static LiveClassEnrolment activeBooking(List<LiveClassEnrolment> bookings, StudentProfile student) {
        return bookings.stream()
                .filter(booking -> booking.getStudent().getId().equals(student.getId()))
                .filter(booking -> HOLDS_SEAT.contains(booking.getEnrolmentStatus()))
                .findFirst()
                .orElse(null);
    }

    private Map<Long, Integer> classProgress(CoursePackage coursePackage, List<Long> studentIds) {
        List<Lesson> lessons = content.lessonsInOrder(coursePackage, false).stream()
                .filter(ClassContentSupport::isLessonLive)
                .toList();
        Map<Long, Map<Long, ClassProgressService.LessonState>> states =
                progress.lessonStates(studentIds, lessons, progress.liveParts(lessons));
        Map<Long, Integer> result = new HashMap<>();
        for (Long studentId : studentIds) {
            int completed = (int) states.getOrDefault(studentId, Map.of()).values().stream()
                    .filter(ClassProgressService.LessonState::completed)
                    .count();
            result.put(studentId, ClassProgressService.classPercent(completed, lessons.size()));
        }
        return result;
    }

    /** Level from the student's XP (100 XP per level), as the XP level helper of the frontend counts it. */
    public static int xpLevel(StudentProfile student) {
        int points = student.getTotalPoints() == null ? 0 : Math.max(0, student.getTotalPoints());
        return points / 100 + 1;
    }

    private static String details(LiveClass session) {
        return String.join("|", session.getTitle(), String.valueOf(session.getMeetingLink()),
                String.valueOf(session.getMeetingPlatform()), String.valueOf(session.getMaxParticipants()),
                String.valueOf(session.getLesson() == null ? null : session.getLesson().getId()));
    }

    private String timeZone(String requested) {
        if (requested == null || requested.isBlank()) {
            return platformProperties.defaultTimeZone();
        }
        try {
            return ZoneId.of(requested.strip()).getId();
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Time zone must be a valid IANA time zone.");
        }
    }

    static OffsetDateTime instant(LiveClass session, LocalDateTime wallClock) {
        if (wallClock == null) {
            return null;
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(session.getTimeZone());
        } catch (DateTimeException | NullPointerException exception) {
            zone = ZoneId.systemDefault();
        }
        return wallClock.atZone(zone).toOffsetDateTime();
    }

    private static String requireWebLink(String value, String message) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isWhitespace)) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        try {
            URI uri = new URI(value.strip());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new ResponseStatusException(BAD_REQUEST, message);
            }
            return uri.normalize().toASCIIString();
        } catch (URISyntaxException exception) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
    }
}
