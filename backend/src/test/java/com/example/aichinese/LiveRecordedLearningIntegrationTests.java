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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.aichinese.support.TestUploadFixtures;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
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
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPublishedLesson(teacherToken, "Live Lesson");

        LocalDateTime start = LocalDateTime.now().plusDays(2);
        LocalDateTime end = start.plusHours(1);

        MvcResult createResult = mockMvc.perform(post("/api/v1/teachers/live-classes")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Weekend Live Class",
                        "description", "Practice speaking together",
                        "lessonId", lessonId,
                        "startTime", start.toString(),
                        "endTime", end.toString(),
                        "meetingLink", "https://meet.google.com/test-room",
                        "meetingPlatform", "GOOGLE_MEET",
                        "status", "SCHEDULED",
                        "maxParticipants", 10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingLinkVisible").value(true))
                .andReturn();

        Long liveClassId = extractDataId(createResult);

        mockMvc.perform(put("/api/v1/teachers/live-classes/" + liveClassId)
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Weekend Live Class Updated",
                        "description", "Practice speaking together",
                        "lessonId", lessonId,
                        "startTime", start.plusHours(1).toString(),
                        "endTime", end.plusHours(1).toString(),
                        "meetingLink", "https://meet.google.com/test-room-updated",
                        "meetingPlatform", "GOOGLE_MEET",
                        "status", "SCHEDULED",
                        "maxParticipants", 12))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Weekend Live Class Updated"));

        mockMvc.perform(patch("/api/v1/teachers/live-classes/" + liveClassId + "/reschedule")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "startTime", start.plusDays(1).toString(),
                        "endTime", end.plusDays(1).toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liveClassStatus").value("RESCHEDULED"));

        mockMvc.perform(get("/api/v1/admin/live-classes/" + liveClassId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingLinkVisible").value(true));

        mockMvc.perform(get("/api/v1/teachers/live-classes")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items").isArray());

        mockMvc.perform(get("/api/v1/admin/live-classes")
                .param("size", "100000")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void liveClassStatusLifecycleRejectsInvalidAndTerminalTransitions() throws Exception {
        String teacherEmail = uniqueEmail("lifecycle-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createPublishedLesson(teacherToken, "Lifecycle Lesson");

        LocalDateTime start = LocalDateTime.now().plusDays(2);
        LocalDateTime end = start.plusHours(1);

        mockMvc.perform(post("/api/v1/teachers/live-classes")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Invalid Initial Status",
                        "description", "Cannot start created as ongoing",
                        "lessonId", lessonId,
                        "startTime", start.toString(),
                        "endTime", end.toString(),
                        "meetingLink", "https://meet.google.com/invalid-initial",
                        "meetingPlatform", "GOOGLE_MEET",
                        "status", "ONGOING"))))
                .andExpect(status().isBadRequest());

        MvcResult createResult = mockMvc.perform(post("/api/v1/teachers/live-classes")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Lifecycle Live Class",
                        "description", "State machine coverage",
                        "lessonId", lessonId,
                        "startTime", start.toString(),
                        "endTime", end.toString(),
                        "meetingLink", "https://meet.google.com/lifecycle",
                        "meetingPlatform", "GOOGLE_MEET",
                        "status", "SCHEDULED"))))
                .andExpect(status().isOk())
                .andReturn();

        Long liveClassId = extractDataId(createResult);

        mockMvc.perform(patch("/api/v1/teachers/live-classes/" + liveClassId + "/status")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "ONGOING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liveClassStatus").value("ONGOING"));

        mockMvc.perform(patch("/api/v1/teachers/live-classes/" + liveClassId + "/status")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "SCHEDULED"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/teachers/live-classes/" + liveClassId + "/status")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liveClassStatus").value("COMPLETED"));

        mockMvc.perform(patch("/api/v1/teachers/live-classes/" + liveClassId + "/cancel")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/teachers/live-classes/" + liveClassId + "/reschedule")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "startTime", start.plusDays(1).toString(),
                        "endTime", end.plusDays(1).toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentCanEnrolAndJoinOnlyWhenAllowedAndMeetingLinkStaysProtected() throws Exception {
        String teacherEmail = uniqueEmail("join-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createPublishedLesson(teacherToken, "Join Lesson");

        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        LocalDateTime end = start.plusMinutes(45);

        MvcResult createResult = mockMvc.perform(post("/api/v1/teachers/live-classes")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Joinable Live Class",
                        "description", "Protected meeting link",
                        "lessonId", lessonId,
                        "startTime", start.toString(),
                        "endTime", end.toString(),
                        "meetingLink", "https://zoom.us/j/123456789",
                        "meetingPlatform", "ZOOM",
                        "status", "SCHEDULED"))))
                .andExpect(status().isOk())
                .andReturn();

        Long liveClassId = extractDataId(createResult);
        String studentToken = registerStudentAndLogin(uniqueEmail("join-student"));
        String otherStudentToken = registerStudentAndLogin(uniqueEmail("join-other-student"));

        mockMvc.perform(get("/api/v1/students/live-classes/" + liveClassId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingLink").doesNotExist())
                .andExpect(jsonPath("$.data.canEnrol").value(true));

        mockMvc.perform(get("/api/v1/students/live-classes/" + liveClassId + "/join")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/students/live-classes/" + liveClassId + "/enrol")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolled").value(true))
                .andExpect(jsonPath("$.data.canJoin").value(true));

        mockMvc.perform(get("/api/v1/teachers/live-classes/" + liveClassId + "/enrolments")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items[0].enrolmentStatus").value("ENROLLED"));

        mockMvc.perform(post("/api/v1/students/live-classes/" + liveClassId + "/enrol")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/students/live-classes/" + liveClassId + "/join")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingLink").value("https://zoom.us/j/123456789"));

        mockMvc.perform(get("/api/v1/students/live-classes/" + liveClassId + "/join")
                .header("Authorization", "Bearer " + otherStudentToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/students/live-classes/" + liveClassId + "/join"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentLiveClassListingHandlesPaidAndFreeClassesWithoutFailing() throws Exception {
        String teacherEmail = uniqueEmail("listing-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long freeLessonId = createPublishedLesson(teacherToken, "Free Listing Lesson");
        Long paidLessonId = createPublishedPaidLesson(teacherToken, "Paid Listing Lesson");
        String studentToken = registerStudentAndLogin(uniqueEmail("listing-student"));

        LocalDateTime freeStart = LocalDateTime.now().plusDays(4);
        LocalDateTime paidStart = LocalDateTime.now().plusDays(5);

        mockMvc.perform(post("/api/v1/teachers/live-classes")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Free Listing Live Class",
                        "description", "Visible free live class",
                        "lessonId", freeLessonId,
                        "startTime", freeStart.toString(),
                        "endTime", freeStart.plusMinutes(45).toString(),
                        "meetingLink", "https://meet.google.com/free-listing",
                        "meetingPlatform", "GOOGLE_MEET",
                        "status", "SCHEDULED"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/teachers/live-classes")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Paid Listing Live Class",
                        "description", "Visible but locked paid live class",
                        "lessonId", paidLessonId,
                        "startTime", paidStart.toString(),
                        "endTime", paidStart.plusMinutes(45).toString(),
                        "meetingLink", "https://zoom.us/j/paid-listing",
                        "meetingPlatform", "ZOOM",
                        "status", "SCHEDULED"))))
                .andExpect(status().isOk());

        MvcResult listingResult = mockMvc.perform(get("/api/v1/students/live-classes")
                .param("size", "100000")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andReturn();

        JsonNode classes = objectMapper.readTree(listingResult.getResponse().getContentAsString()).path("data").path("items");
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
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String studentToken = registerStudentAndLogin(uniqueEmail("terminal-live-student"));
        Long lessonId = createPublishedLesson(teacherToken, "Terminal Live Lesson");

        LocalDateTime completedStart = LocalDateTime.now().plusDays(6);
        Long completedClassId = createLiveClass(teacherToken, lessonId, "Completed Hidden Live Class", completedStart);
        mockMvc.perform(patch("/api/v1/teachers/live-classes/" + completedClassId + "/status")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED"))))
                .andExpect(status().isOk());

        LocalDateTime cancelledStart = LocalDateTime.now().plusDays(7);
        Long cancelledClassId = createLiveClass(teacherToken, lessonId, "Cancelled Hidden Live Class", cancelledStart);
        mockMvc.perform(patch("/api/v1/teachers/live-classes/" + cancelledClassId + "/cancel")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        MvcResult listingResult = mockMvc.perform(get("/api/v1/students/live-classes")
                .param("size", "100000")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode classes = objectMapper.readTree(listingResult.getResponse().getContentAsString()).path("data").path("items");
        assertThat(findLiveClassByTitle(classes, "Completed Hidden Live Class").isMissingNode()).isTrue();
        assertThat(findLiveClassByTitle(classes, "Cancelled Hidden Live Class").isMissingNode()).isTrue();

        mockMvc.perform(get("/api/v1/students/live-classes/" + completedClassId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/students/live-classes/" + cancelledClassId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCanAddRecordedVideoAndStudentCanTrackVideoProgress() throws Exception {
        String teacherEmail = uniqueEmail("recorded-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createPublishedLesson(teacherToken, "Recorded Lesson");
        String studentToken = registerStudentAndLogin(uniqueEmail("recorded-student"));

        MockMultipartFile videoFile = new MockMultipartFile(
                "file",
                "lesson.mp4",
                "video/mp4",
                TestUploadFixtures.minimalMp4());

        MvcResult recordedResult = mockMvc.perform(multipart("/api/v1/teachers/lessons/" + lessonId + "/recorded-videos")
                .file(videoFile)
                .param("title", "Lesson Recording")
                .param("description", "Watch again at home")
                .param("sourceType", "UPLOADED_FILE")
                .param("durationSeconds", "120")
                .param("sortOrder", "1")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.streamUrl").isString())
                .andReturn();

        Long videoId = extractDataId(recordedResult);

        mockMvc.perform(get("/api/v1/teachers/lessons/" + lessonId + "/recorded-videos")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items[0].title").value("Lesson Recording"));

        mockMvc.perform(get("/api/v1/students/lessons/" + lessonId + "/recorded-videos")
                .param("size", "100000")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items[0].title").value("Lesson Recording"));

        mockMvc.perform(post("/api/v1/students/video-progress/" + videoId)
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "watchedSeconds", 60,
                        "totalSeconds", 120,
                        "progressPercentage", 50,
                        "completed", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressPercentage").value(50))
                .andExpect(jsonPath("$.data.completed").value(false));

        mockMvc.perform(post("/api/v1/students/video-progress/" + videoId + "/complete")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressPercentage").value(100))
                .andExpect(jsonPath("$.data.completed").value(true));

        mockMvc.perform(get("/api/v1/teachers/lessons/" + lessonId + "/video-progress")
                .param("size", "100000")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.items[0].progressPercentage").value(100));
    }

    @Test
    void recordedVideoUploadShouldRejectSpoofedMp4Content() throws Exception {
        String teacherEmail = uniqueEmail("recorded-spoofed-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createPublishedLesson(teacherToken, "Recorded Lesson Spoofed");

        MockMultipartFile videoFile = new MockMultipartFile(
                "file",
                "lesson.mp4",
                "video/mp4",
                "not-a-real-mp4".getBytes());

        mockMvc.perform(multipart("/api/v1/teachers/lessons/" + lessonId + "/recorded-videos")
                .file(videoFile)
                .param("title", "Lesson Recording")
                .param("description", "Watch again at home")
                .param("sourceType", "UPLOADED_FILE")
                .param("durationSeconds", "120")
                .param("sortOrder", "1")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Uploaded video content does not match the declared MP4/WEBM/MOV type."));
    }

    @Test
    void recordedVideoUploadShouldRejectOversizedFilesWithPayloadTooLarge() throws Exception {
        String teacherEmail = uniqueEmail("recorded-large-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createPublishedLesson(teacherToken, "Recorded Lesson Too Large");

        MockMultipartFile videoFile = new MockMultipartFile(
                "file",
                "lesson.mp4",
                "video/mp4",
                new byte[2048]);

        mockMvc.perform(multipart("/api/v1/teachers/lessons/" + lessonId + "/recorded-videos")
                .file(videoFile)
                .param("title", "Lesson Recording")
                .param("description", "Watch again at home")
                .param("sourceType", "UPLOADED_FILE")
                .param("durationSeconds", "120")
                .param("sortOrder", "1")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("The file is too large. Please choose a smaller file and try again."));
    }

    @Test
    void recordedVideoMultipartParametersShouldRejectOversizedTextInputs() throws Exception {
        String teacherEmail = uniqueEmail("recorded-boundary-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createPublishedLesson(teacherToken, "Recorded Boundary Lesson");

        MockMultipartFile videoFile = new MockMultipartFile(
                "file",
                "lesson.mp4",
                "video/mp4",
                TestUploadFixtures.minimalMp4());

        mockMvc.perform(multipart("/api/v1/teachers/lessons/" + lessonId + "/recorded-videos")
                .file(videoFile)
                .param("title", "x".repeat(201))
                .param("description", "Watch again at home")
                .param("sourceType", "UPLOADED_FILE")
                .param("durationSeconds", "120")
                .param("sortOrder", "1")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void recordedVideoMultipartParametersShouldRejectInvalidNumericBoundsAndExternalUrlLength() throws Exception {
        String teacherEmail = uniqueEmail("recorded-invalid");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createPublishedLesson(teacherToken, "Recorded Invalid Parameter Lesson");

        MockMultipartFile videoFile = new MockMultipartFile(
                "file",
                "lesson.mp4",
                "video/mp4",
                TestUploadFixtures.minimalMp4());

        mockMvc.perform(multipart("/api/v1/teachers/lessons/" + lessonId + "/recorded-videos")
                .file(videoFile)
                .param("title", "Negative Duration")
                .param("sourceType", "UPLOADED_FILE")
                .param("durationSeconds", "-1")
                .param("sortOrder", "1")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(multipart("/api/v1/teachers/lessons/" + lessonId + "/recorded-videos")
                .file(videoFile)
                .param("title", "Negative Sort")
                .param("sourceType", "UPLOADED_FILE")
                .param("durationSeconds", "120")
                .param("sortOrder", "-1")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(multipart("/api/v1/teachers/lessons/" + lessonId + "/recorded-videos")
                .param("title", "External URL Too Long")
                .param("sourceType", "EXTERNAL_URL")
                .param("externalVideoUrl", "https://video.example.com/" + "x".repeat(501))
                .param("durationSeconds", "120")
                .param("sortOrder", "1")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void recordedVideoShouldRejectPackageThatDoesNotContainLesson() throws Exception {
        String teacherEmail = uniqueEmail("recorded-package");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createPublishedLesson(teacherToken, "Recorded Package Lesson");
        Long unrelatedPackageId = createPackage(teacherToken, "Unrelated Package", "FREE", 0);

        mockMvc.perform(multipart("/api/v1/teachers/lessons/" + lessonId + "/recorded-videos")
                .param("title", "Wrong Package Video")
                .param("sourceType", "EXTERNAL_URL")
                .param("externalVideoUrl", "https://video.example.com/lesson.mp4")
                .param("packageId", String.valueOf(unrelatedPackageId))
                .param("durationSeconds", "120")
                .param("sortOrder", "1")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Selected package must contain the requested lesson."));
    }

    @Test
    void recordedVideoShouldAllowPackageThatContainsLessonThroughModule() throws Exception {
        String teacherEmail = uniqueEmail("recorded-module-pkg");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long moduleId = createModule(teacherToken, "Recorded Module");
        Long lessonId = createPublishedLessonInModule(teacherToken, moduleId, "Recorded Module Lesson");
        Long packageId = createPackage(teacherToken, "Module Package", "FREE", 0);
        addPackageItem(teacherToken, packageId, "MODULE", moduleId);

        mockMvc.perform(multipart("/api/v1/teachers/lessons/" + lessonId + "/recorded-videos")
                .param("title", "Module Package Video")
                .param("sourceType", "EXTERNAL_URL")
                .param("externalVideoUrl", "https://video.example.com/module-lesson.mp4")
                .param("packageId", String.valueOf(packageId))
                .param("durationSeconds", "120")
                .param("sortOrder", "1")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageId").value(packageId))
                .andExpect(jsonPath("$.data.externalVideoUrl").value("https://video.example.com/module-lesson.mp4"));
    }

    @Test
    void recordedVideoStreamKeepsInlineDisposition() throws Exception {
        String teacherEmail = uniqueEmail("recorded-inline-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createPublishedLesson(teacherToken, "Recorded Inline Lesson");

        MockMultipartFile videoFile = new MockMultipartFile(
                "file",
                "lesson.mp4",
                "video/mp4",
                TestUploadFixtures.minimalMp4());

        MvcResult recordedResult = mockMvc.perform(multipart("/api/v1/teachers/lessons/" + lessonId + "/recorded-videos")
                .file(videoFile)
                .param("title", "Inline Lesson Recording")
                .param("description", "Stream from the player")
                .param("sourceType", "UPLOADED_FILE")
                .param("durationSeconds", "120")
                .param("sortOrder", "1")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andReturn();

        Long videoId = extractDataId(recordedResult);

        mockMvc.perform(get("/api/v1/teachers/lessons/" + lessonId + "/recorded-videos/" + videoId + "/stream")
                .header("Authorization", "Bearer " + teacherToken))
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
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String studentToken = registerStudentAndLogin(uniqueEmail("phase10-protect-student"));

        mockMvc.perform(get("/api/v1/teachers/live-classes")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/schedule")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/students/schedule")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/students/live-classes"))
                .andExpect(status().isUnauthorized());
    }

    private Long createPublishedLesson(String teacherToken, String title) throws Exception {
        MvcResult lessonResult = mockMvc.perform(post("/api/v1/teachers/lessons")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "description", "Phase 10 lesson",
                        "levelId", 1,
                        "categoryId", 3,
                        "learningType", "HYBRID",
                        "accessType", "FREE",
                        "price", 0,
                        "estimatedDurationMinutes", 15,
                        "basicContent", "Hybrid lesson content"))))
                .andExpect(status().isOk())
                .andReturn();

        return extractDataId(lessonResult);
    }

    private Long createPublishedPaidLesson(String teacherToken, String title) throws Exception {
        MvcResult lessonResult = mockMvc.perform(post("/api/v1/teachers/lessons")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "description", "Phase 10 paid lesson",
                        "levelId", 1,
                        "categoryId", 3,
                        "learningType", "HYBRID",
                        "accessType", "PAID",
                        "price", 19.90,
                        "estimatedDurationMinutes", 15,
                        "basicContent", "Paid hybrid lesson content"))))
                .andExpect(status().isOk())
                .andReturn();

        return extractDataId(lessonResult);
    }

    private Long createModule(String teacherToken, String title) throws Exception {
        MvcResult moduleResult = mockMvc.perform(post("/api/v1/teachers/modules")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "description", "Phase 10 module",
                        "levelId", 1,
                        "categoryId", 3,
                        "accessType", "FREE",
                        "price", 0))))
                .andExpect(status().isOk())
                .andReturn();

        return extractDataId(moduleResult);
    }

    private Long createPublishedLessonInModule(String teacherToken, Long moduleId, String title) throws Exception {
        MvcResult lessonResult = mockMvc.perform(post("/api/v1/teachers/lessons")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "description", "Phase 10 lesson in module",
                        "moduleId", moduleId,
                        "levelId", 1,
                        "categoryId", 3,
                        "learningType", "HYBRID",
                        "accessType", "FREE",
                        "price", 0,
                        "estimatedDurationMinutes", 15,
                        "basicContent", "Hybrid module lesson content"))))
                .andExpect(status().isOk())
                .andReturn();

        return extractDataId(lessonResult);
    }

    private Long createPackage(String teacherToken, String title, String accessType, double price) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teachers/packages")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "description", "Phase 10 package",
                        "levelId", 1,
                        "categoryId", 3,
                        "learningType", "RECORDED",
                        "accessType", accessType,
                        "price", price))))
                .andExpect(status().isOk())
                .andReturn();

        return extractDataId(result);
    }

    private void addPackageItem(String teacherToken, Long packageId, String itemType, Long itemId) throws Exception {
        mockMvc.perform(post("/api/v1/teachers/packages/" + packageId + "/items")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", itemType,
                        "itemId", itemId,
                        "displayOrder", 1))))
                .andExpect(status().isOk());
    }

    private Long createLiveClass(String teacherToken, Long lessonId, String title, LocalDateTime start) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teachers/live-classes")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "description", "Terminal visibility coverage",
                        "lessonId", lessonId,
                        "startTime", start.toString(),
                        "endTime", start.plusMinutes(45).toString(),
                        "meetingLink", "https://meet.google.com/" + UUID.randomUUID(),
                        "meetingPlatform", "GOOGLE_MEET",
                        "status", "SCHEDULED"))))
                .andExpect(status().isOk())
                .andReturn();

        return extractDataId(result);
    }

    private void approveTeacherProfile(String teacherEmail) throws Exception {
        TeacherProfile profile = registerTeacherWithVerificationDocument(teacherEmail);
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        mockMvc.perform(post("/api/v1/admin/teachers/" + profile.getId() + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Approved for live learning"))))
                .andExpect(status().isOk());

        TeacherProfile refreshed = teacherProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(refreshed.getTeacherStatus()).isEqualTo(TeacherStatus.APPROVED);
    }

    private TeacherProfile registerTeacherWithVerificationDocument(String email) throws Exception {
        registerTeacher(email);
        String teacherToken = loginAndExtractToken(email, "Password123!");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "qualification.pdf",
                "application/pdf",
                TestUploadFixtures.minimalPdf());

        mockMvc.perform(multipart("/api/v1/teachers/verification-documents")
                .file(file)
                .param("documentType", "QUALIFICATION")
                .param("description", "Qualification proof")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        return teacherProfileRepository.findDetailedByUserId(userRepository.findByEmailWithRoles(email).orElseThrow().getId())
                .orElseThrow();
    }

    private void registerTeacher(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/teacher")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Teacher " + email,
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!",
                        "bio", "Teacher bio"))))
                .andExpect(status().isOk());
    }

    private String registerStudentAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/student")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Student " + email,
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!",
                        "preferredLanguage", "en"))))
                .andExpect(status().isOk());

        return loginAndExtractToken(email, "Password123!");
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", password))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private Long extractDataId(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("id").asLong();
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
