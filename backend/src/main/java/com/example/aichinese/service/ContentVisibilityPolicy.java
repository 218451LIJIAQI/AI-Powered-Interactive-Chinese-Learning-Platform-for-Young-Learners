package com.example.aichinese.service;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.TeacherStatus;

/**
 * The single student-visibility rule set (FRCONT06 and FRTVER04).
 *
 * <ul>
 *   <li>Only PUBLISHED items are visible.</li>
 *   <li>A lesson also needs its module (when it has one) to be PUBLISHED, so archiving or
 *       re-reviewing a module hides its lessons.</li>
 *   <li>The owning teacher must be APPROVED and the account active, so rejecting or deactivating a
 *       teacher hides their content from students without deleting it.</li>
 * </ul>
 */
public final class ContentVisibilityPolicy {

    private ContentVisibilityPolicy() {
    }

    public static boolean isActiveApprovedTeacher(TeacherProfile teacher) {
        return teacher != null
                && teacher.getTeacherStatus() == TeacherStatus.APPROVED
                && teacher.getUser() != null
                && teacher.getUser().isActive();
    }

    public static boolean isLessonVisible(Lesson lesson) {
        if (lesson == null || lesson.getContentStatus() != ContentStatus.PUBLISHED) {
            return false;
        }
        LearningModule module = lesson.getModule();
        if (module != null && module.getContentStatus() != ContentStatus.PUBLISHED) {
            return false;
        }
        return isActiveApprovedTeacher(lesson.getTeacher());
    }

    public static boolean isPackageVisible(CoursePackage coursePackage) {
        return coursePackage != null
                && coursePackage.getContentStatus() == ContentStatus.PUBLISHED
                && isActiveApprovedTeacher(coursePackage.getTeacher());
    }

    /**
     * The lesson and course a live class is linked to, where set, are visible. The class status, and whether
     * the class is linked to any content at all, are checked by the callers that need them.
     */
    public static boolean isLiveClassContentVisible(LiveClass liveClass) {
        return (liveClass.getLesson() == null || isLessonVisible(liveClass.getLesson()))
                && (liveClass.getCoursePackage() == null || isPackageVisible(liveClass.getCoursePackage()));
    }

    /** A recorded video needs a visible lesson and, when it is linked to a course, a visible course. */
    public static boolean isRecordedVideoVisible(RecordedLessonVideo video) {
        return isLessonVisible(video.getLesson())
                && (video.getCoursePackage() == null || isPackageVisible(video.getCoursePackage()));
    }
}
