package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.ClassEnrolment;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonRepository;

import lombok.RequiredArgsConstructor;

/**
 * What a learner may open: classes in the catalogue (to preview and join) and, once joined, the class lessons. An
 * archived class stays open to the learners who joined it, read-only: its lessons and their results.
 */
@Service
@RequiredArgsConstructor
public class ClassAccessService {

    static final String CLASS_ARCHIVED_READ_ONLY =
            "This class is archived. You can still open its lessons and your results.";

    private final CoursePackageRepository coursePackageRepository;
    private final LessonRepository lessonRepository;
    private final ClassEnrolmentRepository enrolmentRepository;
    private final ClassContentSupport content;

    private static boolean isOrWasPublished(ContentStatus current, ContentStatus beforeArchive) {
        return current == ContentStatus.PUBLISHED
                || (current == ContentStatus.ARCHIVED && beforeArchive == ContentStatus.PUBLISHED);
    }

    private static boolean isOpenToJoinedStudents(CoursePackage coursePackage) {
        return isOrWasPublished(coursePackage.getContentStatus(), coursePackage.getStatusBeforeArchive());
    }

    EnrolmentState lockForJoin(StudentProfile student, Long classId) {
        EnrolmentState membership = lockEnrolment(student, classId);
        if (membership.existing() == null && !coursePackageRepository.existsInCatalogue(List.of(classId))) {
            throw new ResponseStatusException(NOT_FOUND, ClassContentSupport.CLASS_NOT_FOUND);
        }
        return membership;
    }

    /**
     * The caller holds the learner lock. The shared class lock keeps publication state stable through a join or leave.
     */
    EnrolmentState lockEnrolment(StudentProfile student, Long classId) {
        CoursePackage coursePackage = coursePackageRepository.findForEnrolment(classId)
                .filter(ClassAccessService::isOpenToJoinedStudents)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ClassContentSupport.CLASS_NOT_FOUND));
        ClassEnrolment existing = enrolmentRepository.findByStudentIdAndCoursePackageId(student.getId(), classId)
                .orElse(null);
        return new EnrolmentState(coursePackage, existing);
    }

    boolean hasJoined(StudentProfile student, CoursePackage coursePackage) {
        return isOpenToJoinedStudents(coursePackage)
                && enrolmentRepository.existsByStudentIdAndCoursePackageId(student.getId(), coursePackage.getId());
    }

    /**
     * A lesson of a class the learner joined, published or archived (an archived lesson shows that it is no longer
     * available). Drafts and working copies are never returned.
     */
    @Transactional(readOnly = true)
    public StudentLesson requireStudentLesson(StudentProfile student, Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .filter(ClassAccessService::isVisibleToJoinedStudents)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ClassContentSupport.LESSON_NOT_FOUND));
        List<CoursePackage> parents = content.classesOfLesson(lesson);
        if (parents.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, ClassContentSupport.LESSON_NOT_FOUND);
        }
        List<Long> classIds = parents.stream().map(CoursePackage::getId).toList();
        Set<Long> joinedIds = enrolmentRepository.findJoinedClassIds(student.getId(), classIds);
        // Older units can belong to several classes. A joined, published class still permits learning.
        CoursePackage joined = parents.stream()
                .filter(ClassAccessService::isOpenToJoinedStudents)
                .filter(course -> joinedIds.contains(course.getId()))
                .min(Comparator.comparing(course -> course.getContentStatus() != ContentStatus.PUBLISHED))
                .orElse(null);
        if (joined != null) {
            return new StudentLesson(joined, lesson);
        }
        if (coursePackageRepository.existsInCatalogue(classIds)) {
            throw new ResponseStatusException(CONFLICT, "Join this class to open its lessons.");
        }
        throw new ResponseStatusException(NOT_FOUND, ClassContentSupport.CLASS_NOT_FOUND);
    }

    /**
     * A lesson the learners of its class see: published, or archived after it was published (shown as no longer
     * available). Never a draft or a working copy.
     */
    static boolean isVisibleToJoinedStudents(Lesson lesson) {
        return lesson.getRevisionOf() == null && isOrWasPublished(lesson.getContentStatus(), lesson.getStatusBeforeArchive());
    }

    /**
     * A lesson the learner may work in now: its class joined and not archived (an archived class is read-only), the
     * lesson published.
     */
    @Transactional(readOnly = true)
    public StudentLesson requireOpenLesson(StudentProfile student, Long lessonId) {
        StudentLesson found = requireStudentLesson(student, lessonId);
        if (!ClassContentSupport.isLessonLive(found.lesson())) {
            throw new ResponseStatusException(NOT_FOUND, ClassContentSupport.LESSON_NOT_FOUND);
        }
        if (found.coursePackage().getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(CONFLICT, CLASS_ARCHIVED_READ_ONLY);
        }
        return found;
    }

    public record StudentLesson(CoursePackage coursePackage, Lesson lesson) {
    }

    record EnrolmentState(CoursePackage coursePackage, ClassEnrolment existing) {
    }
}
