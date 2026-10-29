package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.service.ContentVisibilityPolicy;
import com.example.aichinese.service.TeacherContentAccessService;

/**
 * Who may open what: a teacher only their own classes, units and lessons; a student only classes in the catalogue
 * (to preview and join) and, once joined, the class lessons. Archived classes stay open to students who joined.
 */
@Service
public class ClassAccessService {

    private static final String JOIN_FIRST = "Join this class to open its lessons.";
    private static final String UNIT_NOT_FOUND = "Unit was not found.";

    private final TeacherContentAccessService teacherContentAccessService;
    private final CoursePackageRepository coursePackageRepository;
    private final LearningModuleRepository learningModuleRepository;
    private final LessonRepository lessonRepository;
    private final StudentLearningAccessService studentAccess;
    private final ClassEnrolmentRepository enrolmentRepository;
    private final ClassContentSupport content;

    public ClassAccessService(
            TeacherContentAccessService teacherContentAccessService,
            CoursePackageRepository coursePackageRepository,
            LearningModuleRepository learningModuleRepository,
            LessonRepository lessonRepository,
            StudentLearningAccessService studentAccess,
            ClassEnrolmentRepository enrolmentRepository,
            ClassContentSupport content) {
        this.teacherContentAccessService = teacherContentAccessService;
        this.coursePackageRepository = coursePackageRepository;
        this.learningModuleRepository = learningModuleRepository;
        this.lessonRepository = lessonRepository;
        this.studentAccess = studentAccess;
        this.enrolmentRepository = enrolmentRepository;
        this.content = content;
    }

    // ----- teachers -----

    @Transactional(readOnly = true)
    public TeacherProfile requireTeacher(SecurityUserDetails currentUser) {
        return teacherContentAccessService.requireApprovedTeacher(currentUser);
    }

    @Transactional(readOnly = true)
    public CoursePackage requireOwnedClass(SecurityUserDetails currentUser, Long classId) {
        return coursePackageRepository.findByIdAndTeacherUserId(classId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ClassContentSupport.CLASS_NOT_FOUND));
    }

    /** A unit of one of the teacher's classes, with that class. */
    @Transactional(readOnly = true)
    public OwnedUnit requireOwnedUnit(SecurityUserDetails currentUser, Long unitId) {
        LearningModule unit = learningModuleRepository.findByIdAndTeacherUserId(unitId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, UNIT_NOT_FOUND));
        CoursePackage coursePackage = content.classOfUnit(unit.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, UNIT_NOT_FOUND));
        return new OwnedUnit(coursePackage, unit);
    }

    public record OwnedUnit(CoursePackage coursePackage, LearningModule unit) {
    }

    /** A lesson of one of the teacher's classes (never a revision copy; the editor addresses the lesson itself). */
    @Transactional(readOnly = true)
    public Lesson requireOwnedLesson(SecurityUserDetails currentUser, Long lessonId) {
        return lessonRepository.findByIdAndTeacherUserId(lessonId, currentUser.getUserId())
                .filter(lesson -> lesson.getRevisionOf() == null)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ClassContentSupport.LESSON_NOT_FOUND));
    }

    // ----- students -----

    public StudentProfile requireStudent(SecurityUserDetails currentUser) {
        return studentAccess.requireStudent(currentUser);
    }

    /** A class in the catalogue: published, by an approved and active teacher. */
    public static boolean isInCatalogue(CoursePackage coursePackage) {
        return ContentVisibilityPolicy.isPackageVisible(coursePackage);
    }

    /** A class its students may still work in: published, or archived after it was published. */
    public static boolean isOpenToJoinedStudents(CoursePackage coursePackage) {
        boolean wasPublished = coursePackage.getContentStatus() == ContentStatus.PUBLISHED
                || (coursePackage.getContentStatus() == ContentStatus.ARCHIVED
                        && coursePackage.getStatusBeforeArchive() == ContentStatus.PUBLISHED);
        return wasPublished && ContentVisibilityPolicy.isActiveApprovedTeacher(coursePackage.getTeacher());
    }

    /** A class the student may see: in the catalogue, or still open to them because they joined it. */
    @Transactional(readOnly = true)
    public CoursePackage requireVisibleClass(StudentProfile student, Long classId) {
        CoursePackage coursePackage = coursePackageRepository.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ClassContentSupport.CLASS_NOT_FOUND));
        if (isInCatalogue(coursePackage)
                || (isOpenToJoinedStudents(coursePackage) && isJoined(student, coursePackage))) {
            return coursePackage;
        }
        throw new ResponseStatusException(NOT_FOUND, ClassContentSupport.CLASS_NOT_FOUND);
    }

    @Transactional(readOnly = true)
    public CoursePackage requireJoinedClass(StudentProfile student, Long classId) {
        CoursePackage coursePackage = requireVisibleClass(student, classId);
        if (!isJoined(student, coursePackage)) {
            throw new ResponseStatusException(CONFLICT, JOIN_FIRST);
        }
        return coursePackage;
    }

    public boolean isJoined(StudentProfile student, CoursePackage coursePackage) {
        return enrolmentRepository.existsByStudentIdAndCoursePackageId(student.getId(), coursePackage.getId());
    }

    /**
     * A lesson of a class the student joined, published or archived (an archived lesson shows that it is no longer
     * available). Drafts, lessons in review and revision copies are never returned.
     */
    @Transactional(readOnly = true)
    public StudentLesson requireStudentLesson(StudentProfile student, Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .filter(found -> found.getRevisionOf() == null)
                .filter(found -> found.getContentStatus() == ContentStatus.PUBLISHED
                        || (found.getContentStatus() == ContentStatus.ARCHIVED
                                && found.getStatusBeforeArchive() == ContentStatus.PUBLISHED))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ClassContentSupport.LESSON_NOT_FOUND));
        CoursePackage coursePackage = content.requireClassOfLesson(lesson);
        requireJoinedClass(student, coursePackage.getId());
        return new StudentLesson(coursePackage, lesson);
    }

    /** A lesson the student may work in now (its class joined, the lesson published). */
    @Transactional(readOnly = true)
    public StudentLesson requireOpenLesson(StudentProfile student, Long lessonId) {
        StudentLesson found = requireStudentLesson(student, lessonId);
        if (!ClassContentSupport.isLessonLive(found.lesson())) {
            throw new ResponseStatusException(NOT_FOUND, ClassContentSupport.LESSON_NOT_FOUND);
        }
        return found;
    }

    public record StudentLesson(CoursePackage coursePackage, Lesson lesson) {
    }
}
