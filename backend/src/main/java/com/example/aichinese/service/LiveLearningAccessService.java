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
import com.example.aichinese.entity.enums.AccessType;
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

/**
 * Centralised access-control for everything in the live-learning
 * surface area: live classes, recorded lesson videos, and the
 * student-side join / playback flows.
 *
 * <h2>Why one service</h2>
 * <p>{@link LiveClassService}, {@link RecordedLessonService},
 * {@link VideoProgressService}, and {@link StudentLearningService}
 * all need to ask the same questions: "is this student allowed to
 * see this live class / video?", "is the join window open?", "is the
 * meeting URL safe to expose?". Concentrating those checks here keeps
 * the policy auditable in one file.</p>
 *
 * <h2>Join-window policy</h2>
 * <p>{@link #JOINABLE_STATUSES} ({@code SCHEDULED}, {@code ONGOING})
 * combined with the per-class lead-time window determine when the
 * meeting URL is released. {@code COMPLETED} / {@code CANCELLED}
 * sessions never expose the URL again, even to the teacher, so
 * post-class links cannot leak.</p>
 *
 * <h2>Meeting-platform allowlist</h2>
 * <p>Meeting URLs are validated against the allowed schemes / hosts
 * of {@link MeetingPlatform} so a teacher cannot trick a student into
 * launching an arbitrary URL via the join button.</p>
 */
@Service
public class LiveLearningAccessService {

    // Delegate to the canonical set on the enum so the Javadoc contract
    // ("Mirrors LiveLearningAccessService.JOINABLE_STATUSES") is enforced
    // by the type system rather than by parallel literal lists.  The
    // underlying implementation is still an immutable EnumSet wrapper,
    // so contains() remains O(1).
    private static final Set<LiveClassStatus> JOINABLE_STATUSES = LiveClassStatus.JOINABLE;

    private final StudentProfileRepository studentProfileRepository;
    private final LessonRepository lessonRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final LiveClassRepository liveClassRepository;
    private final LiveClassEnrolmentRepository liveClassEnrolmentRepository;
    private final RecordedLessonVideoRepository recordedLessonVideoRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final PackageItemRepository packageItemRepository;
    private final TeacherContentAccessService teacherContentAccessService;
    private final EntitlementService entitlementService;

    public LiveLearningAccessService(
            StudentProfileRepository studentProfileRepository,
            LessonRepository lessonRepository,
            CoursePackageRepository coursePackageRepository,
            LiveClassRepository liveClassRepository,
            LiveClassEnrolmentRepository liveClassEnrolmentRepository,
            RecordedLessonVideoRepository recordedLessonVideoRepository,
            UploadedFileRepository uploadedFileRepository,
            PackageItemRepository packageItemRepository,
            TeacherContentAccessService teacherContentAccessService,
            EntitlementService entitlementService) {
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "studentProfileRepository");
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository");
        this.coursePackageRepository = Objects.requireNonNull(coursePackageRepository, "coursePackageRepository");
        this.liveClassRepository = Objects.requireNonNull(liveClassRepository, "liveClassRepository");
        this.liveClassEnrolmentRepository = Objects.requireNonNull(liveClassEnrolmentRepository, "liveClassEnrolmentRepository");
        this.recordedLessonVideoRepository = Objects.requireNonNull(recordedLessonVideoRepository, "recordedLessonVideoRepository");
        this.uploadedFileRepository = Objects.requireNonNull(uploadedFileRepository, "uploadedFileRepository");
        this.packageItemRepository = Objects.requireNonNull(packageItemRepository, "packageItemRepository");
        this.teacherContentAccessService = Objects.requireNonNull(teacherContentAccessService, "teacherContentAccessService");
        this.entitlementService = Objects.requireNonNull(entitlementService, "entitlementService");
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
        StudentProfile student = requireStudent(currentUser);
        Lesson lesson = requirePublishedLesson(lessonId);
        ensureStudentHasLessonAccess(student, lesson);

