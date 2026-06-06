package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.support.TestUploadFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

/**
 * End-to-end MockMvc coverage of synchronous live classes and
 * recorded lesson videos: teacher scheduling, student enrolment,
 * meeting URL release window, and resume-where-you-left-off video
 * progress.
 *
 * <p>Pins the URL-leak defence (meeting URL only released within the
 * join window for enrolled students with valid entitlement) and the
 * heartbeat-based video progress upsert contract.</p>
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveRecordedLearningIntegrationTests {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "SuperSecure123!";
    private static final String USER_PASSWORD = "Password123!";
    private static final String ACCESS_FREE = "FREE";
    private static final String ACCESS_PAID = "PAID";
    private static final String LEARNING_HYBRID = "HYBRID";
    private static final String LEARNING_RECORDED = "RECORDED";
    private static final String PLATFORM_GOOGLE_MEET = "GOOGLE_MEET";
    private static final String PLATFORM_ZOOM = "ZOOM";
    private static final String STATUS_SCHEDULED = "SCHEDULED";
    private static final String STATUS_ONGOING = "ONGOING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_RESCHEDULED = "RESCHEDULED";
    private static final String SOURCE_UPLOADED_FILE = "UPLOADED_FILE";
    private static final String SOURCE_EXTERNAL_URL = "EXTERNAL_URL";
    private static final String ITEM_TYPE_MODULE = "MODULE";
    private static final String QUALIFICATION_DOCUMENT_TYPE = "QUALIFICATION";
    private static final String FILE_PART = "file";
    private static final String PDF_FILE_NAME = "qualification.pdf";
    private static final String MP4_FILE_NAME = "lesson.mp4";
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String MP4_CONTENT_TYPE = "video/mp4";
    private static final int DEFAULT_VIDEO_DURATION_SECONDS = 120;
    private static final String DEFAULT_VIDEO_DURATION_SECONDS_PARAM = String.valueOf(DEFAULT_VIDEO_DURATION_SECONDS);
    private static final String DEFAULT_SORT_ORDER = "1";
    private static final String RECORDED_VIDEO_TITLE = "Lesson Recording";
    private static final String RECORDED_VIDEO_DESCRIPTION = "Watch again at home";
    private static final String SELECTED_PACKAGE_MISMATCH_MESSAGE =
            "Selected package must contain the requested lesson.";
    private static final String SPOOFED_VIDEO_MESSAGE =
            "Uploaded video content does not match the declared MP4/WEBM/MOV type.";
    private static final String PAYLOAD_TOO_LARGE_MESSAGE =
            "The file is too large. Please choose a smaller file and try again.";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int OVERSIZED_PAGE_SIZE = 100_000;
    private static final int DEFAULT_DURATION_MINUTES = 15;
    private static final int DEFAULT_LIVE_CLASS_MINUTES = 45;
    private static final int HALF_PROGRESS_PERCENTAGE = 50;
    private static final int COMPLETED_PROGRESS_PERCENTAGE = 100;
    private static final int OVERSIZED_TEXT_LENGTH = 201;
    private static final int OVERSIZED_EXTERNAL_URL_SUFFIX_LENGTH = 501;
    private static final int OVERSIZED_FILE_BYTES = 2_048;
    private static final long BEGINNER_LEVEL_ID = 1L;
    private static final long VOCABULARY_CATEGORY_ID = 3L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Test
    void approvedTeacherCanCreateUpdateAndRescheduleLiveClass() throws Exception {
        String teacherEmail = uniqueEmail("live-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        String adminToken = adminToken();
        Long lessonId = createPublishedLesson(teacherToken, "Live Lesson");

        LocalDateTime start = LocalDateTime.now().plusDays(2);
        LocalDateTime end = start.plusHours(1);

        MvcResult createResult = mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/live-classes",
                teacherToken,
                Map.of(
                        "title", "Weekend Live Class",
                        "description", "Practice speaking together",
                        "lessonId", lessonId,
                        "startTime", start.toString(),
                        "endTime", end.toString(),
                        "meetingLink", "https://meet.google.com/test-room",
                        "meetingPlatform", PLATFORM_GOOGLE_MEET,
                        "status", STATUS_SCHEDULED,
                        "maxParticipants", 10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingLinkVisible").value(true))
                .andReturn();

        Long liveClassId = extractDataId(createResult);

        mockMvc.perform(authenticatedJsonPut(
                "/api/v1/teachers/live-classes/" + liveClassId,
                teacherToken,
                Map.of(
                        "title", "Weekend Live Class Updated",
                        "description", "Practice speaking together",
                        "lessonId", lessonId,
                        "startTime", start.plusHours(1).toString(),
                        "endTime", end.plusHours(1).toString(),
                        "meetingLink", "https://meet.google.com/test-room-updated",
                        "meetingPlatform", PLATFORM_GOOGLE_MEET,
                        "status", STATUS_SCHEDULED,
                        "maxParticipants", 12)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Weekend Live Class Updated"));

        mockMvc.perform(authenticatedJsonPatch(
                "/api/v1/teachers/live-classes/" + liveClassId + "/reschedule",
                teacherToken,
                Map.of(
                        "startTime", start.plusDays(1).toString(),
                        "endTime", end.plusDays(1).toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liveClassStatus").value(STATUS_RESCHEDULED));

        mockMvc.perform(authenticatedGet("/api/v1/admin/live-classes/" + liveClassId, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingLinkVisible").value(true));

        mockMvc.perform(oversizedPageGet("/api/v1/teachers/live-classes", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(MAX_PAGE_SIZE))
                .andExpect(jsonPath("$.data.items").isArray());

        mockMvc.perform(oversizedPageGet("/api/v1/admin/live-classes", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(MAX_PAGE_SIZE))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void liveClassStatusLifecycleRejectsInvalidAndTerminalTransitions() throws Exception {
        String teacherEmail = uniqueEmail("lifecycle-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        Long lessonId = createPublishedLesson(teacherToken, "Lifecycle Lesson");

        LocalDateTime start = LocalDateTime.now().plusDays(2);
        LocalDateTime end = start.plusHours(1);

        mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/live-classes",
                teacherToken,
                Map.of(
                        "title", "Invalid Initial Status",
                        "description", "Cannot start created as ongoing",
                        "lessonId", lessonId,
                        "startTime", start.toString(),
                        "endTime", end.toString(),
                        "meetingLink", "https://meet.google.com/invalid-initial",
                        "meetingPlatform", PLATFORM_GOOGLE_MEET,
                        "status", STATUS_ONGOING)))
                .andExpect(status().isBadRequest());

        MvcResult createResult = mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/live-classes",
                teacherToken,
                Map.of(
                        "title", "Lifecycle Live Class",
                        "description", "State machine coverage",
                        "lessonId", lessonId,
                        "startTime", start.toString(),
                        "endTime", end.toString(),
                        "meetingLink", "https://meet.google.com/lifecycle",
                        "meetingPlatform", PLATFORM_GOOGLE_MEET,
                        "status", STATUS_SCHEDULED)))
                .andExpect(status().isOk())
                .andReturn();

        Long liveClassId = extractDataId(createResult);

        mockMvc.perform(authenticatedJsonPatch(
                "/api/v1/teachers/live-classes/" + liveClassId + "/status",
                teacherToken,
                Map.of("status", STATUS_ONGOING)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liveClassStatus").value(STATUS_ONGOING));

        mockMvc.perform(authenticatedJsonPatch(
                "/api/v1/teachers/live-classes/" + liveClassId + "/status",
                teacherToken,
                Map.of("status", STATUS_SCHEDULED)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(authenticatedJsonPatch(
                "/api/v1/teachers/live-classes/" + liveClassId + "/status",
                teacherToken,
                Map.of("status", STATUS_COMPLETED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liveClassStatus").value(STATUS_COMPLETED));

        mockMvc.perform(authenticatedPatch("/api/v1/teachers/live-classes/" + liveClassId + "/cancel", teacherToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(authenticatedJsonPatch(
                "/api/v1/teachers/live-classes/" + liveClassId + "/reschedule",
                teacherToken,
                Map.of(
                        "startTime", start.plusDays(1).toString(),
                        "endTime", end.plusDays(1).toString())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentCanEnrolAndJoinOnlyWhenAllowedAndMeetingLinkStaysProtected() throws Exception {
        String teacherEmail = uniqueEmail("join-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        Long lessonId = createPublishedLesson(teacherToken, "Join Lesson");

        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        LocalDateTime end = start.plusMinutes(DEFAULT_LIVE_CLASS_MINUTES);

        MvcResult createResult = mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/live-classes",
                teacherToken,
                Map.of(
                        "title", "Joinable Live Class",
                        "description", "Protected meeting link",
                        "lessonId", lessonId,
                        "startTime", start.toString(),
                        "endTime", end.toString(),
                        "meetingLink", "https://zoom.us/j/123456789",
                        "meetingPlatform", PLATFORM_ZOOM,
                        "status", STATUS_SCHEDULED)))
                .andExpect(status().isOk())
                .andReturn();

        Long liveClassId = extractDataId(createResult);
        String studentToken = registerStudentAndLogin(uniqueEmail("join-student"));
        String otherStudentToken = registerStudentAndLogin(uniqueEmail("join-other-student"));

        mockMvc.perform(authenticatedGet("/api/v1/students/live-classes/" + liveClassId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingLink").doesNotExist())
                .andExpect(jsonPath("$.data.canEnrol").value(true));

        mockMvc.perform(authenticatedGet("/api/v1/students/live-classes/" + liveClassId + "/join", studentToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(authenticatedPost("/api/v1/students/live-classes/" + liveClassId + "/enrol", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolled").value(true))
                .andExpect(jsonPath("$.data.canJoin").value(true));

        mockMvc.perform(oversizedPageGet("/api/v1/teachers/live-classes/" + liveClassId + "/enrolments", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(MAX_PAGE_SIZE))
                .andExpect(jsonPath("$.data.items[0].enrolmentStatus").value("ENROLLED"));

        mockMvc.perform(authenticatedPost("/api/v1/students/live-classes/" + liveClassId + "/enrol", studentToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(authenticatedGet("/api/v1/students/live-classes/" + liveClassId + "/join", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingLink").value("https://zoom.us/j/123456789"));

        mockMvc.perform(authenticatedGet("/api/v1/students/live-classes/" + liveClassId + "/join", otherStudentToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/students/live-classes/" + liveClassId + "/join"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentLiveClassListingHandlesPaidAndFreeClassesWithoutFailing() throws Exception {
        String teacherEmail = uniqueEmail("listing-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        Long freeLessonId = createPublishedLesson(teacherToken, "Free Listing Lesson");
        Long paidLessonId = createPublishedPaidLesson(teacherToken, "Paid Listing Lesson");
        String studentToken = registerStudentAndLogin(uniqueEmail("listing-student"));

        LocalDateTime freeStart = LocalDateTime.now().plusDays(4);
        LocalDateTime paidStart = LocalDateTime.now().plusDays(5);

        mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/live-classes",
                teacherToken,
                Map.of(
                        "title", "Free Listing Live Class",
                        "description", "Visible free live class",
                        "lessonId", freeLessonId,
                        "startTime", freeStart.toString(),
                        "endTime", freeStart.plusMinutes(DEFAULT_LIVE_CLASS_MINUTES).toString(),
                        "meetingLink", "https://meet.google.com/free-listing",
                        "meetingPlatform", PLATFORM_GOOGLE_MEET,
                        "status", STATUS_SCHEDULED)))
                .andExpect(status().isOk());

        mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/live-classes",
                teacherToken,
                Map.of(
                        "title", "Paid Listing Live Class",
                        "description", "Visible but locked paid live class",
                        "lessonId", paidLessonId,
                        "startTime", paidStart.toString(),
                        "endTime", paidStart.plusMinutes(DEFAULT_LIVE_CLASS_MINUTES).toString(),
                        "meetingLink", "https://zoom.us/j/paid-listing",
                        "meetingPlatform", PLATFORM_ZOOM,
                        "status", STATUS_SCHEDULED)))
                .andExpect(status().isOk());

        MvcResult listingResult = mockMvc.perform(oversizedPageGet("/api/v1/students/live-classes", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(MAX_PAGE_SIZE))
                .andReturn();

        JsonNode classes = responseItems(listingResult);
        JsonNode freeClass = findLiveClassByTitle(classes, "Free Listing Live Class");
        JsonNode paidClass = findLiveClassByTitle(classes, "Paid Listing Live Class");

        assertThat(freeClass.isMissingNode()).isFalse();
        assertThat(freeClass.path("accessAllowed").asBoolean()).isTrue();
        assertThat(freeClass.path("canEnrol").asBoolean()).isTrue();
        assertThat(freeClass.path("meetingLink").isNull()).isTrue();

        assertThat(paidClass.isMissingNode()).isFalse();
        assertThat(paidClass.path("accessAllowed").asBoolean()).isFalse();
        assertThat(paidClass.path("canEnrol").asBoolean()).isFalse();
        assertThat(paidClass.path("meetingLink").isNull()).isTrue();
    }

    @Test
    void studentLiveClassListingAndDetailHideTerminalClasses() throws Exception {
        String teacherEmail = uniqueEmail("terminal-live-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        String studentToken = registerStudentAndLogin(uniqueEmail("terminal-live-student"));
        Long lessonId = createPublishedLesson(teacherToken, "Terminal Live Lesson");

        LocalDateTime completedStart = LocalDateTime.now().plusDays(6);
        Long completedClassId = createLiveClass(teacherToken, lessonId, "Completed Hidden Live Class", completedStart);
        mockMvc.perform(authenticatedJsonPatch(
                "/api/v1/teachers/live-classes/" + completedClassId + "/status",
                teacherToken,
                Map.of("status", STATUS_COMPLETED)))
                .andExpect(status().isOk());

        LocalDateTime cancelledStart = LocalDateTime.now().plusDays(7);
        Long cancelledClassId = createLiveClass(teacherToken, lessonId, "Cancelled Hidden Live Class", cancelledStart);
        mockMvc.perform(authenticatedPatch(
                "/api/v1/teachers/live-classes/" + cancelledClassId + "/cancel",
                teacherToken))
                .andExpect(status().isOk());

        MvcResult listingResult = mockMvc.perform(oversizedPageGet("/api/v1/students/live-classes", studentToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode classes = responseItems(listingResult);
        assertThat(findLiveClassByTitle(classes, "Completed Hidden Live Class").isMissingNode()).isTrue();
        assertThat(findLiveClassByTitle(classes, "Cancelled Hidden Live Class").isMissingNode()).isTrue();

        mockMvc.perform(authenticatedGet("/api/v1/students/live-classes/" + completedClassId, studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(authenticatedGet("/api/v1/students/live-classes/" + cancelledClassId, studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCanAddRecordedVideoAndStudentCanTrackVideoProgress() throws Exception {
        String teacherEmail = uniqueEmail("recorded-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        Long lessonId = createPublishedLesson(teacherToken, "Recorded Lesson");
        String studentToken = registerStudentAndLogin(uniqueEmail("recorded-student"));

        MockMultipartFile videoFile = minimalMp4File();

        MvcResult recordedResult = mockMvc.perform(uploadedRecordedVideoMultipart(
                lessonId,
                teacherToken,
                videoFile,
                RECORDED_VIDEO_TITLE,
                RECORDED_VIDEO_DESCRIPTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.streamUrl").isString())
                .andReturn();

        Long videoId = extractDataId(recordedResult);

        mockMvc.perform(oversizedPageGet("/api/v1/teachers/lessons/" + lessonId + "/recorded-videos", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(MAX_PAGE_SIZE))
                .andExpect(jsonPath("$.data.items[0].title").value(RECORDED_VIDEO_TITLE));

        mockMvc.perform(oversizedPageGet("/api/v1/students/lessons/" + lessonId + "/recorded-videos", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(MAX_PAGE_SIZE))
                .andExpect(jsonPath("$.data.items[0].title").value(RECORDED_VIDEO_TITLE));

        mockMvc.perform(authenticatedJsonPost(
                "/api/v1/students/video-progress/" + videoId,
                studentToken,
                Map.of(
                        "watchedSeconds", 60,
                        "totalSeconds", DEFAULT_VIDEO_DURATION_SECONDS,
                        "progressPercentage", HALF_PROGRESS_PERCENTAGE,
                        "completed", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressPercentage").value(HALF_PROGRESS_PERCENTAGE))
                .andExpect(jsonPath("$.data.completed").value(false));

        mockMvc.perform(authenticatedPost("/api/v1/students/video-progress/" + videoId + "/complete", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressPercentage").value(COMPLETED_PROGRESS_PERCENTAGE))
                .andExpect(jsonPath("$.data.completed").value(true));

        mockMvc.perform(oversizedPageGet("/api/v1/teachers/lessons/" + lessonId + "/video-progress", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(MAX_PAGE_SIZE))
                .andExpect(jsonPath("$.data.items[0].progressPercentage").value(COMPLETED_PROGRESS_PERCENTAGE));
    }

    @Test
    void recordedVideoUploadShouldRejectSpoofedMp4Content() throws Exception {
        String teacherEmail = uniqueEmail("recorded-spoofed-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        Long lessonId = createPublishedLesson(teacherToken, "Recorded Lesson Spoofed");

        MockMultipartFile videoFile = new MockMultipartFile(
                FILE_PART,
                MP4_FILE_NAME,
                MP4_CONTENT_TYPE,
                "not-a-real-mp4".getBytes());

        mockMvc.perform(uploadedRecordedVideoMultipart(
                lessonId,
                teacherToken,
                videoFile,
                RECORDED_VIDEO_TITLE,
                RECORDED_VIDEO_DESCRIPTION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SPOOFED_VIDEO_MESSAGE));
    }

    @Test
    void recordedVideoUploadShouldRejectOversizedFilesWithPayloadTooLarge() throws Exception {
        String teacherEmail = uniqueEmail("recorded-large-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        Long lessonId = createPublishedLesson(teacherToken, "Recorded Lesson Too Large");

        MockMultipartFile videoFile = new MockMultipartFile(
                FILE_PART,
                MP4_FILE_NAME,
                MP4_CONTENT_TYPE,
                new byte[OVERSIZED_FILE_BYTES]);

        mockMvc.perform(uploadedRecordedVideoMultipart(
                lessonId,
                teacherToken,
                videoFile,
                RECORDED_VIDEO_TITLE,
                RECORDED_VIDEO_DESCRIPTION))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value(PAYLOAD_TOO_LARGE_MESSAGE));
    }

    @Test
    void recordedVideoMultipartParametersShouldRejectOversizedTextInputs() throws Exception {
        String teacherEmail = uniqueEmail("recorded-boundary-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        Long lessonId = createPublishedLesson(teacherToken, "Recorded Boundary Lesson");

        MockMultipartFile videoFile = minimalMp4File();

        mockMvc.perform(uploadedRecordedVideoMultipart(
                lessonId,
                teacherToken,
                videoFile,
                "x".repeat(OVERSIZED_TEXT_LENGTH),
                RECORDED_VIDEO_DESCRIPTION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void recordedVideoMultipartParametersShouldRejectInvalidNumericBoundsAndExternalUrlLength() throws Exception {
        String teacherEmail = uniqueEmail("recorded-invalid");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        Long lessonId = createPublishedLesson(teacherToken, "Recorded Invalid Parameter Lesson");

        MockMultipartFile videoFile = minimalMp4File();

        mockMvc.perform(recordedVideoMultipart(lessonId, teacherToken)
                .file(videoFile)
                .param("title", "Negative Duration")
                .param("sourceType", SOURCE_UPLOADED_FILE)
                .param("durationSeconds", "-1")
                .param("sortOrder", DEFAULT_SORT_ORDER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(recordedVideoMultipart(lessonId, teacherToken)
                .file(videoFile)
                .param("title", "Negative Sort")
                .param("sourceType", SOURCE_UPLOADED_FILE)
                .param("durationSeconds", DEFAULT_VIDEO_DURATION_SECONDS_PARAM)
                .param("sortOrder", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(recordedVideoMultipart(lessonId, teacherToken)
                .param("title", "External URL Too Long")
                .param("sourceType", SOURCE_EXTERNAL_URL)
                .param(
                        "externalVideoUrl",
                        "https://video.example.com/" + "x".repeat(OVERSIZED_EXTERNAL_URL_SUFFIX_LENGTH))
                .param("durationSeconds", DEFAULT_VIDEO_DURATION_SECONDS_PARAM)
                .param("sortOrder", DEFAULT_SORT_ORDER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void recordedVideoShouldRejectPackageThatDoesNotContainLesson() throws Exception {
        String teacherEmail = uniqueEmail("recorded-package");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        Long lessonId = createPublishedLesson(teacherToken, "Recorded Package Lesson");
        Long unrelatedPackageId = createPackage(teacherToken, "Unrelated Package", ACCESS_FREE, 0);

        mockMvc.perform(externalRecordedVideoMultipart(
                lessonId,
                teacherToken,
                "Wrong Package Video",
                "https://video.example.com/lesson.mp4")
                .param("packageId", String.valueOf(unrelatedPackageId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(SELECTED_PACKAGE_MISMATCH_MESSAGE));
    }

    @Test
    void recordedVideoShouldAllowPackageThatContainsLessonThroughModule() throws Exception {
        String teacherEmail = uniqueEmail("recorded-module-pkg");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        Long moduleId = createModule(teacherToken, "Recorded Module");
        Long lessonId = createPublishedLessonInModule(teacherToken, moduleId, "Recorded Module Lesson");
        Long packageId = createPackage(teacherToken, "Module Package", ACCESS_FREE, 0);
        addPackageItem(teacherToken, packageId, ITEM_TYPE_MODULE, moduleId);

        mockMvc.perform(externalRecordedVideoMultipart(
                lessonId,
                teacherToken,
                "Module Package Video",
                "https://video.example.com/module-lesson.mp4")
                .param("packageId", String.valueOf(packageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageId").value(packageId))
                .andExpect(jsonPath("$.data.externalVideoUrl").value("https://video.example.com/module-lesson.mp4"));
    }

    @Test
    void recordedVideoStreamKeepsInlineDisposition() throws Exception {
        String teacherEmail = uniqueEmail("recorded-inline-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        Long lessonId = createPublishedLesson(teacherToken, "Recorded Inline Lesson");

        MockMultipartFile videoFile = minimalMp4File();

        MvcResult recordedResult = mockMvc.perform(uploadedRecordedVideoMultipart(
                lessonId,
                teacherToken,
                videoFile,
                "Inline Lesson Recording",
                "Stream from the player"))
                .andExpect(status().isOk())
                .andReturn();

        Long videoId = extractDataId(recordedResult);

        mockMvc.perform(authenticatedGet(
                "/api/v1/teachers/lessons/" + lessonId + "/recorded-videos/" + videoId + "/stream",
                teacherToken))
                .andExpect(status().isOk())
                .andExpect(header()
                        .string("Content-Disposition", startsWith("inline;")))
                .andExpect(header()
                        .string("Cache-Control", "no-store"));
    }

    @Test
    void wrongRoleAndGuestCannotAccessProtectedPhaseTenEndpoints() throws Exception {
        String teacherEmail = uniqueEmail("phase10-protect-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = userToken(teacherEmail);
        String studentToken = registerStudentAndLogin(uniqueEmail("phase10-protect-student"));

        mockMvc.perform(authenticatedGet("/api/v1/teachers/live-classes", studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(authenticatedGet("/api/v1/admin/schedule", studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(authenticatedGet("/api/v1/students/schedule", teacherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/students/live-classes"))
                .andExpect(status().isUnauthorized());
    }

    private Long createPublishedLesson(String teacherToken, String title) throws Exception {
        MvcResult lessonResult = mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/lessons",
                teacherToken,
                Map.of(
                        "title", title,
                        "description", "Phase 10 lesson",
                        "levelId", BEGINNER_LEVEL_ID,
                        "categoryId", VOCABULARY_CATEGORY_ID,
                        "learningType", LEARNING_HYBRID,
                        "accessType", ACCESS_FREE,
                        "price", 0,
                        "estimatedDurationMinutes", DEFAULT_DURATION_MINUTES,
                        "basicContent", "Hybrid lesson content")))
                .andExpect(status().isOk())
                .andReturn();

        return extractDataId(lessonResult);
    }

    private Long createPublishedPaidLesson(String teacherToken, String title) throws Exception {
        MvcResult lessonResult = mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/lessons",
                teacherToken,
                Map.of(
                        "title", title,
                        "description", "Phase 10 paid lesson",
                        "levelId", BEGINNER_LEVEL_ID,
                        "categoryId", VOCABULARY_CATEGORY_ID,
                        "learningType", LEARNING_HYBRID,
                        "accessType", ACCESS_PAID,
                        "price", 19.90,
                        "estimatedDurationMinutes", DEFAULT_DURATION_MINUTES,
                        "basicContent", "Paid hybrid lesson content")))
                .andExpect(status().isOk())
                .andReturn();

        return extractDataId(lessonResult);
    }

    private Long createModule(String teacherToken, String title) throws Exception {
        MvcResult moduleResult = mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/modules",
                teacherToken,
                Map.of(
                        "title", title,
                        "description", "Phase 10 module",
                        "levelId", BEGINNER_LEVEL_ID,
                        "categoryId", VOCABULARY_CATEGORY_ID,
                        "accessType", ACCESS_FREE,
                        "price", 0)))
                .andExpect(status().isOk())
                .andReturn();

        return extractDataId(moduleResult);
    }

    private Long createPublishedLessonInModule(String teacherToken, Long moduleId, String title) throws Exception {
        MvcResult lessonResult = mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/lessons",
                teacherToken,
                Map.of(
                        "title", title,
                        "description", "Phase 10 lesson in module",
                        "moduleId", moduleId,
                        "levelId", BEGINNER_LEVEL_ID,
                        "categoryId", VOCABULARY_CATEGORY_ID,
                        "learningType", LEARNING_HYBRID,
                        "accessType", ACCESS_FREE,
                        "price", 0,
                        "estimatedDurationMinutes", DEFAULT_DURATION_MINUTES,
                        "basicContent", "Hybrid module lesson content")))
                .andExpect(status().isOk())
                .andReturn();

        return extractDataId(lessonResult);
    }

    private Long createPackage(String teacherToken, String title, String accessType, double price) throws Exception {
        MvcResult result = mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/packages",
                teacherToken,
                Map.of(
                        "title", title,
                        "description", "Phase 10 package",
                        "levelId", BEGINNER_LEVEL_ID,
                        "categoryId", VOCABULARY_CATEGORY_ID,
                        "learningType", LEARNING_RECORDED,
                        "accessType", accessType,
                        "price", price)))
                .andExpect(status().isOk())
                .andReturn();

        return extractDataId(result);
    }

    private void addPackageItem(String teacherToken, Long packageId, String itemType, Long itemId) throws Exception {
        mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/packages/" + packageId + "/items",
                teacherToken,
                Map.of(
                        "itemType", itemType,
                        "itemId", itemId,
                        "displayOrder", 1)))
                .andExpect(status().isOk());
    }

    private Long createLiveClass(String teacherToken, Long lessonId, String title, LocalDateTime start)
            throws Exception {
        MvcResult result = mockMvc.perform(authenticatedJsonPost(
                "/api/v1/teachers/live-classes",
                teacherToken,
                Map.of(
                        "title", title,
                        "description", "Terminal visibility coverage",
                        "lessonId", lessonId,
                        "startTime", start.toString(),
                        "endTime", start.plusMinutes(DEFAULT_LIVE_CLASS_MINUTES).toString(),
                        "meetingLink", "https://meet.google.com/" + UUID.randomUUID(),
                        "meetingPlatform", PLATFORM_GOOGLE_MEET,
                        "status", STATUS_SCHEDULED)))
                .andExpect(status().isOk())
                .andReturn();

        return extractDataId(result);
    }

    private void approveTeacherProfile(String teacherEmail) throws Exception {
        TeacherProfile profile = registerTeacherWithVerificationDocument(teacherEmail);
        String adminToken = adminToken();
        mockMvc.perform(authenticatedJsonPost(
                "/api/v1/admin/teachers/" + profile.getId() + "/approve",
                adminToken,
                Map.of("note", "Approved for live learning")))
                .andExpect(status().isOk());

        TeacherProfile refreshed = teacherProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(refreshed.getTeacherStatus()).isEqualTo(TeacherStatus.APPROVED);
    }

    private TeacherProfile registerTeacherWithVerificationDocument(String email) throws Exception {
        registerTeacher(email);
        String teacherToken = userToken(email);
        MockMultipartFile file = new MockMultipartFile(
                FILE_PART,
                PDF_FILE_NAME,
                PDF_CONTENT_TYPE,
                TestUploadFixtures.minimalPdf());

        mockMvc.perform(authenticatedMultipart("/api/v1/teachers/verification-documents", teacherToken)
                .file(file)
                .param("documentType", QUALIFICATION_DOCUMENT_TYPE)
                .param("description", "Qualification proof"))
                .andExpect(status().isOk());

        return teacherProfileRepository.findDetailedByUserId(
                userRepository.findByEmailWithRoles(email).orElseThrow().getId())
                .orElseThrow();
    }

    private void registerTeacher(String email) throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/register/teacher", Map.of(
                        "fullName", "Teacher " + email,
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "bio", "Teacher bio")))
                .andExpect(status().isOk());
    }

    private String registerStudentAndLogin(String email) throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/register/student", Map.of(
                        "fullName", "Student " + email,
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "preferredLanguage", "en")))
                .andExpect(status().isOk());

        return userToken(email);
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(jsonPost("/api/v1/auth/login", Map.of(
                        "email", email,
                        "password", password)))
                .andExpect(status().isOk())
                .andReturn();

        return responseData(result).path("token").asText();
    }

    private String adminToken() throws Exception {
        return loginAndExtractToken(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    private String userToken(String email) throws Exception {
        return loginAndExtractToken(email, USER_PASSWORD);
    }

    private Long extractDataId(MvcResult result) throws Exception {
        return responseData(result).path("id").asLong();
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode responseItems(MvcResult result) throws Exception {
        return responseData(result).path("items");
    }

    private MockHttpServletRequestBuilder authenticatedGet(String path, String token) {
        return get(path).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    private MockHttpServletRequestBuilder oversizedPageGet(String path, String token) {
        return authenticatedGet(path, token).param("size", String.valueOf(OVERSIZED_PAGE_SIZE));
    }

    private MockHttpServletRequestBuilder authenticatedPost(String path, String token) {
        return post(path)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    private MockHttpServletRequestBuilder authenticatedJsonPost(String path, String token, Map<String, ?> payload)
            throws Exception {
        return jsonPost(path, payload).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    private MockHttpServletRequestBuilder authenticatedJsonPut(String path, String token, Map<String, ?> payload)
            throws Exception {
        return jsonPut(path, payload).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    private MockHttpServletRequestBuilder authenticatedJsonPatch(String path, String token, Map<String, ?> payload)
            throws Exception {
        return jsonPatch(path, payload).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    private MockHttpServletRequestBuilder authenticatedPatch(String path, String token) {
        return patch(path)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    private MockMultipartHttpServletRequestBuilder authenticatedMultipart(String path, String token) {
        MockMultipartHttpServletRequestBuilder builder = multipart(path);
        builder.with(csrf());
        builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
        return builder;
    }

    private MockMultipartHttpServletRequestBuilder recordedVideoMultipart(Long lessonId, String token) {
        return authenticatedMultipart("/api/v1/teachers/lessons/" + lessonId + "/recorded-videos", token);
    }

    private MockMultipartHttpServletRequestBuilder uploadedRecordedVideoMultipart(
            Long lessonId,
            String token,
            MockMultipartFile file,
            String title,
            String description) {
        MockMultipartHttpServletRequestBuilder builder = recordedVideoMultipart(lessonId, token);
        builder.file(file);
        builder.param("title", title);
        builder.param("description", description);
        builder.param("sourceType", SOURCE_UPLOADED_FILE);
        builder.param("durationSeconds", DEFAULT_VIDEO_DURATION_SECONDS_PARAM);
        builder.param("sortOrder", DEFAULT_SORT_ORDER);
        return builder;
    }

    private MockMultipartHttpServletRequestBuilder externalRecordedVideoMultipart(
            Long lessonId,
            String token,
            String title,
            String externalVideoUrl) {
        MockMultipartHttpServletRequestBuilder builder = recordedVideoMultipart(lessonId, token);
        builder.param("title", title);
        builder.param("sourceType", SOURCE_EXTERNAL_URL);
        builder.param("externalVideoUrl", externalVideoUrl);
        builder.param("durationSeconds", DEFAULT_VIDEO_DURATION_SECONDS_PARAM);
        builder.param("sortOrder", DEFAULT_SORT_ORDER);
        return builder;
    }

    private static MockMultipartFile minimalMp4File() {
        return new MockMultipartFile(
                FILE_PART,
                MP4_FILE_NAME,
                MP4_CONTENT_TYPE,
                TestUploadFixtures.minimalMp4());
    }

    private MockHttpServletRequestBuilder jsonPost(String path, Map<String, ?> payload) throws Exception {
        return post(path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(payload));
    }

    private MockHttpServletRequestBuilder jsonPut(String path, Map<String, ?> payload) throws Exception {
        return put(path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(payload));
    }

    private MockHttpServletRequestBuilder jsonPatch(String path, Map<String, ?> payload) throws Exception {
        return patch(path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(payload));
    }

    private String json(Map<String, ?> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode findLiveClassByTitle(JsonNode classes, String title) {
        for (JsonNode item : classes) {
            if (title.equals(item.path("title").asText())) {
                return item;
            }
        }
        return MissingNode.getInstance();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
