package com.example.aichinese.support;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LearnLessonCompletion;
import com.example.aichinese.entity.LevelCheckOption;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.LearnLessonRepository;
import com.example.aichinese.repository.LearnQuestionRepository;
import com.example.aichinese.repository.LevelCheckOptionRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.learn.PracticeCatalog;
import com.example.aichinese.service.learn.PracticeSetDirectory;
import com.example.aichinese.util.PaginationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared setup of the MockMvc integration tests: the test profile with the seeded admin account of
 * {@link LearningApiTestClient}, a fresh {@link #api} client for every test, and the lookups several test classes
 * need (accounts, notifications, Learn lessons, practice sets, level-check options).
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
    protected StudentProfileRepository studentProfileRepository;

    @Autowired
    protected NotificationRepository notificationRepository;

    @Autowired
    protected LearnLessonRepository learnLessonRepository;

    @Autowired
    protected LearnQuestionRepository learnQuestionRepository;

    @Autowired
    protected LevelCheckOptionRepository levelCheckOptionRepository;

    @Autowired
    protected PracticeCatalog practiceCatalog;

    @Autowired
    protected PracticeSetDirectory practiceSets;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected AuditLogRepository auditLogRepository;

    protected LearningApiTestClient api;

    @BeforeEach
    void createApiClient() {
        api = new LearningApiTestClient(mockMvc, objectMapper);
    }

    /** The account registered with {@code email}. */
    protected User user(String email) {
        return userRepository.findByEmailWithRoles(email).orElseThrow();
    }

    protected SecurityUserDetails principalFor(String email) {
        return new SecurityUserDetails(user(email));
    }

    /** The learner profile of the account registered with {@code email}. */
    protected StudentProfile studentProfile(String email) {
        return studentProfileRepository.findByUserEmail(email).orElseThrow();
    }

    /** Every notification of the account registered with {@code email}, newest first. */
    protected List<Notification> notificationsOf(String email) {
        return notificationRepository.findByUserIdAndStatus(
                user(email).getId(), null, Pageable.unpaged(PaginationUtils.NEWEST_FIRST)).getContent();
    }

    protected List<AuditLog> auditEntriesAbout(String targetType, long targetId) {
        return auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(targetType, targetId, Pageable.unpaged());
    }

    protected Optional<AuditLog> auditForRequest(String requestId, String action) {
        return auditLogRepository.findOne((root, query, builder) -> builder.and(
                builder.equal(root.get("requestId"), requestId), builder.equal(root.get("actionType"), action)));
    }

    /** The internal id of the support request with this public reference number. */
    protected Long supportRequestId(int number) {
        return jdbc.queryForObject("select id from support_requests where request_number = ?", Long.class, number);
    }

    /** The Learn path lesson with this title. */
    protected LearnLesson learnLesson(String title) {
        return learnLessonRepository.findPathInOrder().stream()
                .filter(lesson -> lesson.getTitle().equals(title))
                .findFirst()
                .orElseThrow();
    }

    /** The right answer to every question of the lesson (read from the server's own answer key). */
    protected List<Map<String, Object>> rightAnswers(LearnLesson lesson) {
        return learnQuestionRepository.findByLessonIdOrderByPositionAscIdAsc(lesson.getId()).stream()
                .map(question -> Map.<String, Object>of("questionId", question.getId(), "answer", question.getAnswer()))
                .toList();
    }

    /** A lesson completed once without recording XP or badges. */
    protected static LearnLessonCompletion completion(StudentProfile student, LearnLesson lesson, LocalDateTime when) {
        LearnLessonCompletion completion = new LearnLessonCompletion();
        completion.setStudent(student);
        completion.setLesson(lesson);
        completion.setAttempts(1);
        completion.setFirstCompletedAt(when);
        completion.setLastCompletedAt(when);
        return completion;
    }

    protected List<Map<String, String>> gotItRatings(PracticeActivityContent set) {
        return practiceCatalog.content(PracticeType.FLASHCARDS, set).items().stream()
                .map(card -> Map.of("itemKey", card.key(), "answer", "GOT_IT"))
                .toList();
    }

    protected static String wrongOption(PracticeCatalog.Item item) {
        return item.gradable().options().stream()
                .map(ChoiceOption::text)
                .filter(text -> !item.gradable().isCorrect(text))
                .findFirst()
                .orElseThrow();
    }

    /** The platform practice set of this type with this title, whatever its status. */
    protected PracticeActivityContent practiceSet(PracticeType type, String title) {
        return practiceSets.sets(type, null).stream()
                .filter(candidate -> title.equals(candidate.getTitle()))
                .findFirst()
                .orElseThrow();
    }

    /** The id of the right option of a level-check question. */
    protected Long rightLevelCheckOption(long questionId) {
        return levelCheckOptionRepository.findByQuestionIdOrderByPositionAsc(questionId).stream()
                .filter(LevelCheckOption::isCorrect)
                .findFirst()
                .orElseThrow()
                .getId();
    }

    /** One answer of a level check: the chosen option, or none ({@code null}: "I don't know"). */
    protected static Map<String, Object> levelCheckAnswer(long questionId, Long optionId) {
        Map<String, Object> answer = new HashMap<>();
        answer.put("questionId", questionId);
        answer.put("optionId", optionId);
        return answer;
    }
}
