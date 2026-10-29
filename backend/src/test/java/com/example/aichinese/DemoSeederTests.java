package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.aichinese.config.seed.DemoClassesSeeder;
import com.example.aichinese.config.seed.DemoContentSeeder;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.classes.ClassCatalogueService;
import com.example.aichinese.service.classes.ClassReviewService;

@SpringBootTest
@ActiveProfiles("demo")
class DemoSeederTests {

    @Autowired
    private DemoContentSeeder demoContentSeeder;

    @Autowired
    private DemoClassesSeeder demoClassesSeeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private CoursePackageRepository coursePackageRepository;

    @Autowired
    private ClassEnrolmentRepository enrolmentRepository;

    @Autowired
    private ClassCatalogueService catalogue;

    @Autowired
    private ClassReviewService reviews;

    @Test
    void seedsThePrototypeClassesOnceWithEightInTheCatalogue() throws Exception {
        assertThat(userRepository.existsByEmailIgnoreCase("teacher@fyp.local")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("student@fyp.local")).isTrue();
        assertThat(catalogue.search(new ClassCatalogueService.Query(null, null, null, null, null), Set.of(), 0, 50)
                .totalItems()).isEqualTo(8);
        assertThat(coursePackageRepository.findByContentStatus(ContentStatus.SUBMITTED))
                .extracting("title").containsExactly("Chinese Culture and Festivals");
        // The new class with its lesson, and one lesson revision, wait for review; the other revision has changes requested.
        assertThat(reviews.waitingCount()).isEqualTo(2);
        long lessons = lessonRepository.count();
        long classes = coursePackageRepository.count();
        long enrolments = enrolmentRepository.count();

        demoContentSeeder.run();
        demoClassesSeeder.run();

        assertThat(lessonRepository.count()).isEqualTo(lessons);
        assertThat(coursePackageRepository.count()).isEqualTo(classes);
        assertThat(enrolmentRepository.count()).isEqualTo(enrolments);
    }
}
