package com.example.aichinese.config.seed;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningCategory;
import com.example.aichinese.entity.LearningLevel;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.PackageItem;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.PackageItemType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.LearningLevelRepository;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.repository.RoleRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;

@Component
@Order(1)
@Profile("demo & !prod & !production")
@ConditionalOnProperty(name = "app.demo.seed-content", havingValue = "true", matchIfMissing = true)
public class DemoContentSeeder implements CommandLineRunner {

    private static final String TEACHER_EMAIL = "teacher@fyp.local";
    private static final String STUDENT_EMAIL = "student@fyp.local";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LearningLevelRepository learningLevelRepository;
    private final LearningCategoryRepository learningCategoryRepository;
    private final LearningModuleRepository learningModuleRepository;
    private final LessonRepository lessonRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final PackageItemRepository packageItemRepository;
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
            LearningLevelRepository learningLevelRepository,
            LearningCategoryRepository learningCategoryRepository,
            LearningModuleRepository learningModuleRepository,
            LessonRepository lessonRepository,
            CoursePackageRepository coursePackageRepository,
            PackageItemRepository packageItemRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.learningLevelRepository = learningLevelRepository;
        this.learningCategoryRepository = learningCategoryRepository;
        this.learningModuleRepository = learningModuleRepository;
        this.lessonRepository = lessonRepository;
        this.coursePackageRepository = coursePackageRepository;
        this.packageItemRepository = packageItemRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByEmailIgnoreCase(TEACHER_EMAIL)) {
            return;
        }

        LearningLevel beginner = learningLevelRepository.findByName("Beginner")
                .orElseThrow(() -> new IllegalStateException("Beginner level must be seeded first."));
        LearningCategory greetings = category("Greetings");
        TeacherProfile teacher = createTeacher();
        createStudent(beginner);

        LearningModule module = new LearningModule();
        module.setTeacher(teacher);
        module.setLevel(beginner);
        module.setCategory(greetings);
        module.setTitle("Beginner Mandarin Foundations");
        module.setDescription("A friendly first path through greetings, numbers, and family words.");
        module.setContentStatus(ContentStatus.PUBLISHED);
        module.setAccessType(AccessType.FREE);
        module.setPrice(BigDecimal.ZERO);
        module = learningModuleRepository.save(module);

        Lesson greetingsLesson = createLesson(module, teacher, beginner, greetings,
                "Hello and Introductions", "Learn to greet a friend and introduce yourself.",
                "你好 (nǐ hǎo) means hello. 我叫… (wǒ jiào…) means my name is… Try greeting a friend and saying your name.",
                12);
        Lesson numbersLesson = createLesson(module, teacher, beginner, category("Numbers"),
                "Count from One to Ten", "Practice Mandarin numbers with short spoken examples.",
                "一 (yī), 二 (èr), 三 (sān), 四 (sì), 五 (wǔ), 六 (liù), 七 (qī), 八 (bā), 九 (jiǔ), 十 (shí). Count ten objects around you.",
                15);
        Lesson familyLesson = createLesson(module, teacher, beginner, category("Family"),
                "Family Words", "Say the names of family members in Mandarin.",
                "妈妈 (māma) is mother. 爸爸 (bàba) is father. 家 (jiā) means family or home. Describe your family using these words.",
                14);

        CoursePackage coursePackage = new CoursePackage();
        coursePackage.setTeacher(teacher);
        coursePackage.setLevel(beginner);
        coursePackage.setCategory(greetings);
        coursePackage.setTitle("Beginner Mandarin Starter Path");
        coursePackage.setDescription("Three short lessons for a confident start in Mandarin.");
        coursePackage.setContentStatus(ContentStatus.PUBLISHED);
        coursePackage.setAccessType(AccessType.FREE);
        coursePackage.setLearningType(LearningType.RECORDED);
        coursePackage.setPrice(BigDecimal.ZERO);
        coursePackage = coursePackageRepository.save(coursePackage);

        int order = 1;
        for (Lesson lesson : List.of(greetingsLesson, numbersLesson, familyLesson)) {
            PackageItem item = new PackageItem();
            item.setCoursePackage(coursePackage);
            item.setItemType(PackageItemType.LESSON);
            item.setItemId(lesson.getId());
            item.setSortOrder(order++);
            item.setTitleSnapshot(lesson.getTitle());
            packageItemRepository.save(item);
        }
    }

    private TeacherProfile createTeacher() {
        Role role = roleRepository.findByName(RoleName.TEACHER)
                .orElseThrow(() -> new IllegalStateException("TEACHER role must be seeded first."));
        User user = new User();
        user.setEmail(TEACHER_EMAIL);
        user.setPasswordHash(passwordEncoder.encode(teacherPassword));
        user.setDisplayName("Li Wei");
        user.setPreferredLanguage("en");
        user.setRoles(Set.of(role));
        user = userRepository.save(user);

        TeacherProfile profile = new TeacherProfile();
        profile.setUser(user);
        profile.setTeacherStatus(TeacherStatus.APPROVED);
        profile.setBio("A supportive teacher introducing children to everyday Mandarin.");
        profile.setQualificationSummary("Demo educator for the local learning experience.");
        profile.setTeachingCategories("Greetings, Numbers, Family");
        profile.setVerifiedAt(LocalDateTime.now());
        profile.setPublicVisible(true);
        return teacherProfileRepository.save(profile);
    }

    private void createStudent(LearningLevel beginner) {
        Role role = roleRepository.findByName(RoleName.STUDENT)
                .orElseThrow(() -> new IllegalStateException("STUDENT role must be seeded first."));
        User user = new User();
        user.setEmail(STUDENT_EMAIL);
        user.setPasswordHash(passwordEncoder.encode(studentPassword));
        user.setDisplayName("Demo Learner");
        user.setPreferredLanguage("en");
        user.setRoles(Set.of(role));
        user = userRepository.save(user);

        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        profile.setCurrentLevel(beginner);
        profile.setDisplayName("Demo Learner");
        profile.setCountry("Malaysia");
        studentProfileRepository.save(profile);
    }

    private LearningCategory category(String name) {
        return learningCategoryRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException(name + " category must be seeded first."));
    }

    private Lesson createLesson(
            LearningModule module,
            TeacherProfile teacher,
            LearningLevel level,
            LearningCategory category,
            String title,
            String summary,
            String content,
            int durationMinutes) {
        Lesson lesson = new Lesson();
        lesson.setModule(module);
        lesson.setTeacher(teacher);
        lesson.setLevel(level);
        lesson.setCategory(category);
        lesson.setTitle(title);
        lesson.setSummary(summary);
        lesson.setLessonContent(content);
        lesson.setContentStatus(ContentStatus.PUBLISHED);
        lesson.setAccessType(AccessType.FREE);
        lesson.setLearningType(LearningType.RECORDED);
        lesson.setPrice(BigDecimal.ZERO);
        lesson.setEstimatedDurationMinutes(durationMinutes);
        return lessonRepository.save(lesson);
    }
}
