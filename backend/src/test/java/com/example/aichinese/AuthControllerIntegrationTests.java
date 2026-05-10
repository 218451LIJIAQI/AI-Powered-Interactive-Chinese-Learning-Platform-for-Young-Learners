package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.StudentProfileRepository;
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
class AuthControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Test
    void shouldRegisterStudentSuccessfully() throws Exception {
        String email = uniqueEmail("student");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register/student")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Student One",
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!",
                        "preferredLanguage", "ms"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.data.preferredLanguage").value("ms"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn();

        User user = userRepository.findByEmailWithRoles(email).orElseThrow();

        assertThat(user.getPasswordHash()).isNotBlank();
        assertThat(studentProfileRepository.findByUserId(user.getId())).isPresent();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password_hash");
    }

    @Test
    void shouldRegisterTeacherSuccessfullyWithPendingStatus() throws Exception {
        String email = uniqueEmail("teacher");

        mockMvc.perform(post("/api/v1/auth/register/teacher")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Teacher One",
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!",
                        "bio", "Friendly Mandarin teacher"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles[0]").value("TEACHER"))
                .andExpect(jsonPath("$.data.teacherStatus").value("PENDING"));

        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        TeacherProfile profile = teacherProfileRepository.findByUserId(user.getId()).orElseThrow();

        assertThat(profile.getTeacherStatus()).isEqualTo(TeacherStatus.PENDING);
    }

    @Test
    void shouldRejectDuplicateEmailRegistration() throws Exception {
        String email = uniqueEmail("duplicate");

        registerStudent(email, "Password123!");

        mockMvc.perform(post("/api/v1/auth/register/student")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Student Duplicate",
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email is already registered."));
    }

    @Test
    void shouldLoginSuccessfullyForStudent() throws Exception {
        String email = uniqueEmail("login-student");
        registerStudent(email, "Password123!");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", "Password123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.roles[0]").value("STUDENT"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password_hash");
    }

    @Test
    void shouldRejectLoginWithWrongPassword() throws Exception {
        String email = uniqueEmail("wrong-password");
        registerStudent(email, "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", "WrongPassword999!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void shouldAllowAdminLoginFromSeededAccount() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "admin@example.com",
                        "password", "SuperSecure123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("admin@example.com"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("ADMIN"));
    }

    @Test
    void shouldReturnCurrentUserForAuthenticatedStudent() throws Exception {
        String email = uniqueEmail("me-student");
        registerStudent(email, "Password123!");
        String token = loginAndExtractToken(email, "Password123!");

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.studentProfile").isMap())
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void shouldReturnCurrentUserForAuthenticatedTeacher() throws Exception {
        String email = uniqueEmail("me-teacher");
        registerTeacher(email, "Password123!");
        String token = loginAndExtractToken(email, "Password123!");

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.teacherProfile").isMap())
                .andExpect(jsonPath("$.data.teacherProfile.teacherStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.studentProfile").doesNotExist());
    }

    @Test
    void shouldReturnCurrentUserForAuthenticatedAdminWithoutProfiles() throws Exception {
        String token = loginAndExtractToken("admin@example.com", "SuperSecure123!");

        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("admin@example.com"))
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("\"studentProfile\":null");
        assertThat(responseBody).contains("\"teacherProfile\":null");
        assertThat(responseBody).doesNotContain("passwordHash");
        assertThat(responseBody).doesNotContain("password_hash");
    }

    @Test
    void shouldRejectProtectedRouteWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/students/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldReturnForbiddenWhenStudentAccessesAdminRoute() throws Exception {
        String email = uniqueEmail("forbidden-student");
        registerStudent(email, "Password123!");
        String token = loginAndExtractToken(email, "Password123!");

        mockMvc.perform(get("/api/v1/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource."));
    }

    @Test
    void shouldIncludeTeacherStatusWhenTeacherLogsIn() throws Exception {
        String email = uniqueEmail("teacher-login");
        registerTeacher(email, "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", "Password123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.roles[0]").value("TEACHER"))
                .andExpect(jsonPath("$.data.user.teacherStatus").value("PENDING"));
    }

    @Test
    void shouldReturnBadRequestWhenRequestBodyIsMalformedJson() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("malformed");
        assertThat(responseBody).doesNotContain("Exception");
    }

    @Test
    void shouldReturnServiceUnavailableForResetPasswordBecauseEmailDeliveryIsNotConfigured() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "token", "any-token",
                        "password", "Password123!",
                        "confirmPassword", "Password123!"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("unavailable")));
    }

    private void registerStudent(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/student")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Student Test",
                        "email", email,
                        "password", password,
                        "confirmPassword", password,
                        "preferredLanguage", "en"))))
                .andExpect(status().isOk());
    }

    private void registerTeacher(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/teacher")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Teacher Test",
                        "email", email,
                        "password", password,
                        "confirmPassword", password,
                        "bio", "Teacher bio"))))
                .andExpect(status().isOk());
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

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
