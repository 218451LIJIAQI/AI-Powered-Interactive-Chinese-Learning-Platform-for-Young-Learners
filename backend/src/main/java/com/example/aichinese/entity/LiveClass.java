package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.LiveClassStatus;
import com.example.aichinese.entity.enums.MeetingPlatform;

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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Scheduled synchronous teaching session conducted on an external
 * meeting platform.
 *
 * <p>Either {@link #lesson} or {@link #coursePackage} (or neither, for
 * standalone sessions) anchors the live class to learning content.
 * {@link #scheduledStartAt} / {@link #scheduledEndAt} drive the
 * student-facing calendar; {@link #meetingLink} carries the join URL.</p>
 *
 * <p>Lifecycle is governed by
 * {@link com.example.aichinese.entity.enums.LiveClassStatus}:
 * {@code SCHEDULED → ONGOING → COMPLETED|CANCELLED|RESCHEDULED}. The
 * {@code LiveLearningAccessService} only allows joins when the status
 * is {@code SCHEDULED} or {@code ONGOING}.</p>
 *
 * <p>{@link #version} prevents two admins from simultaneously
 * cancelling and rescheduling the same session.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "live_classes", indexes = {
        @Index(name = "idx_live_classes_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_live_classes_lesson_id", columnList = "lesson_id"),
        @Index(name = "idx_live_classes_status", columnList = "live_class_status"),
        @Index(name = "idx_live_classes_scheduled_start_at", columnList = "scheduled_start_at")
})
@NamedEntityGraph(
        // Single source of truth for the eager-fetch attribute set used
        // by every read in LiveClassRepository: teacher dashboards,
        // student catalogue and admin schedule view all need teacher /
        // teacher.user / lesson / lesson.teacher / coursePackage /
        // coursePackage.teacher hydrated in one round trip. Three
        // distinct sub-graphs declare the nested ManyToOne fetches so
        // the response mappers do not regress to the per-row lazy-load
        // N+1 pattern when rendering parent-artefact metadata.
        name = LiveClass.GRAPH_DETAIL,
        attributeNodes = {
                @NamedAttributeNode(value = "teacher", subgraph = "LiveClass.teacherWithUser"),
                @NamedAttributeNode(value = "lesson", subgraph = "LiveClass.lessonWithTeacher"),
                @NamedAttributeNode(value = "coursePackage", subgraph = "LiveClass.packageWithTeacher")
        },
        subgraphs = {
                @NamedSubgraph(
                        name = "LiveClass.teacherWithUser",
                        attributeNodes = @NamedAttributeNode("user")),
                @NamedSubgraph(
                        name = "LiveClass.lessonWithTeacher",
                        attributeNodes = @NamedAttributeNode("teacher")),
                @NamedSubgraph(
                        name = "LiveClass.packageWithTeacher",
                        attributeNodes = @NamedAttributeNode("teacher"))
        }
)
public class LiveClass extends BaseEntity {

    /**
     * Name of the {@link NamedEntityGraph} that eagerly fetches the
     * standard attribute set required by every read endpoint that
     * surfaces a {@code LiveClass}: {@code teacher} (with nested
     * {@code teacher.user}), {@code lesson} (with nested
     * {@code lesson.teacher}) and {@code coursePackage} (with nested
     * {@code coursePackage.teacher}).
     */
    public static final String GRAPH_DETAIL = "LiveClass.detail";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private TeacherProfile teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_package_id")
    private CoursePackage coursePackage;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_platform", nullable = false, length = 40)
    private MeetingPlatform meetingPlatform = MeetingPlatform.OTHER;

    @Column(name = "scheduled_start_at")
    private LocalDateTime scheduledStartAt;

    @Column(name = "scheduled_end_at")
    private LocalDateTime scheduledEndAt;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = "Asia/Singapore";

    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "live_class_status", nullable = false, length = 30)
    private LiveClassStatus liveClassStatus = LiveClassStatus.SCHEDULED;
}
