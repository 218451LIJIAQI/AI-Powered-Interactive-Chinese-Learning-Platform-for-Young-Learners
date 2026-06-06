package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.LiveClassEnrolment;
import com.example.aichinese.entity.UploadedFile;
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
import com.example.aichinese.service.EntitlementService;
import com.example.aichinese.service.LiveLearningAccessService;
import com.example.aichinese.service.TeacherContentAccessService;

class LiveLearningAccessServiceTests {

    private static final String INVALID_MEETING_LINK_MESSAGE = "Meeting link must be a valid URL.";
    private static final String PLATFORM_HOST_MISMATCH_MESSAGE =
            "Meeting link host does not match the selected meeting platform.";
    private static final String UPLOADED_VIDEO_REQUIRED_MESSAGE = "Uploaded recorded video file is required.";
    private static final String CANCELLATION_REJECTED_MESSAGE =
            "Only an enrolled future live class can be cancelled.";
    private static final String CANONICAL_MEETING_LINK = "HTTPS://example.com/class?room=1";
    private static final String EXTERNAL_VIDEO_URL = "https://video.example.com/course/../lesson.mp4?quality=hd";
    private static final String CANONICAL_EXTERNAL_VIDEO_URL = "https://video.example.com/lesson.mp4?quality=hd";
    private static final long CANCELLABLE_LIVE_CLASS_ID = 10L;
    private static final long STARTED_LIVE_CLASS_ID = 11L;
    private static final long STUDENT_ID = 20L;

    private final LiveClassEnrolmentRepository liveClassEnrolmentRepository = mock(LiveClassEnrolmentRepository.class);

    private final LiveLearningAccessService service = new LiveLearningAccessService(
            mock(StudentProfileRepository.class),
            mock(LessonRepository.class),
            mock(CoursePackageRepository.class),
            mock(LiveClassRepository.class),
            liveClassEnrolmentRepository,
            mock(RecordedLessonVideoRepository.class),
            mock(UploadedFileRepository.class),
            mock(PackageItemRepository.class),
            mock(TeacherContentAccessService.class),
            mock(EntitlementService.class));

    @Test
    void validateMeetingLinkReturnsCanonicalSafeHttpUrl() {
        assertThat(service.validateMeetingLink("  HTTPS://example.com/live/../class?room=1  "))
                .isEqualTo(CANONICAL_MEETING_LINK);
    }

    @Test
    void validateMeetingLinkRejectsAmbiguousOrCredentialedUrls() {
        assertUnsafeMeetingUrl("https://user@example.com/class");
        assertUnsafeMeetingUrl("https://example.com/class#token");
        assertUnsafeMeetingUrl("https:\\\\example.com\\class");
        assertUnsafeMeetingUrl("javascript:alert(1)");
        assertUnsafeMeetingUrl("https://example.com/\u202eclass");
    }

    @Test
    void parseMeetingPlatformAcceptsFrontendDisplayLabels() {
        assertThat(service.parseMeetingPlatform("Google Meet")).isEqualTo(MeetingPlatform.GOOGLE_MEET);
        assertThat(service.parseMeetingPlatform("Microsoft Teams")).isEqualTo(MeetingPlatform.MICROSOFT_TEAMS);
        assertThat(service.parseMeetingPlatform("ZOOM")).isEqualTo(MeetingPlatform.ZOOM);
    }

    @Test
    void validateMeetingLinkEnforcesSelectedPlatformHost() {
        assertThat(service.validateMeetingLink("https://meet.google.com/abc-defg-hij", MeetingPlatform.GOOGLE_MEET))
                .isEqualTo("https://meet.google.com/abc-defg-hij");
        assertThat(service.validateMeetingLink("https://us02web.zoom.us/j/123456789", MeetingPlatform.ZOOM))
                .isEqualTo("https://us02web.zoom.us/j/123456789");
        assertThat(service.validateMeetingLink("https://classroom.example.com/join", MeetingPlatform.OTHER))
                .isEqualTo("https://classroom.example.com/join");

        assertThatThrownBy(() -> service.validateMeetingLink(
                "https://evil.example.com/google-meet-lookalike",
                MeetingPlatform.GOOGLE_MEET))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(PLATFORM_HOST_MISMATCH_MESSAGE);
    }

