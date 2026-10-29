package com.example.aichinese.config.seed;

import java.time.LocalDateTime;
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
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.RoleRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;

/**
 * The demo accounts (see {@link DemoPersonas}). Each module seeds its own demo content afterwards (classes in
 * DemoClassesSeeder).
 */
@Component
@Order(1)
@Profile("demo & !prod & !production")
public class DemoContentSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${DEMO_TEACHER_PASSWORD:DemoTeacher123!}")
    private String teacherPassword;

    @Value("${DEMO_STUDENT_PASSWORD:DemoStudent123!}")
    private String studentPassword;

    public DemoContentSeeder(
            UserRepository userRepository,
            RoleRepository roleRepository,
            TeacherProfileRepository teacherProfileRepository,
            StudentProfileRepository studentProfileRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByEmailIgnoreCase(DemoPersonas.TEACHER.email())) {
            return;
        }

        createTeacher(DemoPersonas.TEACHER,
                "A Mandarin tutor helping university students and young professionals build everyday communication skills.",
                TeacherStatus.APPROVED);
        DemoPersonas.OTHER_TEACHERS.forEach(persona -> createTeacher(persona,
                "Mandarin teacher for young adult beginners in Malaysia, focusing on everyday speaking.",
                TeacherStatus.APPROVED));
        createTeacher(DemoPersonas.APPLICANT,
                "Mandarin teacher for adult beginners. I help university students and working adults build everyday speaking confidence.",
                TeacherStatus.PENDING);
        createStudent(DemoPersonas.STUDENT);
        DemoPersonas.CLASSMATES.forEach(this::createStudent);
    }

    private void createTeacher(DemoPersonas.Persona persona, String bio, TeacherStatus status) {
        TeacherProfile profile = new TeacherProfile();
        profile.setUser(createUser(persona.email(), teacherPassword, persona.displayName(), RoleName.TEACHER));
        profile.setTeacherStatus(status);
        profile.setBio(bio);
        if (status == TeacherStatus.APPROVED) {
            profile.setVerifiedAt(LocalDateTime.now());
        }
        teacherProfileRepository.save(profile);
    }

    private void createStudent(DemoPersonas.Persona persona) {
        StudentProfile profile = new StudentProfile();
        profile.setUser(createUser(persona.email(), studentPassword, persona.displayName(), RoleName.STUDENT));
        studentProfileRepository.save(profile);
    }

    private User createUser(String email, String password, String displayName, RoleName roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(roleName + " role must be seeded first."));
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setPreferredLanguage("en");
        user.setRoles(Set.of(role));
        return userRepository.save(user);
    }
}
