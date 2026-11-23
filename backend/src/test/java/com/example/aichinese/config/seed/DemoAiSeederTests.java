package com.example.aichinese.config.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.aichinese.entity.AiResponseReport;
import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.AiUsageQuotaCounter;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearnUnit;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AiResponseReportRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearnUnitRepository;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.plan.LearningPlanService;

import jakarta.persistence.EntityManager;

/**
 * Amira's "N of 100 requests today" must match her seeded log rows of today whatever time the demo starts: a "today"
 * row whose time is still ahead of the clock is seeded on the day before, and then it is not one of today's requests.
 * The seeded times are platform wall-clock times stored server-local ({@link DemoClock}): on a server in another zone
 * than the platform's, the rows about one moment still match.
 */
class DemoAiSeederTests {

    /** 12:00 on 14 November 2026 in Singapore, the platform zone. */
    private static final Instant NOON_IN_SINGAPORE = Instant.parse("2026-11-14T04:00:00Z");
    /** 10:30 in Singapore: before Amira's 11:00 seeded request, so that row rolls back to the previous day. */
    private static final Instant MID_MORNING_IN_SINGAPORE = Instant.parse("2026-11-14T02:30:00Z");
    private static final ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Singapore");

    private final Map<String, User> personas = new HashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private final EntityManager entityManager = mock(EntityManager.class);
    private final AiResponseReportRepository reports = mock(AiResponseReportRepository.class);
    private final DemoClock clock = new DemoClock(PLATFORM_ZONE, PLATFORM_ZONE, NOON_IN_SINGAPORE);
    /** The platform in Singapore, the server in UTC: platform wall times are 8 hours ahead of the stored ones. */
    private final DemoClock utcServer = new DemoClock(PLATFORM_ZONE, ZoneOffset.UTC, NOON_IN_SINGAPORE);

