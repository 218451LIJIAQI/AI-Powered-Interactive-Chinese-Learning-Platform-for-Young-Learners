package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.LiveClassEnrolment;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;
import com.example.aichinese.entity.enums.MeetingPlatform;
import com.example.aichinese.entity.enums.PackageItemType;
import com.example.aichinese.entity.enums.RecordedVideoSourceType;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.LiveClassEnrolmentRepository;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UploadedFileRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class LiveLearningAccessService {

    private static final Set<LiveClassStatus> JOINABLE_STATUSES = LiveClassStatus.JOINABLE;
    private static final Set<LiveClassStatus> CANCELLABLE_STATUSES =
            Set.of(LiveClassStatus.SCHEDULED, LiveClassStatus.RESCHEDULED);
    private static final int JOIN_WINDOW_LEAD_MINUTES = 15;
    /** Enrolments that hold a seat (capacity) and grant join access; CANCELLED/MISSED do not. */
    public static final Set<LiveClassEnrolmentStatus> ACTIVE_ENROLMENT_STATUSES =
            Set.of(LiveClassEnrolmentStatus.ENROLLED, LiveClassEnrolmentStatus.ATTENDED);

    private final StudentProfileRepository studentProfileRepository;
    private final LessonRepository lessonRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final LiveClassRepository liveClassRepository;
    private final LiveClassEnrolmentRepository liveClassEnrolmentRepository;
    private final RecordedLessonVideoRepository recordedLessonVideoRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final PackageItemRepository packageItemRepository;
    private final TeacherContentAccessService teacherContentAccessService;

    public LiveLearningAccessService(
            StudentProfileRepository studentProfileRepository,
            LessonRepository lessonRepository,
            CoursePackageRepository coursePackageRepository,
            LiveClassRepository liveClassRepository,
            LiveClassEnrolmentRepository liveClassEnrolmentRepository,
            RecordedLessonVideoRepository recordedLessonVideoRepository,
            UploadedFileRepository uploadedFileRepository,
            PackageItemRepository packageItemRepository,
            TeacherContentAccessService teacherContentAccessService) {
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "studentProfileRepository");
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository");
        this.coursePackageRepository = Objects.requireNonNull(coursePackageRepository, "coursePackageRepository");
        this.liveClassRepository = Objects.requireNonNull(liveClassRepository, "liveClassRepository");
        this.liveClassEnrolmentRepository = Objects.requireNonNull(liveClassEnrolmentRepository, "liveClassEnrolmentRepository");
        this.recordedLessonVideoRepository = Objects.requireNonNull(recordedLessonVideoRepository, "recordedLessonVideoRepository");
        this.uploadedFileRepository = Objects.requireNonNull(uploadedFileRepository, "uploadedFileRepository");
        this.packageItemRepository = Objects.requireNonNull(packageItemRepository, "packageItemRepository");
        this.teacherContentAccessService = Objects.requireNonNull(teacherContentAccessService, "teacherContentAccessService");
    }

    @Transactional(readOnly = true)
    public StudentProfile requireStudent(SecurityUserDetails currentUser) {
        return studentProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student profile could not be found."));
    }

    @Transactional
    public StudentProfile requireLockedStudent(SecurityUserDetails currentUser) {
        return studentProfileRepository.findLockedByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Student profile could not be found."));
    }

    @Transactional(readOnly = true)
    public TeacherProfile requireApprovedTeacher(SecurityUserDetails currentUser) {
        return teacherContentAccessService.requireApprovedTeacher(currentUser);
    }

    @Transactional(readOnly = true)
    public Lesson requireOwnedLesson(SecurityUserDetails currentUser, Long lessonId) {
        requireApprovedTeacher(currentUser);
        return lessonRepository.findByIdAndTeacherUserId(lessonId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested lesson was not found."));
    }

    @Transactional(readOnly = true)
    public Lesson requirePublishedLesson(Long lessonId) {
        return lessonRepository.findByIdAndContentStatus(lessonId, ContentStatus.PUBLISHED)
                .filter(ContentVisibilityPolicy::isLessonVisible)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Published lesson was not found."));
    }

    @Transactional(readOnly = true)
    public CoursePackage requireOwnedPackage(SecurityUserDetails currentUser, Long packageId) {
        requireApprovedTeacher(currentUser);
        return coursePackageRepository.findByIdAndTeacherUserId(packageId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested package was not found."));
    }

    @Transactional(readOnly = true)
    public CoursePackage requireOwnedPackageContainingLesson(SecurityUserDetails currentUser, Long packageId, Long lessonId) {
        CoursePackage coursePackage = requireOwnedPackage(currentUser, packageId);
        Lesson lesson = lessonRepository.findByIdAndTeacherUserId(lessonId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested lesson was not found."));
        if (!isPackageRelatedToLesson(coursePackage.getId(), lesson)) {
            throw new ResponseStatusException(BAD_REQUEST, "Selected package must contain the requested lesson.");
        }
        return coursePackage;
    }

    @Transactional(readOnly = true)
    public LiveClass requireTeacherOwnedLiveClass(SecurityUserDetails currentUser, Long liveClassId) {
        requireApprovedTeacher(currentUser);
        return liveClassRepository.findByIdAndTeacherUserId(liveClassId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested live class was not found."));
    }

    @Transactional(readOnly = true)
    public LiveClass requireLiveClass(Long liveClassId) {
        return liveClassRepository.findById(liveClassId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested live class was not found."));
    }

    @Transactional
    public LiveClass requireLockedLiveClass(Long liveClassId) {
        return liveClassRepository.findLockedById(liveClassId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested live class was not found."));
    }

    @Transactional(readOnly = true)
    public RecordedLessonVideo requireTeacherOwnedRecordedVideo(SecurityUserDetails currentUser, Long lessonId, Long videoId) {
        requireApprovedTeacher(currentUser);
        RecordedLessonVideo video = recordedLessonVideoRepository.findByIdAndLessonTeacherUserId(videoId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Recorded video was not found."));
        if (!Objects.equals(video.getLesson().getId(), lessonId)) {
            throw new ResponseStatusException(NOT_FOUND, "Recorded video was not found.");
        }
        return video;
    }

    @Transactional(readOnly = true)
    public RecordedLessonVideo requireAccessibleRecordedVideo(SecurityUserDetails currentUser, Long lessonId, Long videoId) {
        requireStudent(currentUser);
        requirePublishedLesson(lessonId);

        RecordedLessonVideo video = recordedLessonVideoRepository.findByIdAndLessonIdAndActiveTrue(videoId, lessonId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Recorded video was not found."));

        if (video.getCoursePackage() != null) {
            ensurePublishedPackage(video.getCoursePackage());
        }

        return video;
    }

    @Transactional(readOnly = true)
    public UploadedFile requireAccessibleRecordedFile(SecurityUserDetails currentUser, Long videoId) {
        requireStudent(currentUser);
        RecordedLessonVideo video = requireActiveRecordedVideo(videoId);
        requirePublishedLesson(video.getLesson().getId());
        if (video.getUploadedFile() == null) {
            throw new ResponseStatusException(NOT_FOUND, "Recorded video file was not found.");
        }
        if (video.getCoursePackage() != null) {
            ensurePublishedPackage(video.getCoursePackage());
        }
        return uploadedFileRepository.findById(video.getUploadedFile().getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Recorded video file was not found."));
    }

    @Transactional(readOnly = true)
    public RecordedLessonVideo requireActiveRecordedVideo(Long videoId) {
        return recordedLessonVideoRepository.findByIdAndActiveTrue(videoId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Recorded video was not found."));
    }

    /** ENROLLED or ATTENDED enrolment of the student (cancelled rows are kept as history only). */
    public LiveClassEnrolment requireActiveEnrolment(Long liveClassId, Long studentId) {
        return liveClassEnrolmentRepository
                .findFirstByLiveClassIdAndStudentIdAndEnrolmentStatusInOrderByIdDesc(
                        liveClassId, studentId, ACTIVE_ENROLMENT_STATUSES)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Live class enrolment was not found."));
    }

    @Transactional(readOnly = true)
    public LiveClassEnrolment requireCancellableEnrolment(Long liveClassId, Long studentId) {
        LiveClassEnrolment enrolment = requireActiveEnrolment(liveClassId, studentId);
        LiveClass liveClass = enrolment.getLiveClass();
        boolean isFutureClass = liveClass != null
                && liveClass.getScheduledStartAt() != null
                && LocalDateTime.now().isBefore(liveClass.getScheduledStartAt());
        boolean isOpenForCancellation = liveClass != null
                && CANCELLABLE_STATUSES.contains(liveClass.getLiveClassStatus());

        if (enrolment.getEnrolmentStatus() != LiveClassEnrolmentStatus.ENROLLED
                || !isFutureClass
                || !isOpenForCancellation) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Only an enrolled future live class can be cancelled.");
        }
        return enrolment;
    }

    @Transactional(readOnly = true)
    public void ensureStudentCanEnrol(LiveClass liveClass) {
        ensureLiveClassVisibleToStudents(liveClass);
        if (hasEnded(liveClass)) {
            throw new ResponseStatusException(BAD_REQUEST, "This live class has already ended.");
        }
        if (liveClass.getMaxParticipants() != null
                && countSeatsTaken(liveClass) >= liveClass.getMaxParticipants()) {
            throw new ResponseStatusException(BAD_REQUEST, "This live class is already full.");
        }
    }

    /** Students enrol only in upcoming or running classes (SRS 4.12): a class past its planned end is closed. */
    public static boolean hasEnded(LiveClass liveClass) {
        return liveClass.getScheduledEndAt() != null && !LocalDateTime.now().isBefore(liveClass.getScheduledEndAt());
    }

    /** Seats taken under the participant limit: ENROLLED and ATTENDED enrolments (D26). */
    @Transactional(readOnly = true)
    public long countSeatsTaken(LiveClass liveClass) {
        return liveClassEnrolmentRepository.countByLiveClassIdAndEnrolmentStatusIn(
                liveClass.getId(),
                ACTIVE_ENROLMENT_STATUSES);
    }

    /** Checks visibility, status and join window, then returns the student's active enrolment. */
    @Transactional(readOnly = true)
    public LiveClassEnrolment ensureStudentCanJoin(StudentProfile student, LiveClass liveClass) {
        ensureLiveClassVisibleToStudents(liveClass);
        if (!JOINABLE_STATUSES.contains(liveClass.getLiveClassStatus())) {
            throw new ResponseStatusException(FORBIDDEN, "This live class is not available to join.");
        }
        if (!isJoinWindowOpen(liveClass)) {
            throw new ResponseStatusException(FORBIDDEN, "The live class join window is not open yet.");
        }
        return requireActiveEnrolment(liveClass.getId(), student.getId());
    }

    public boolean isJoinWindowOpen(LiveClass liveClass) {
        if (liveClass == null
                || !JOINABLE_STATUSES.contains(liveClass.getLiveClassStatus())
                || liveClass.getScheduledStartAt() == null
                || liveClass.getScheduledEndAt() == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(liveClass.getScheduledStartAt().minusMinutes(JOIN_WINDOW_LEAD_MINUTES))
                && now.isBefore(liveClass.getScheduledEndAt());
    }

    /**
     * Every published lesson is free for any signed-in student; the gate is the shared
     * {@link ContentVisibilityPolicy}: PUBLISHED lesson, PUBLISHED module (if any) and an
     * approved, active teacher.
     */
    public void ensurePublishedLesson(Lesson lesson) {
        if (!ContentVisibilityPolicy.isLessonVisible(lesson)) {
            throw new ResponseStatusException(NOT_FOUND, "Published lesson was not found.");
        }
    }

    /**
     * Every published course is free for any signed-in student; the gate is a PUBLISHED course
     * owned by an approved, active teacher ({@link ContentVisibilityPolicy}).
     */
    public void ensurePublishedPackage(CoursePackage coursePackage) {
        if (!ContentVisibilityPolicy.isPackageVisible(coursePackage)) {
            throw new ResponseStatusException(NOT_FOUND, "Published course was not found.");
        }
    }

    public MeetingPlatform parseMeetingPlatform(String value) {
        try {
            return MeetingPlatform.fromValue(value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage());
        }
    }

    public LiveClassStatus parseLiveClassStatus(String value, LiveClassStatus fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return LiveClassStatus.tryFrom(value)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Live class status is invalid."));
    }

    public RecordedVideoSourceType parseRecordedVideoSourceType(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Recorded video source type is required.");
        }
        return RecordedVideoSourceType.tryFrom(value)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Recorded video source type is invalid."));
    }

    public String validateMeetingLink(String meetingLink, MeetingPlatform platform) {
        if (platform == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Meeting platform is required.");
        }

        URI uri = parseSupportedWebUri(meetingLink, "Meeting link must be a valid URL.");
        if (!platform.allowsHost(uri.getHost())) {
            throw new ResponseStatusException(BAD_REQUEST, "Meeting link host does not match the selected meeting platform.");
        }
        return uri.normalize().toASCIIString();
    }

    private static String validateExternalVideoUrl(String value) {
        return normalizeSupportedWebUri(value, "External video URL must be valid.");
    }

    public void validateLiveClassTiming(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new ResponseStatusException(BAD_REQUEST, "End time must be after start time.");
        }
    }

    public String validateRecordedVideoSource(
            RecordedVideoSourceType sourceType,
            String externalVideoUrl,
            UploadedFile uploadedFile) {
        if (sourceType == RecordedVideoSourceType.EXTERNAL_URL) {
            if (externalVideoUrl == null || externalVideoUrl.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "External video URL is required.");
            }
            return validateExternalVideoUrl(externalVideoUrl);
        }

        if (uploadedFile == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded recorded video file is required.");
        }
        return null;
    }

    /** True while the student holds an ENROLLED or ATTENDED (joined) enrolment. */
    public boolean isStudentEnrolled(Long liveClassId, Long studentId) {
        return liveClassEnrolmentRepository.existsByLiveClassIdAndStudentIdAndEnrolmentStatusIn(
                liveClassId,
                studentId,
                ACTIVE_ENROLMENT_STATUSES);
    }

    public Set<Long> findPublishedPackageIdsContainingLesson(Lesson lesson) {
        Set<Long> directPackageIds = packageItemRepository
                .findByItemTypeAndItemId(PackageItemType.LESSON, lesson.getId()).stream()
                .filter(item -> ContentVisibilityPolicy.isPackageVisible(item.getCoursePackage()))
                .map(item -> item.getCoursePackage().getId())
                .collect(Collectors.toSet());
        if (lesson.getModule() != null) {
            packageItemRepository
                    .findByItemTypeAndItemId(PackageItemType.MODULE, lesson.getModule().getId()).stream()
                    .filter(item -> ContentVisibilityPolicy.isPackageVisible(item.getCoursePackage()))
                    .map(item -> item.getCoursePackage().getId())
                    .forEach(directPackageIds::add);
        }
        return directPackageIds;
    }

    private boolean isPackageRelatedToLesson(Long packageId, Lesson lesson) {
        if (packageItemRepository.existsByCoursePackageIdAndItemTypeAndItemId(
                packageId,
                PackageItemType.LESSON,
                lesson.getId())) {
            return true;
        }
        return lesson.getModule() != null
                && packageItemRepository.existsByCoursePackageIdAndItemTypeAndItemId(
                        packageId,
                        PackageItemType.MODULE,
                        lesson.getModule().getId());
    }

    private void ensureLiveClassVisibleToStudents(LiveClass liveClass) {
        if (!liveClass.getLiveClassStatus().isStudentVisible()) {
            throw new ResponseStatusException(FORBIDDEN, "This live class is not available.");
        }

        if (liveClass.getLesson() != null && !ContentVisibilityPolicy.isLessonVisible(liveClass.getLesson())) {
            throw new ResponseStatusException(NOT_FOUND, "Published lesson was not found.");
        }

        if (liveClass.getCoursePackage() != null
                && !ContentVisibilityPolicy.isPackageVisible(liveClass.getCoursePackage())) {
            throw new ResponseStatusException(NOT_FOUND, "Published course was not found.");
        }
    }

    private static String normalizeSupportedWebUri(String value, String invalidMessage) {
        return parseSupportedWebUri(value, invalidMessage).normalize().toASCIIString();
    }

    private static URI parseSupportedWebUri(String value, String invalidMessage) {
        if (value == null || value.isBlank() || containsUnsafeUrlCodePoint(value) || value.indexOf('\\') >= 0) {
            throw new ResponseStatusException(BAD_REQUEST, invalidMessage);
        }

        try {
            URI uri = new URI(value.strip());
            if (!isSupportedWebUri(uri)
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw new ResponseStatusException(BAD_REQUEST, invalidMessage);
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new ResponseStatusException(BAD_REQUEST, invalidMessage);
        }
    }

    private static boolean isSupportedWebUri(URI uri) {
        if (uri == null || uri.isOpaque() || uri.getScheme() == null || uri.getHost() == null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return "http".equals(scheme) || "https".equals(scheme);
    }

    private static boolean containsUnsafeUrlCodePoint(String value) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT) {
                return true;
            }
        }
        return false;
    }
}