        RecordedLessonVideo video = recordedLessonVideoRepository.findByIdAndLessonIdAndActiveTrue(videoId, lessonId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Recorded video was not found."));

        if (video.getCoursePackage() != null) {
            ensureStudentHasPackageAccess(student, video.getCoursePackage());
        }

        return video;
    }

    @Transactional(readOnly = true)
    public UploadedFile requireAccessibleRecordedFile(SecurityUserDetails currentUser, Long videoId) {
        StudentProfile student = requireStudent(currentUser);
        RecordedLessonVideo video = requireActiveRecordedVideo(videoId);
        Lesson lesson = requirePublishedLesson(video.getLesson().getId());
        ensureStudentHasLessonAccess(student, lesson);
        if (video.getUploadedFile() == null) {
            throw new ResponseStatusException(NOT_FOUND, "Recorded video file was not found.");
        }
        if (video.getCoursePackage() != null) {
            ensureStudentHasPackageAccess(student, video.getCoursePackage());
        }
        return uploadedFileRepository.findById(video.getUploadedFile().getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Recorded video file was not found."));
    }

    @Transactional(readOnly = true)
    public RecordedLessonVideo requireActiveRecordedVideo(Long videoId) {
        return recordedLessonVideoRepository.findByIdAndActiveTrue(videoId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Recorded video was not found."));
    }

    @Transactional(readOnly = true)
    public LiveClassEnrolment requireActiveEnrolment(Long liveClassId, Long studentId) {
        LiveClassEnrolment enrolment = liveClassEnrolmentRepository.findByLiveClassIdAndStudentId(liveClassId, studentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Live class enrolment was not found."));
        if (enrolment.getEnrolmentStatus() != LiveClassEnrolmentStatus.ENROLLED
                && enrolment.getEnrolmentStatus() != LiveClassEnrolmentStatus.ATTENDED) {
            throw new ResponseStatusException(NOT_FOUND, "Live class enrolment was not found.");
        }
        return enrolment;
    }

    @Transactional(readOnly = true)
    public void ensureStudentCanEnrol(StudentProfile student, LiveClass liveClass) {
        ensureLiveClassVisibleToStudents(liveClass);
        ensureStudentHasLinkedAccess(student, liveClass);
        if (liveClass.getMaxParticipants() != null) {
            long enrolledCount = liveClassEnrolmentRepository.countByLiveClassIdAndEnrolmentStatus(
                    liveClass.getId(),
                    LiveClassEnrolmentStatus.ENROLLED);
            if (enrolledCount >= liveClass.getMaxParticipants()) {
                throw new ResponseStatusException(BAD_REQUEST, "This live class is already full.");
            }
        }
    }

    @Transactional(readOnly = true)
    public void ensureStudentCanJoin(StudentProfile student, LiveClass liveClass) {
        ensureLiveClassVisibleToStudents(liveClass);
        if (!JOINABLE_STATUSES.contains(liveClass.getLiveClassStatus())) {
            throw new ResponseStatusException(FORBIDDEN, "This live class is not available to join.");
        }
        requireActiveEnrolment(liveClass.getId(), student.getId());
        ensureStudentHasLinkedAccess(student, liveClass);
    }

    @Transactional(readOnly = true)
    public boolean hasStudentLessonAccess(StudentProfile student, Lesson lesson) {
        if (lesson.getAccessType() != AccessType.PAID) {
            return true;
        }

        boolean directLessonAccess = entitlementService.hasAccessToItem(student.getId(), "LESSON", lesson.getId());
        boolean moduleAccess = lesson.getModule() != null
                && entitlementService.hasAccessToItem(student.getId(), "MODULE", lesson.getModule().getId());
        return directLessonAccess || moduleAccess;
    }

    @Transactional(readOnly = true)
    public boolean hasStudentPackageAccess(StudentProfile student, CoursePackage coursePackage) {
        if (coursePackage.getAccessType() != AccessType.PAID) {
            return true;
        }

        return entitlementService.hasAccessToItem(student.getId(), "PACKAGE", coursePackage.getId());
    }

    @Transactional(readOnly = true)
    public void ensureStudentHasLessonAccess(StudentProfile student, Lesson lesson) {
        if (lesson.getContentStatus() != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(NOT_FOUND, "Published lesson was not found.");
        }
        if (!hasStudentLessonAccess(student, lesson)) {
            throw new ResponseStatusException(FORBIDDEN, "Paid content is locked until payment is completed.");
        }
    }

    @Transactional(readOnly = true)
    public void ensureStudentHasPackageAccess(StudentProfile student, CoursePackage coursePackage) {
        if (coursePackage.getContentStatus() != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(NOT_FOUND, "Published package was not found.");
        }
        if (!hasStudentPackageAccess(student, coursePackage)) {
            throw new ResponseStatusException(FORBIDDEN, "Paid content is locked until payment is completed.");
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

        try {
            return LiveClassStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Live class status is invalid.");
        }
    }

    public RecordedVideoSourceType parseRecordedVideoSourceType(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Recorded video source type is required.");
        }
        try {
            return RecordedVideoSourceType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Recorded video source type is invalid.");
        }
    }

    public String validateMeetingLink(String meetingLink) {
        return normalizeSupportedWebUri(meetingLink, "Meeting link must be a valid URL.");
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

    private String validateExternalVideoUrl(String value) {
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

    public boolean isStudentEnrolled(Long liveClassId, Long studentId) {
        return liveClassEnrolmentRepository.existsByLiveClassIdAndStudentIdAndEnrolmentStatus(
                liveClassId,
                studentId,
                LiveClassEnrolmentStatus.ENROLLED);
    }

    public Set<Long> findPublishedPackageIdsContainingLesson(Lesson lesson) {
        Set<Long> directPackageIds = packageItemRepository
                .findByItemTypeAndItemId(PackageItemType.LESSON, lesson.getId()).stream()
                .filter(item -> item.getCoursePackage() != null
                        && item.getCoursePackage().getContentStatus() == ContentStatus.PUBLISHED)
                .map(item -> item.getCoursePackage().getId())
                .collect(Collectors.toSet());
        if (lesson.getModule() != null) {
            packageItemRepository
                    .findByItemTypeAndItemId(PackageItemType.MODULE, lesson.getModule().getId()).stream()
                    .filter(item -> item.getCoursePackage() != null
                            && item.getCoursePackage().getContentStatus() == ContentStatus.PUBLISHED)
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
        if (!JOINABLE_STATUSES.contains(liveClass.getLiveClassStatus())
                && liveClass.getLiveClassStatus() != LiveClassStatus.RESCHEDULED) {
            throw new ResponseStatusException(FORBIDDEN, "This live class is not available.");
        }

        if (liveClass.getLesson() != null && liveClass.getLesson().getContentStatus() != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(NOT_FOUND, "Published lesson was not found.");
        }

        if (liveClass.getCoursePackage() != null
                && liveClass.getCoursePackage().getContentStatus() != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(NOT_FOUND, "Published package was not found.");
        }
    }

    private void ensureStudentHasLinkedAccess(StudentProfile student, LiveClass liveClass) {
        if (liveClass.getLesson() != null) {
            ensureStudentHasLessonAccess(student, liveClass.getLesson());
        }

        if (liveClass.getCoursePackage() != null) {
            ensureStudentHasPackageAccess(student, liveClass.getCoursePackage());
        }
    }

    private String normalizeSupportedWebUri(String value, String invalidMessage) {
        return parseSupportedWebUri(value, invalidMessage).normalize().toASCIIString();
    }

    private URI parseSupportedWebUri(String value, String invalidMessage) {
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

    private boolean isSupportedWebUri(URI uri) {
        if (uri == null || uri.isOpaque() || uri.getScheme() == null || uri.getHost() == null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return "http".equals(scheme) || "https".equals(scheme);
    }

    private boolean containsUnsafeUrlCodePoint(String value) {
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
