package com.example.aichinese.config.seed;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.RoleRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.util.LanguageCodes;

import lombok.RequiredArgsConstructor;

/**
 * The demo learner accounts (see {@link DemoPersonas}); the admin is the default admin. Each module seeds its own demo
 * content afterwards (classes in DemoClassesSeeder; the notifications and audit log entries about these accounts in
 * DemoOpsSeeder).
 */
@Component
@Order(1)
@Profile("demo & !prod & !production")
@RequiredArgsConstructor
public class DemoContentSeeder implements CommandLineRunner {

    /** When the learners' accounts were created: before their oldest seeded activity (a class joined 40 days ago). */
    private static final int STUDENTS_JOINED_DAYS_AGO = 45;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoClock demoClock;

    @Value("${app.demo.student-password:${DEMO_STUDENT_PASSWORD:DemoStudent123!}}")
    private String studentPassword;

    @Override
    @Transactional
    public void run(String... args) {
        Role studentRole = roleRepository.findByName(RoleName.STUDENT).orElseThrow();
        LocalDateTime studentsJoinedAt = demoClock.daysAgo(STUDENTS_JOINED_DAYS_AGO, 19, 0);
        createStudentIfAbsent(DemoPersonas.STUDENT, studentsJoinedAt, studentRole, true);
        DemoPersonas.OTHER_STUDENTS.forEach(persona -> createStudentIfAbsent(persona, studentsJoinedAt, studentRole, true));
        // Deactivated later by an admin (DemoOpsSeeder audits it).
        createStudentIfAbsent(DemoPersonas.ZHANG_YUXI, demoClock.daysAgo(20, 11, 0), studentRole, false);
    }

    /**
     * An account as sign-up makes it (AuthService.createAccount), created on {@code joinedAt}: the creation time is set
     * before the first save, because it cannot change afterwards, so the account is older than its seeded history.
     */
    private void createStudentIfAbsent(
            DemoPersonas.Persona persona, LocalDateTime joinedAt, Role studentRole, boolean active) {
        if (userRepository.existsByEmailIgnoreCase(persona.email())) {
            return;
        }
        User user = new User();
        user.setDisplayName(persona.displayName());
        user.setEmail(persona.email());
        user.setPasswordHash(passwordEncoder.encode(studentPassword));
        user.setPreferredLanguage(LanguageCodes.ENGLISH);
        user.setActive(active);
        user.setRoles(new HashSet<>(Set.of(studentRole)));
        user.setCreatedAt(joinedAt);
        // A demo address has no inbox to open a confirmation link in, so it counts as confirmed at sign-up.
        user.setEmailVerifiedAt(joinedAt.plusMinutes(5));
        StudentProfile profile = new StudentProfile();
        profile.setUser(userRepository.save(user));
        // The streak calendar of Progress starts at the month the learner joined.
        profile.setCreatedAt(joinedAt);
        studentProfileRepository.save(profile);
    }
}