    @Test
    void reportsStayPendingWhenTheDemoHasNoAdminToReviewThem() {
        seeder(clock).run();

        var saved = ArgumentCaptor.forClass(AiResponseReport.class);
        verify(reports, times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(report -> {
            assertThat(report.getStatus()).isEqualTo(AiResponseReportStatus.PENDING_REVIEW);
            assertThat(report.getReviewerUser()).isNull();
            assertThat(report.getReviewerNote()).isNull();
            assertThat(report.getReviewedAt()).isNull();
            assertThat(report.getCreatedAt()).isNotNull();
            assertThat(report.getUpdatedAt()).isEqualTo(report.getCreatedAt());
        });
    }

    @Test
    void todaysRequestCountMatchesTheSeededLogRowsOfToday() {
        DemoClock midMorning = new DemoClock(PLATFORM_ZONE, PLATFORM_ZONE, MID_MORNING_IN_SINGAPORE);
        seeder(midMorning).run();

        User amira = personas.get(DemoPersonas.STUDENT.email());
        LocalDate today = midMorning.today();
        long todaysRows = savedLogs().stream()
                .filter(entry -> entry.getUser() == amira)
                .filter(entry -> entry.getStatus() != AiRequestStatus.UNAVAILABLE)
                .filter(entry -> today.equals(midMorning.dayOf(entry.getCreatedAt())))
                .count();
        assertThat(todaysRows).isEqualTo(2);
        ArgumentCaptor<AiUsageQuotaCounter> counter = ArgumentCaptor.forClass(AiUsageQuotaCounter.class);
        verify(entityManager).persist(counter.capture());
        assertThat(counter.getValue().getUser()).isSameAs(amira);
        assertThat(counter.getValue().getUsageDay()).isEqualTo(today);
        assertThat(counter.getValue().getDayCount()).isEqualTo(todaysRows);
    }

    /**
     * The SparkBot requests behind the two demo plans are logged at the moments the plans were built (20:15 eight days ago
     * and 18:40 three days ago in Singapore), not 8 hours later on another platform day; and today's requests are
     * counted on the platform's today.
     */
    @Test
    void onAServerInUtcThePlansAndTheirSparkBotRequestsAreSeededAtTheSameMoment() {
        seeder(utcServer).run();
        Map<String, LocalDateTime> builtAt = seedPlans(utcServer);

        assertThat(builtAt)
                .containsEntry(DemoPersonas.STUDENT.email(), LocalDateTime.of(2026, 11, 6, 12, 15))
                .containsEntry(DemoPersonas.CHEN_HAO.email(), LocalDateTime.of(2026, 11, 11, 10, 40));
        Map<String, LocalDateTime> requests = savedLogs().stream()
                .filter(entry -> entry.getFeatureKey() == AiFeatureKey.LEARNING_PLAN)
                .collect(Collectors.toMap(entry -> entry.getUser().getEmail(), AiUsageLog::getCreatedAt));
        assertThat(requests).isEqualTo(builtAt);

        // Amira's requests of today before noon in Singapore: 9:42, 10:05 and 11:00.
        ArgumentCaptor<AiUsageQuotaCounter> counter = ArgumentCaptor.forClass(AiUsageQuotaCounter.class);
        verify(entityManager).persist(counter.capture());
        assertThat(counter.getValue().getUsageDay()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(counter.getValue().getDayCount()).isEqualTo(3);
        assertThat(counter.getValue().getMinuteStartedAt()).isEqualTo(LocalDateTime.of(2026, 11, 14, 3, 0));
    }

    private DemoAiSeeder seeder(DemoClock demoClock) {
        UserRepository users = mock(UserRepository.class);
        when(users.findByEmailWithRoles(anyString())).thenAnswer(invocation ->
                Optional.of(persona(invocation.getArgument(0))));
        when(users.findAllByRoleName(RoleName.ADMIN)).thenReturn(List.of());
        return new DemoAiSeeder(users, reports, mock(LessonRepository.class), entityManager, demoClock);
    }

    private List<AiUsageLog> savedLogs() {
        var logs = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(entityManager, atLeastOnce()).persist(logs.capture());
        return logs.getAllValues();
    }

    /** Runs DemoPlanSeeder and returns when each student's plan was built, by e-mail. */
    private Map<String, LocalDateTime> seedPlans(DemoClock demoClock) {
        StudentProfileRepository students = mock(StudentProfileRepository.class);
        when(students.findByUserEmail(anyString())).thenAnswer(invocation -> {
            StudentProfile profile = new StudentProfile();
            profile.setId(ids.incrementAndGet());
            profile.setUser(persona(invocation.getArgument(0)));
            return Optional.of(profile);
        });
        LearningPlanRepository plans = mock(LearningPlanRepository.class);
        when(plans.findFirstByStudentIdAndActiveTrueOrderByVersionDesc(any())).thenReturn(Optional.empty());
        LearnUnitRepository units = mock(LearnUnitRepository.class);
        when(units.findAll()).thenReturn(IntStream.rangeClosed(1, 5).mapToObj(position -> {
            LearnUnit unit = new LearnUnit();
            unit.setId(ids.incrementAndGet());
            unit.setPosition(position);
            return unit;
        }).toList());
        CoursePackageRepository classes = mock(CoursePackageRepository.class);
        when(classes.findAll()).thenReturn(Stream.of("Mandarin for Campus Life", "Travel Mandarin", "Shopping and Prices")
                .map(title -> {
                    CoursePackage coursePackage = new CoursePackage();
                    coursePackage.setId(ids.incrementAndGet());
                    coursePackage.setTitle(title);
                    return coursePackage;
                })
                .toList());
        LearningPlanService planService = mock(LearningPlanService.class);

        new DemoPlanSeeder(students, plans, units, classes, planService, demoClock).run();

        ArgumentCaptor<StudentProfile> student = ArgumentCaptor.forClass(StudentProfile.class);
        ArgumentCaptor<LocalDateTime> builtAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(planService, times(2)).seed(student.capture(), any(), any(), any(), builtAt.capture());
        List<StudentProfile> profiles = student.getAllValues();
        List<LocalDateTime> buildTimes = builtAt.getAllValues();
        Map<String, LocalDateTime> byEmail = new HashMap<>();
        for (int index = 0; index < buildTimes.size(); index++) {
            byEmail.put(profiles.get(index).getUser().getEmail(), buildTimes.get(index));
        }
        return byEmail;
    }

    private User persona(String email) {
        return personas.computeIfAbsent(email, address -> {
            Role role = new Role();
            role.setName(RoleName.STUDENT);
            User user = new User();
            user.setId(ids.incrementAndGet());
            user.setEmail(address);
            user.setRoles(Set.of(role));
            return user;
        });
    }
}
