package com.example.aichinese.service.classes;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.classes.TeacherClassViews;
import com.example.aichinese.entity.ClassContentEvent;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ClassContentEventType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.ClassContentEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Writes and reads the history of classes and their lessons (class History tab, admin review and details). */
@Service
public class ClassEventService {

    private static final Logger log = LoggerFactory.getLogger(ClassEventService.class);
    private static final TypeReference<List<TeacherClassViews.Change>> CHANGES = new TypeReference<>() {
    };

    private final ClassContentEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public ClassEventService(ClassContentEventRepository eventRepository, ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(
            CoursePackage coursePackage,
            Lesson lesson,
            User actor,
            ClassContentEventType type,
            String note,
            List<TeacherClassViews.Change> changes) {
        ClassContentEvent event = new ClassContentEvent();
        event.setCoursePackage(coursePackage);
        event.setLesson(lesson);
        event.setActor(actor);
        event.setEventType(type);
        event.setSubjectTitle(lesson != null ? lesson.getTitle() : coursePackage.getTitle());
        event.setNote(note);
        event.setChanges(changes == null || changes.isEmpty() ? null : write(changes));
        eventRepository.save(event);
    }

    @Transactional
    public void record(CoursePackage coursePackage, Lesson lesson, User actor, ClassContentEventType type) {
        record(coursePackage, lesson, actor, type, null, null);
    }

    @Transactional(readOnly = true)
    public List<TeacherClassViews.Event> classHistory(Long classId) {
        return eventRepository.findByCoursePackageIdOrderByCreatedAtDescIdDesc(classId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<TeacherClassViews.Event> lessonHistory(Long lessonId) {
        return eventRepository.findByLessonIdOrderByCreatedAtDescIdDesc(lessonId).stream().map(this::toView).toList();
    }

    /** History of the class itself (not its lessons). */
    @Transactional(readOnly = true)
    public List<TeacherClassViews.Event> classOnlyHistory(Long classId) {
        return eventRepository.findByCoursePackageIdOrderByCreatedAtDescIdDesc(classId).stream()
                .filter(event -> event.getLesson() == null)
                .map(this::toView)
                .toList();
    }

    /** The newest "changes requested" step of a history (newest first): who wrote the open review note, and when. */
    static TeacherClassViews.Event latestChangesRequested(List<TeacherClassViews.Event> history) {
        return history.stream().filter(event -> event.type().endsWith("CHANGES_REQUESTED")).findFirst().orElse(null);
    }

    private TeacherClassViews.Event toView(ClassContentEvent event) {
        User actor = event.getActor();
        String role = actor == null ? null : actor.getRoles().stream()
                .map(roleEntity -> roleEntity.getName().name())
                .filter(name -> name.equals(RoleName.ADMIN.name()))
                .findFirst()
                .orElse(RoleName.TEACHER.name());
        return new TeacherClassViews.Event(
                event.getId(),
                event.getEventType().name(),
                event.getSubjectTitle(),
                event.getLesson() == null ? null : event.getLesson().getId(),
                actor == null ? null : actor.getDisplayName(),
                role,
                event.getNote(),
                read(event.getChanges()),
                event.getCreatedAt());
    }

    private String write(List<TeacherClassViews.Change> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JsonProcessingException exception) {
            log.warn("Could not store the change list of a class event: {}", exception.getOriginalMessage());
            return null;
        }
    }

    private List<TeacherClassViews.Change> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, CHANGES);
        } catch (JsonProcessingException exception) {
            log.warn("Could not read the change list of a class event: {}", exception.getOriginalMessage());
            return List.of();
        }
    }
}