    @Test
    void validateRecordedVideoSourceReturnsCanonicalExternalUrl() {
        assertThat(service.validateRecordedVideoSource(
                RecordedVideoSourceType.EXTERNAL_URL,
                EXTERNAL_VIDEO_URL,
                null))
                .isEqualTo(CANONICAL_EXTERNAL_VIDEO_URL);
    }

    @Test
    void validateRecordedVideoSourceRejectsMissingFileForUploadedSource() {
        assertThatThrownBy(() -> service.validateRecordedVideoSource(
                RecordedVideoSourceType.UPLOADED_FILE,
                null,
                null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(UPLOADED_VIDEO_REQUIRED_MESSAGE);

        assertThat(service.validateRecordedVideoSource(
                RecordedVideoSourceType.UPLOADED_FILE,
                null,
                new UploadedFile()))
                .isNull();
    }

    @Test
    void requireCancellableEnrolmentAllowsOnlyEnrolledFutureClasses() {
        LocalDateTime now = LocalDateTime.now();
        LiveClassEnrolment enrolment = enrolment(
                LiveClassEnrolmentStatus.ENROLLED,
                LiveClassStatus.SCHEDULED,
                now.plusHours(1));
        stubEnrolment(CANCELLABLE_LIVE_CLASS_ID, enrolment);

        assertThat(service.requireCancellableEnrolment(CANCELLABLE_LIVE_CLASS_ID, STUDENT_ID))
                .isSameAs(enrolment);
    }

    @Test
    void requireCancellableEnrolmentRejectsAttendedOrStartedClasses() {
        LocalDateTime now = LocalDateTime.now();
        stubEnrolment(CANCELLABLE_LIVE_CLASS_ID, enrolment(
                LiveClassEnrolmentStatus.ATTENDED,
                LiveClassStatus.SCHEDULED,
                now.plusHours(1)));
        stubEnrolment(STARTED_LIVE_CLASS_ID, enrolment(
                LiveClassEnrolmentStatus.ENROLLED,
                LiveClassStatus.ONGOING,
                now.minusMinutes(5)));

        assertCancellationRejected(CANCELLABLE_LIVE_CLASS_ID);
        assertCancellationRejected(STARTED_LIVE_CLASS_ID);
    }

    @Test
    void joinWindowOpensFifteenMinutesBeforeStartAndClosesAfterEnd() {
        LocalDateTime now = LocalDateTime.now();

        assertThat(service.isJoinWindowOpen(scheduledClass(
                now.plusMinutes(16),
                now.plusHours(1))))
                .isFalse();
        assertThat(service.isJoinWindowOpen(scheduledClass(
                now.plusMinutes(10),
                now.plusHours(1))))
                .isTrue();
        assertThat(service.isJoinWindowOpen(scheduledClass(
                now.minusHours(1),
                now.minusMinutes(1))))
                .isFalse();
    }

    private static LiveClass scheduledClass(LocalDateTime scheduledStartAt, LocalDateTime scheduledEndAt) {
        LiveClass liveClass = new LiveClass();
        liveClass.setLiveClassStatus(LiveClassStatus.SCHEDULED);
        liveClass.setScheduledStartAt(scheduledStartAt);
        liveClass.setScheduledEndAt(scheduledEndAt);
        return liveClass;
    }

    private static LiveClassEnrolment enrolment(
            LiveClassEnrolmentStatus enrolmentStatus,
            LiveClassStatus liveClassStatus,
            LocalDateTime scheduledStartAt) {
        LiveClass liveClass = new LiveClass();
        liveClass.setLiveClassStatus(liveClassStatus);
        liveClass.setScheduledStartAt(scheduledStartAt);
        LiveClassEnrolment enrolment = new LiveClassEnrolment();
        enrolment.setLiveClass(liveClass);
        enrolment.setEnrolmentStatus(enrolmentStatus);
        return enrolment;
    }

    private void assertUnsafeMeetingUrl(String value) {
        assertThatThrownBy(() -> service.validateMeetingLink(value))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(INVALID_MEETING_LINK_MESSAGE);
    }

    private void stubEnrolment(Long liveClassId, LiveClassEnrolment enrolment) {
        when(liveClassEnrolmentRepository.findByLiveClassIdAndStudentId(liveClassId, STUDENT_ID))
                .thenReturn(Optional.of(enrolment));
    }

    private void assertCancellationRejected(Long liveClassId) {
        assertThatThrownBy(() -> service.requireCancellableEnrolment(liveClassId, STUDENT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(CANCELLATION_REJECTED_MESSAGE);
    }
}
