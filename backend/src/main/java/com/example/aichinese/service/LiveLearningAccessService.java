package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

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

    private static final EnumSet<LiveClassStatus> JOINABLE_STATUSES =
            EnumSet.of(LiveClassStatus.SCHEDULED, LiveClassStatus.ONGOING);

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
        this.studentProfileRepository = studentProfileRepository;
        this.lessonRepository = lessonRepository;
        this.coursePackageRepository = coursePackageRepository;
        this.liveClassRepository = liveClassRepository;
        this.liveClassEnrolmentRepository = liveClassEnrolmentRepository;
        this.recordedLessonVideoRepository = recordedLessonVideoRepository;
        this.uploadedFileRepository = uploadedFileRepository;
        this.packageItemRepository = packageItemRepository;
        this.teacherContentAccessService = teacherContentAccessService;
        this.entitlementService = entitlementService;
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
        return coursePackageRepository.findByIdAndTeacherUserId(packageId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested package was not found."));
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
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Meeting platform is required.");
        }
        try {
            return MeetingPlatform.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Meeting platform is invalid.");
        }
    }

    public LiveClassStatus parseLiveClassStatus(String value, LiveClassStatus fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return LiveClassStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Live class status is invalid.");
        }
    }

    public RecordedVideoSourceType parseRecordedVideoSourceType(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Recorded video source type is required.");
        }
        try {
            return RecordedVideoSourceType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Recorded video source type is invalid.");
        }
    }

    public void validateMeetingLink(String meetingLink) {
        try {
            URI uri = new URI(meetingLink);
            if (!isSupportedWebUri(uri)) {
                throw new ResponseStatusException(BAD_REQUEST, "Meeting link must be a valid URL.");
            }
        } catch (URISyntaxException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Meeting link must be a valid URL.");
        }
    }

    private void validateExternalVideoUrl(String value) {
        try {
            URI uri = new URI(value);
            if (!isSupportedWebUri(uri)) {
                throw new ResponseStatusException(BAD_REQUEST, "External video URL must be valid.");
            }
        } catch (URISyntaxException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "External video URL must be valid.");
        }
    }

    public void validateLiveClassTiming(java.time.LocalDateTime startTime, java.time.LocalDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new ResponseStatusException(BAD_REQUEST, "End time must be after start time.");
        }
    }

    public void validateRecordedVideoSource(RecordedVideoSourceType sourceType, String externalVideoUrl, UploadedFile uploadedFile) {
        if (sourceType == RecordedVideoSourceType.EXTERNAL_URL) {
            if (externalVideoUrl == null || externalVideoUrl.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "External video URL is required.");
            }
            validateExternalVideoUrl(externalVideoUrl.trim());
            return;
        }

        if (uploadedFile == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded recorded video file is required.");
        }
    }

    public boolean isStudentEnrolled(Long liveClassId, Long studentId) {
        return liveClassEnrolmentRepository.existsByLiveClassIdAndStudentIdAndEnrolmentStatus(
                liveClassId,
                studentId,
                LiveClassEnrolmentStatus.ENROLLED);
    }

    public Set<Long> findPublishedPackageIdsContainingLesson(Lesson lesson) {
        return packageItemRepository
                .findByItemTypeAndItemId(com.example.aichinese.entity.enums.PackageItemType.LESSON, lesson.getId())
                .stream()
                .filter(item -> item.getCoursePackage() != null
                        && item.getCoursePackage().getContentStatus() == ContentStatus.PUBLISHED)
                .map(item -> item.getCoursePackage().getId())
                .collect(java.util.stream.Collectors.toSet());
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

    private boolean isSupportedWebUri(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        return "http".equals(scheme) || "https".equals(scheme);
    }
}
