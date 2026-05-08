package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.repository.LessonRepository;
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
class ContentManagementIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Autowired
    private LearningModuleRepository learningModuleRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private CoursePackageRepository coursePackageRepository;

    @Test
    void publicShouldListLevelsAndCategories() throws Exception {
        mockMvc.perform(get("/api/v1/public/levels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").isString());

        mockMvc.perform(get("/api/v1/public/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").isString());
    }

    @Test
    void approvedTeacherShouldCreateAndSubmitModule() throws Exception {
        String teacherEmail = uniqueEmail("approved-module");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");

        MvcResult createResult = mockMvc.perform(post("/api/v1/teachers/modules")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Beginner Sounds",
                        "description", "Pinyin module for young learners",
                        "levelId", beginnerLevelId(),
                        "categoryId", categoryId("Pinyin"),
                        "accessType", "FREE",
                        "price", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("DRAFT"))
                .andReturn();

        Long moduleId = extractId(createResult);

        mockMvc.perform(post("/api/v1/teachers/modules/" + moduleId + "/submit")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("SUBMITTED"));

        LearningModule module = learningModuleRepository.findById(moduleId).orElseThrow();
        assertThat(module.getContentStatus()).isEqualTo(ContentStatus.SUBMITTED);
    }

    @Test
    void pendingTeacherAndRejectedTeacherShouldNotCreateModule() throws Exception {
        String pendingEmail = uniqueEmail("pending-module");
        registerTeacher(pendingEmail);
        String pendingToken = loginAndExtractToken(pendingEmail, "Password123!");

        mockMvc.perform(post("/api/v1/teachers/modules")
                .with(csrf())
                .header("Authorization", "Bearer " + pendingToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(modulePayload("Pending blocked module")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only approved teachers can create or submit learning content."));

        String rejectedEmail = uniqueEmail("rejected-module");
        TeacherProfile rejectedProfile = registerTeacherWithVerificationDocument(rejectedEmail);
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        mockMvc.perform(post("/api/v1/admin/teachers/" + rejectedProfile.getId() + "/reject")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Verification incomplete"))))
                .andExpect(status().isOk());

        String rejectedToken = loginAndExtractToken(rejectedEmail, "Password123!");
        mockMvc.perform(post("/api/v1/teachers/modules")
                .with(csrf())
                .header("Authorization", "Bearer " + rejectedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(modulePayload("Rejected blocked module")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only approved teachers can create or submit learning content."));
    }

    @Test
    void teacherCannotManageAnotherTeachersModule() throws Exception {
        String firstTeacherEmail = uniqueEmail("owner-module");
        approveTeacherProfile(firstTeacherEmail);
        String firstTeacherToken = loginAndExtractToken(firstTeacherEmail, "Password123!");
        Long moduleId = createModule(firstTeacherToken, "Owner module");

        String secondTeacherEmail = uniqueEmail("other-module");
        approveTeacherProfile(secondTeacherEmail);
        String secondTeacherToken = loginAndExtractToken(secondTeacherEmail, "Password123!");

        mockMvc.perform(get("/api/v1/teachers/modules/" + moduleId)
                .header("Authorization", "Bearer " + secondTeacherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void teacherShouldCreateLessonSubmitPackageAndManageActivities() throws Exception {
        String teacherEmail = uniqueEmail("lesson-package");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");

        Long moduleId = createModule(teacherToken, "Numbers module");

        MvcResult lessonResult = mockMvc.perform(post("/api/v1/teachers/lessons")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Count from one to ten",
                        "description", "Simple counting lesson",
                        "moduleId", moduleId,
                        "levelId", beginnerLevelId(),
                        "categoryId", categoryId("Numbers"),
                        "learningType", "RECORDED",
                        "accessType", "FREE",
                        "price", 0,
                        "estimatedDurationMinutes", 12,
                        "basicContent", "Learn numbers in Mandarin."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("DRAFT"))
                .andReturn();

        Long lessonId = extractId(lessonResult);

        mockMvc.perform(post("/api/v1/teachers/lessons/" + lessonId + "/activities")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Flashcard starter",
                        "activityType", "FLASHCARD",
                        "instruction", "Review number words",
                        "description", "Placeholder flashcard activity",
                        "displayOrder", 1,
                        "placeholderContent", "{\"cards\":0}"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityType").value("FLASHCARD"));

        mockMvc.perform(post("/api/v1/teachers/lessons/" + lessonId + "/submit")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("SUBMITTED"));

        MvcResult packageResult = mockMvc.perform(post("/api/v1/teachers/packages")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Number Starter Pack",
                        "description", "Package for number basics",
                        "levelId", beginnerLevelId(),
                        "categoryId", categoryId("Numbers"),
                        "learningType", "RECORDED",
                        "accessType", "PAID",
                        "price", 19.90))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessType").value("PAID"))
                .andReturn();

        Long packageId = extractId(packageResult);

        mockMvc.perform(post("/api/v1/teachers/packages/" + packageId + "/items")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "itemType", "LESSON",
                        "itemId", lessonId,
                        "displayOrder", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageItems[0].itemType").value("LESSON"));

        mockMvc.perform(post("/api/v1/teachers/packages/" + packageId + "/submit")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("SUBMITTED"));

        Lesson lesson = lessonRepository.findById(lessonId).orElseThrow();
        CoursePackage coursePackage = coursePackageRepository.findById(packageId).orElseThrow();
        assertThat(lesson.getContentStatus()).isEqualTo(ContentStatus.SUBMITTED);
        assertThat(coursePackage.getContentStatus()).isEqualTo(ContentStatus.SUBMITTED);
    }

    @Test
    void adminShouldApproveRejectPublishAndListSubmissions() throws Exception {
        String teacherEmail = uniqueEmail("admin-content");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");

        Long moduleId = createModule(teacherToken, "Animals module");
        mockMvc.perform(post("/api/v1/teachers/modules/" + moduleId + "/submit")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/content/submissions")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].contentType").isString());

        mockMvc.perform(post("/api/v1/admin/content/MODULE/" + moduleId + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Looks good"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("APPROVED"));

        mockMvc.perform(post("/api/v1/admin/content/MODULE/" + moduleId + "/publish")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Publish now"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("PUBLISHED"));

        Long draftModuleId = createModule(teacherToken, "Rejected module");
        mockMvc.perform(post("/api/v1/teachers/modules/" + draftModuleId + "/submit")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/content/MODULE/" + draftModuleId + "/reject")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Needs more lesson detail"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Needs more lesson detail"));
    }

    @Test
    void adminShouldArchivePublishedPackageAndPublicShouldNoLongerSeeIt() throws Exception {
        String teacherEmail = uniqueEmail("archive-package");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");

        Long packageId = createPackage(teacherToken, "Archived package", "PAID", 12.90);
        submitApproveAndPublish("PACKAGE", packageId, teacherToken, adminToken);

        mockMvc.perform(get("/api/v1/public/courses/PACKAGE-" + packageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Archived package"));

        mockMvc.perform(patch("/api/v1/admin/content/PACKAGE/" + packageId + "/archive")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Archive after review"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("ARCHIVED"));

        CoursePackage archivedPackage = coursePackageRepository.findById(packageId).orElseThrow();
        assertThat(archivedPackage.getContentStatus()).isEqualTo(ContentStatus.ARCHIVED);

        mockMvc.perform(get("/api/v1/public/courses/PACKAGE-" + packageId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectWithoutReasonShouldFailAndNonAdminShouldBeBlocked() throws Exception {
        String teacherEmail = uniqueEmail("admin-block");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long moduleId = createModule(teacherToken, "Blocked module");
        mockMvc.perform(post("/api/v1/teachers/modules/" + moduleId + "/submit")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        mockMvc.perform(post("/api/v1/admin/content/MODULE/" + moduleId + "/reject")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        String studentEmail = uniqueEmail("student-block-content");
        String studentToken = registerStudentAndLogin(studentEmail);
        mockMvc.perform(post("/api/v1/admin/content/MODULE/" + moduleId + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "forbidden"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void publicDiscoveryShouldOnlyShowPublishedContentAndPaidContentShouldBeLocked() throws Exception {
        String teacherEmail = uniqueEmail("public-discovery");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");

        Long publishedLessonId = createLesson(teacherToken, "Published free lesson", "FREE", 0);
        submitApproveAndPublish("LESSON", publishedLessonId, teacherToken, adminToken);

        Long publishedPackageId = createPackage(teacherToken, "Published paid package", "PAID", 15.50);
        submitApproveAndPublish("PACKAGE", publishedPackageId, teacherToken, adminToken);

        Long draftLessonId = createLesson(teacherToken, "Draft hidden lesson", "FREE", 0);
        assertThat(lessonRepository.findById(draftLessonId).orElseThrow().getContentStatus()).isEqualTo(ContentStatus.DRAFT);

        mockMvc.perform(get("/api/v1/public/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.title=='Published free lesson')]").exists())
                .andExpect(jsonPath("$.data.items[?(@.title=='Published paid package')]").exists())
                .andExpect(jsonPath("$.data.items[?(@.title=='Draft hidden lesson')]").doesNotExist())
                .andExpect(jsonPath("$.data.items[?(@.title=='Published paid package' && @.locked==true)]").exists());

        mockMvc.perform(get("/api/v1/public/courses/LESSON-" + publishedLessonId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Published free lesson"))
                .andExpect(jsonPath("$.data.locked").value(false));

        mockMvc.perform(get("/api/v1/public/courses/PACKAGE-" + publishedPackageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Published paid package"))
                .andExpect(jsonPath("$.data.locked").value(true));

        Long publishedModuleId = createModule(teacherToken, "Published browse-hidden module");
        submitApproveAndPublish("MODULE", publishedModuleId, teacherToken, adminToken);

        mockMvc.perform(get("/api/v1/public/courses/MODULE-" + publishedModuleId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/public/courses/LESSON-" + draftLessonId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void studentCannotAccessTeacherContentApisAndGuestCannotAccessProtectedContentApis() throws Exception {
        String studentToken = registerStudentAndLogin(uniqueEmail("student-content-forbidden"));

        mockMvc.perform(get("/api/v1/teachers/modules")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/v1/teachers/modules")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(modulePayload("Guest blocked module")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    private void approveTeacherProfile(String teacherEmail) throws Exception {
        TeacherProfile profile = registerTeacherWithVerificationDocument(teacherEmail);
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        mockMvc.perform(post("/api/v1/admin/teachers/" + profile.getId() + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Approved for content management"))))
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
                "verification".getBytes());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/teachers/verification-documents")
                .file(file)
                .param("documentType", "QUALIFICATION")
                .param("description", "Qualification proof")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return teacherProfileRepository.findDetailedByUserId(user.getId()).orElseThrow();
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

    private Long createModule(String teacherToken, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teachers/modules")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(modulePayload(title)))
                .andExpect(status().isOk())
                .andReturn();

        return extractId(result);
    }

    private Long createLesson(String teacherToken, String title, String accessType, double price) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teachers/lessons")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "description", "Phase 6 lesson",
                        "levelId", beginnerLevelId(),
                        "categoryId", categoryId("Vocabulary"),
                        "learningType", "RECORDED",
                        "accessType", accessType,
                        "price", price,
                        "estimatedDurationMinutes", 10,
                        "basicContent", "Lesson intro"))))
                .andExpect(status().isOk())
                .andReturn();

        return extractId(result);
    }

    private Long createPackage(String teacherToken, String title, String accessType, double price) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/teachers/packages")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "description", "Phase 6 package",
                        "levelId", beginnerLevelId(),
                        "categoryId", categoryId("Vocabulary"),
                        "learningType", "RECORDED",
                        "accessType", accessType,
                        "price", price))))
                .andExpect(status().isOk())
                .andReturn();

        return extractId(result);
    }

    private void submitApproveAndPublish(String type, Long id, String teacherToken, String adminToken) throws Exception {
        String teacherPath = switch (type) {
            case "LESSON" -> "/api/v1/teachers/lessons/" + id + "/submit";
            case "PACKAGE" -> "/api/v1/teachers/packages/" + id + "/submit";
            case "MODULE" -> "/api/v1/teachers/modules/" + id + "/submit";
            default -> throw new IllegalArgumentException("Unsupported type");
        };

        mockMvc.perform(post(teacherPath)
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/content/" + type + "/" + id + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Approved"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/content/" + type + "/" + id + "/publish")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Published"))))
                .andExpect(status().isOk());
    }

    private String modulePayload(String title) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "title", title,
                "description", "Module description",
                "levelId", beginnerLevelId(),
                "categoryId", categoryId("Vocabulary"),
                "accessType", "FREE",
                "price", 0));
    }

    private Long extractId(MvcResult result) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("id").asLong();
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

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("token").asText();
    }

    private Long beginnerLevelId() {
        return 1L;
    }

    private Long categoryId(String categoryName) {
        return switch (categoryName) {
            case "Pinyin" -> 1L;
            case "Tones" -> 2L;
            case "Vocabulary" -> 3L;
            case "Chinese Characters" -> 4L;
            case "Listening" -> 5L;
            case "Reading" -> 6L;
            case "Writing / Tracing" -> 7L;
            case "Daily Conversation" -> 8L;
            case "Numbers" -> 9L;
            case "Colours" -> 10L;
            case "Animals" -> 11L;
            case "Family" -> 12L;
            case "School" -> 13L;
            case "Food" -> 14L;
            case "Greetings" -> 15L;
            default -> throw new IllegalArgumentException("Unknown category name: " + categoryName);
        };
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
