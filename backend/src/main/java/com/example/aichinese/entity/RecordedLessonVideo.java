package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.RecordedVideoSourceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.NamedSubgraph;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "recorded_lesson_videos", indexes = {
        @Index(name = "idx_recorded_videos_lesson_id", columnList = "lesson_id"),
        @Index(name = "idx_recorded_videos_package_id", columnList = "course_package_id"),
        @Index(name = "idx_recorded_videos_uploaded_file_id", columnList = "uploaded_file_id"),
        @Index(name = "idx_recorded_videos_active", columnList = "active")
})
@NamedEntityGraph(
        name = RecordedLessonVideo.GRAPH_DETAIL,
        attributeNodes = {
                @NamedAttributeNode(value = "lesson", subgraph = "RecordedLessonVideo.lessonWithTeacher"),
                @NamedAttributeNode("coursePackage"),
                @NamedAttributeNode("uploadedFile")
        },
        subgraphs = {
                @NamedSubgraph(
                        name = "RecordedLessonVideo.lessonWithTeacher",
                        attributeNodes = @NamedAttributeNode(value = "teacher", subgraph = "RecordedLessonVideo.teacherWithUser")),
                @NamedSubgraph(
                        name = "RecordedLessonVideo.teacherWithUser",
                        attributeNodes = @NamedAttributeNode("user"))
        }
)
public class RecordedLessonVideo extends BaseEntity {

    public static final String GRAPH_DETAIL = "RecordedLessonVideo.detail";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_package_id")
    private CoursePackage coursePackage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_file_id")
    private UploadedFile uploadedFile;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private RecordedVideoSourceType sourceType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "external_video_url", length = 500)
    private String externalVideoUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
