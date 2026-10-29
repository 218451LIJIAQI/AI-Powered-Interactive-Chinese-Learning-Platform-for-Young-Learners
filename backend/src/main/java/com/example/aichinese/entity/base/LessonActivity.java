package com.example.aichinese.entity.base;

import com.example.aichinese.entity.Lesson;

/**
 * A practice activity that belongs to a class lesson (flashcards, character tracing or matching). The lesson's
 * activities are listed in {@link #getLessonPosition()} order; an activity of a lesson revision remembers the
 * published activity it was copied from, so approval updates that activity and keeps students' completions.
 */
public interface LessonActivity {

    Long getId();

    String getTitle();

    Lesson getLesson();

    void setLesson(Lesson lesson);

    Integer getLessonPosition();

    void setLessonPosition(Integer lessonPosition);

    Long getRevisionSourceId();

    void setRevisionSourceId(Long revisionSourceId);
}
