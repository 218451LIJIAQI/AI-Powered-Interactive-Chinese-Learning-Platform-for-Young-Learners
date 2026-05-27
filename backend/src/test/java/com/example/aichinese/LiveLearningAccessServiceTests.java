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
                .isEqualTo("HTTPS://example.com/class?room=1");
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
                .hasMessageContaining("Meeting link host does not match the selected meeting platform.");
    }

    @Test
    void validateRecordedVideoSourceReturnsCanonicalExternalUrl() {
        assertThat(service.validateRecordedVideoSource(
                RecordedVideoSourceType.EXTERNAL_URL,
                "https://video.example.com/course/../lesson.mp4?quality=hd",
                null))
                .isEqualTo("https://video.example.com/lesson.mp4?quality=hd");
    }

    @Test
    void validateRecordedVideoSourceRejectsMissingFileForUploadedSource() {
        assertThatThrownBy(() -> service.validateRecordedVideoSource(
                RecordedVideoSourceType.UPLOADED_FILE,
                null,
                null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Uploaded recorded video file is required.");

        assertThat(service.validateRecordedVideoSource(
                RecordedVideoSourceType.UPLOADED_FILE,
                null,
                new UploadedFile()))
                .isNull();
    }

    @Test
    void requireCancellableEnrolmentAllowsOnlyEnrolledFutureClasses() {
        LiveClassEnrolment enrolment = enrolment(
                LiveClassEnrolmentStatus.ENROLLED,
                LiveClassStatus.SCHEDULED,
                LocalDateTime.now().plusHours(1));
        when(liveClassEnrolmentRepository.findByLiveClassIdAndStudentId(10L, 20L))
                .thenReturn(Optional.of(enrolment));

        assertThat(service.requireCancellableEnrolment(10L, 20L)).isSameAs(enrolment);
    }

    @Test
    void requireCancellableEnrolmentRejectsAttendedOrStartedClasses() {
        when(liveClassEnrolmentRepository.findByLiveClassIdAndStudentId(10L, 20L))
                .thenReturn(Optional.of(enrolment(
                        LiveClassEnrolmentStatus.ATTENDED,
                        LiveClassStatus.SCHEDULED,
                        LocalDateTime.now().plusHours(1))));
        when(liveClassEnrolmentRepository.findByLiveClassIdAndStudentId(11L, 20L))
                .thenReturn(Optional.of(enrolment(
                        LiveClassEnrolmentStatus.ENROLLED,
                        LiveClassStatus.ONGOING,
                        LocalDateTime.now().minusMinutes(5))));

        assertThatThrownBy(() -> service.requireCancellableEnrolment(10L, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only an enrolled future live class can be cancelled.");
        assertThatThrownBy(() -> service.requireCancellableEnrolment(11L, 20L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only an enrolled future live class can be cancelled.");
    }

    @Test
    void joinWindowOpensFifteenMinutesBeforeStartAndClosesAfterEnd() {
        assertThat(service.isJoinWindowOpen(scheduledClass(
                LocalDateTime.now().plusMinutes(16),
                LocalDateTime.now().plusHours(1))))
                .isFalse();
        assertThat(service.isJoinWindowOpen(scheduledClass(
                LocalDateTime.now().plusMinutes(10),
                LocalDateTime.now().plusHours(1))))
                .isTrue();
        assertThat(service.isJoinWindowOpen(scheduledClass(
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().minusMinutes(1))))
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
                .hasMessageContaining("Meeting link must be a valid URL.");
    }
}
