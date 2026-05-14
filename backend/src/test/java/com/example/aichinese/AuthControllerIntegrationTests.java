package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                // Wave-6 regression: /auth/me returns Cache-Control: no-store
                // so the JSON profile cannot be replayed from browser cache or
                // bfcache after the user logs out.
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("no-store")));
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
    void shouldReturnForbiddenWhenTeacherAccessesAdminRoute() throws Exception {
        String email = uniqueEmail("forbidden-teacher");
        registerTeacher(email, "Password123!");
        String token = loginAndExtractToken(email, "Password123!");

        mockMvc.perform(get("/api/v1/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldReturnForbiddenWhenStudentAccessesTeacherRoute() throws Exception {
        String email = uniqueEmail("student-on-teacher");
        registerStudent(email, "Password123!");
        String token = loginAndExtractToken(email, "Password123!");

        mockMvc.perform(get("/api/v1/teachers/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
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
    void shouldReturnBadRequestWhenPathVariableTypeDoesNotMatch() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/public/teachers/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Invalid value for parameter")))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("Exception");
        assertThat(responseBody).doesNotContain("at org.springframework");
    }

    @Test
    void shouldReturnNotFoundForUnknownAuthenticatedApiRoute() throws Exception {
        // After the default-deny SecurityConfig change, an unmapped path is only
        // reachable when the request is authenticated. We therefore log in as a student
        // and hit a non-existent path under the student namespace so that Spring Security
        // permits the request and the GlobalExceptionHandler can handle the resulting
        // NoResourceFoundException with a clean 404 response.
        String email = uniqueEmail("not-found");
        registerStudent(email, "Password123!");
        String token = loginAndExtractToken(email, "Password123!");

        MvcResult result = mockMvc.perform(get("/api/v1/students/this-route-does-not-exist-" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("not found")))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("Exception");
        assertThat(responseBody).doesNotContain("at org.springframework");
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

    @Test
    void shouldReturnConsistentForgotPasswordResponseRegardlessOfEmailExistence() throws Exception {
        // Register a real account first so we can compare responses for a known-existing
        // email and a definitely-non-existent email. The endpoint must return the same
        // public message in both cases so attackers cannot probe which addresses are
        // registered on the platform.
        String registeredEmail = uniqueEmail("forgot-existing");
        registerStudent(registeredEmail, "Password123!");

        MvcResult existingResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", registeredEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        MvcResult missingResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "definitely-not-registered-" + UUID.randomUUID() + "@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode existingBody = objectMapper.readTree(existingResult.getResponse().getContentAsString());
        JsonNode missingBody = objectMapper.readTree(missingResult.getResponse().getContentAsString());

        assertThat(existingBody.path("message").asText()).isEqualTo(missingBody.path("message").asText());
        assertThat(existingBody.path("data").isNull()).isTrue();
        assertThat(missingBody.path("data").isNull()).isTrue();
    }

    @Test
    void shouldReturnUnauthorizedForUnmappedApiRouteWithoutToken() throws Exception {
        // Default-deny check: if a request path that is not explicitly listed in
        // SecurityConfig is reached without authentication, the application must
        // respond with 401, not silently allow the request through.
        mockMvc.perform(get("/api/v1/some-future-route-without-explicit-rule"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldLockOutLoginAfterRepeatedFailedAttempts() throws Exception {
        // Verify the brute-force defence: after the LoginAttemptLimiter
        // threshold (5 by default) is reached, further attempts must be
        // refused with HTTP 429 BEFORE the password is even checked. We use
        // a freshly registered student so this test is independent of the
        // shared seeded admin account, and a unique email so other tests in
        // the same JVM cannot poison the limiter's bucket.
        String email = uniqueEmail("lockout-target");
        registerStudent(email, "Password123!");

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "email", email,
                            "password", "WrongPassword!"))))
                    .andExpect(status().isUnauthorized());
        }

        // Sixth attempt — still wrong password — should be refused at the
        // rate-limiter layer with 429 Too Many Requests, not 401.
        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", "WrongPassword!"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));

        // And critically: even the *correct* password is refused while the
        // bucket is locked, otherwise an attacker who finally guesses right
        // would still slip through after the threshold.
        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", "Password123!"))))
                .andExpect(status().isTooManyRequests());
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
