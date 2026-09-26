package com.example.aichinese;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.aichinese.config.seed.DemoContentSeeder;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.PublicContentService;

@SpringBootTest
@ActiveProfiles("demo")
class DemoContentSeederTests {

    @Autowired
    private DemoContentSeeder demoContentSeeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private CoursePackageRepository coursePackageRepository;

    @Autowired
    private PublicContentService publicContentService;

    @Test
    void seedsPublicCoursesAndDoesNotDuplicateThemOnLaterRuns() throws Exception {
        assertTrue(userRepository.existsByEmailIgnoreCase("teacher@fyp.local"));
        assertTrue(userRepository.existsByEmailIgnoreCase("student@fyp.local"));
        assertEquals(3, lessonRepository.count());
        assertEquals(1, coursePackageRepository.count());
        assertEquals(4, publicContentService.discoverCourses(null, null, null, null, null, null, 0, 10).totalItems());

        demoContentSeeder.run();

        assertEquals(3, lessonRepository.count());
        assertEquals(1, coursePackageRepository.count());
    }
}
