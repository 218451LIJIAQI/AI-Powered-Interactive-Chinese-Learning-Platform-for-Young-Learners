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

/**
 * Playable video asset attached to a {@link Lesson}.
 *
 * <p>A lesson may carry several videos (intro, main lecture, summary,
 * supplementary) ordered by {@link #sortOrder}. The asset itself comes
 * from one of two sources, captured by {@link #sourceType}:</p>
 * <ul>
 *   <li>{@link com.example.aichinese.entity.enums.RecordedVideoSourceType#UPLOADED_FILE}
 *       — points at a platform-managed {@link UploadedFile}; playback
 *       is served via signed URLs.</li>
 *   <li>{@link com.example.aichinese.entity.enums.RecordedVideoSourceType#EXTERNAL_URL}
 *       — points at a third-party hosted video via {@link #externalVideoUrl};
 *       treated as untrusted external content for safety filtering.</li>
 * </ul>
 *
 * <p>{@link #coursePackage} is an optional denormalized back-reference
 * useful for "package-only" supplementary clips that aren't anchored to
 * a single lesson.</p>
 *
 * <p>{@link #active} provides soft-deletion semantics: setting to
 * {@code false} removes the video from public listings without
 * destroying the {@code VideoProgress} rows that reference it.</p>
 */
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
        // Single source of truth for the eager-fetch attribute set used
        // by every read in RecordedLessonVideoRepository: teacher and
        // student video lists, the IDOR-safe single-row reads, and the
        // student-visible paywall-aware query all need lesson +
        // lesson.teacher + lesson.teacher.user + coursePackage +
        // uploadedFile hydrated in one round trip. The
        // lesson.teacher.user nested fetch is declared as a sub-graph
        // so the response mapper can render the teacher's display name
        // without an N+1 lazy load.
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

    /**
     * Name of the {@link NamedEntityGraph} that eagerly fetches the
     * standard attribute set required by every read endpoint that
     * surfaces a {@code RecordedLessonVideo}: {@code lesson} (with
     * nested {@code lesson.teacher} and {@code lesson.teacher.user}),
     * {@code coursePackage} and {@code uploadedFile}.
     */
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
