package com.example.aichinese.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared setup of the MockMvc integration tests: the test profile with the seeded admin account of
 * {@link LearningApiTestClient}, and a fresh {@link #api} client for every test.
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=" + LearningApiTestClient.ADMIN_EMAIL,
        "DEFAULT_ADMIN_PASSWORD=" + LearningApiTestClient.ADMIN_PASSWORD
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TeacherProfileRepository teacherProfileRepository;

    @Autowired
    protected StudentProfileRepository studentProfileRepository;

    protected LearningApiTestClient api;

    @BeforeEach
    void createApiClient() {
        api = new LearningApiTestClient(mockMvc, objectMapper, userRepository, teacherProfileRepository);
    }

    /** The account registered with {@code email}. */
    protected User user(String email) {
        return userRepository.findByEmailWithRoles(email).orElseThrow();
    }

    /** The student profile of the account registered with {@code email}. */
    protected StudentProfile studentProfile(String email) {
        return studentProfileRepository.findByUserId(user(email).getId()).orElseThrow();
    }

    /** The teacher profile (with its user) of the account registered with {@code email}. */
    protected TeacherProfile teacherProfile(String email) {
        return teacherProfileRepository.findDetailedByUserId(user(email).getId()).orElseThrow();
    }
}
