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
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "live_classes", indexes = {
        @Index(name = "idx_live_classes_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_live_classes_lesson_id", columnList = "lesson_id"),
        @Index(name = "idx_live_classes_status", columnList = "live_class_status"),
        @Index(name = "idx_live_classes_scheduled_start_at", columnList = "scheduled_start_at")
})
@NamedEntityGraph(
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

    public static final String GRAPH_DETAIL = "LiveClass.detail";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
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

    /** Recording of a past session; only students who booked it can open it. */
    @Column(name = "recording_url", length = 500)
    private String recordingUrl;

    /** The start before the latest reschedule ("Moved from 2:00 PM"). */
    @Column(name = "rescheduled_from_start_at")
    private LocalDateTime rescheduledFromStartAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
}
