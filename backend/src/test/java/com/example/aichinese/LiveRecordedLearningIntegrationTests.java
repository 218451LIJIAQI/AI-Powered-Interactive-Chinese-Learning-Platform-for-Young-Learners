package com.example.aichinese;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        Long lessonId = createPublishedLesson(teacherToken, adminToken, "Live Lesson");

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
    }

    @Test
    void studentCanEnrolAndJoinOnlyWhenAllowedAndMeetingLinkStaysProtected() throws Exception {
        String teacherEmail = uniqueEmail("join-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPublishedLesson(teacherToken, adminToken, "Join Lesson");

        LocalDateTime start = LocalDateTime.now().plusDays(3);
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
                .andExpect(jsonPath("$.data.enrolled").value(true));

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
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long freeLessonId = createPublishedLesson(teacherToken, adminToken, "Free Listing Lesson");
        Long paidLessonId = createPublishedPaidLesson(teacherToken, adminToken, "Paid Listing Lesson");
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
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode classes = objectMapper.readTree(listingResult.getResponse().getContentAsString()).path("data");
        JsonNode freeClass = findLiveClassByTitle(classes, "Free Listing Live Class");
        JsonNode paidClass = findLiveClassByTitle(classes, "Paid Listing Live Class");

        org.assertj.core.api.Assertions.assertThat(freeClass.isMissingNode()).isFalse();
        org.assertj.core.api.Assertions.assertThat(freeClass.path("accessAllowed").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(freeClass.path("canEnrol").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(freeClass.path("meetingLink").isNull()).isTrue();

        org.assertj.core.api.Assertions.assertThat(paidClass.isMissingNode()).isFalse();
        org.assertj.core.api.Assertions.assertThat(paidClass.path("accessAllowed").asBoolean()).isFalse();
        org.assertj.core.api.Assertions.assertThat(paidClass.path("canEnrol").asBoolean()).isFalse();
        org.assertj.core.api.Assertions.assertThat(paidClass.path("meetingLink").isNull()).isTrue();
    }

    @Test
    void teacherCanAddRecordedVideoAndStudentCanTrackVideoProgress() throws Exception {
        String teacherEmail = uniqueEmail("recorded-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        Long lessonId = createPublishedLesson(teacherToken, adminToken, "Recorded Lesson");
        String studentToken = registerStudentAndLogin(uniqueEmail("recorded-student"));

        MockMultipartFile videoFile = new MockMultipartFile(
                "file",
                "lesson.mp4",
                "video/mp4",
                "fake-video".getBytes());

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

        mockMvc.perform(get("/api/v1/students/lessons/" + lessonId + "/recorded-videos")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Lesson Recording"));

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
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].progressPercentage").value(100));
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

    private Long createPublishedLesson(String teacherToken, String adminToken, String title) throws Exception {
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

        Long lessonId = extractDataId(lessonResult);

        mockMvc.perform(post("/api/v1/teachers/lessons/" + lessonId + "/submit")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/content/LESSON/" + lessonId + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Approved"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/content/LESSON/" + lessonId + "/publish")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Published"))))
                .andExpect(status().isOk());

        return lessonId;
    }

    private Long createPublishedPaidLesson(String teacherToken, String adminToken, String title) throws Exception {
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

        Long lessonId = extractDataId(lessonResult);

        mockMvc.perform(post("/api/v1/teachers/lessons/" + lessonId + "/submit")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/content/LESSON/" + lessonId + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Approved"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/content/LESSON/" + lessonId + "/publish")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Published"))))
                .andExpect(status().isOk());

        return lessonId;
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
        org.assertj.core.api.Assertions.assertThat(refreshed.getTeacherStatus()).isEqualTo(TeacherStatus.APPROVED);
    }

    private TeacherProfile registerTeacherWithVerificationDocument(String email) throws Exception {
        registerTeacher(email);
        String teacherToken = loginAndExtractToken(email, "Password123!");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "qualification.pdf",
                "application/pdf",
                "verification".getBytes());

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
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
