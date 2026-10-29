package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.dto.classes.LiveSessionViews;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningCategory;
import com.example.aichinese.entity.LearningLevel;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.LearningLevelRepository;

/**
 * The class catalogue (public Classes page, landing page featured classes, the student's Find classes) and the
 * class preview before joining: published classes of approved teachers with their units and lesson titles. Lesson
 * content opens only after joining.
 */
@Service
public class ClassCatalogueService {

    private static final int FEATURED_COUNT = 3;

    private final ClassContentSupport content;
    private final ClassLiveSessionService liveSessions;
    private final CoursePackageRepository coursePackageRepository;
    private final LearningLevelRepository levelRepository;
    private final LearningCategoryRepository categoryRepository;

    public ClassCatalogueService(
            ClassContentSupport content,
            ClassLiveSessionService liveSessions,
            CoursePackageRepository coursePackageRepository,
            LearningLevelRepository levelRepository,
            LearningCategoryRepository categoryRepository) {
        this.content = content;
        this.liveSessions = liveSessions;
        this.coursePackageRepository = coursePackageRepository;
        this.levelRepository = levelRepository;
        this.categoryRepository = categoryRepository;
    }

    /** Filters of the catalogue; {@code sort}: RECENT (default) or TITLE. */
    public record Query(String keyword, Long topicId, Long levelId, String learningType, String sort) {
    }

    @Transactional(readOnly = true)
    public PageResponse<ClassViews.ClassCard> search(Query query, Set<Long> excludedClassIds, int page, int size) {
        List<CoursePackage> classes = filter(catalogueClasses(), query).stream()
                .filter(coursePackage -> !excludedClassIds.contains(coursePackage.getId()))
                .toList();
        return PageResponse.fromList(classes, page, size, this::cards);
    }

    /** The topics and levels the catalogue can be filtered by, in their display order. */
    @Transactional(readOnly = true)
    public ClassViews.CatalogueSummary summary() {
        List<ClassViews.Option> topics = categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(LearningCategory::getSortOrder))
                .map(topic -> new ClassViews.Option(topic.getId(), topic.getName()))
                .toList();
        List<ClassViews.Option> levels = levelRepository.findAll().stream()
                .sorted(Comparator.comparing(LearningLevel::getSortOrder))
                .map(level -> new ClassViews.Option(level.getId(), level.getName()))
                .toList();
        return new ClassViews.CatalogueSummary(topics, levels);
    }

    /** Three classes for the landing page: the ones with the most lessons, newest first on a tie. */
    @Transactional(readOnly = true)
    public List<ClassViews.ClassCard> featured() {
        List<ClassViews.ClassCard> cards = new ArrayList<>(cards(catalogueClasses()));
        cards.sort(Comparator.comparingInt(ClassViews.ClassCard::lessonCount).reversed());
        return cards.stream().limit(FEATURED_COUNT).toList();
    }

    /** The public class page. */
    @Transactional(readOnly = true)
    public ClassViews.ClassPreview preview(Long classId) {
        CoursePackage coursePackage = coursePackageRepository.findById(classId)
                .filter(ClassAccessService::isInCatalogue)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ClassContentSupport.CLASS_NOT_FOUND));
        return new ClassViews.ClassPreview(
                card(coursePackage),
                coursePackage.getTeacherNote(),
                unitPreviews(coursePackage),
                liveSessions.upcomingByClass(List.of(classId), null).getOrDefault(classId, List.of()));
    }

    List<ClassViews.UnitPreview> unitPreviews(CoursePackage coursePackage) {
        List<LearningModule> units = content.units(coursePackage, false);
        Map<Long, List<Lesson>> lessonsByUnit = content.lessonsByUnit(units);
        List<Long> lessonIds = lessonsByUnit.values().stream().flatMap(List::stream)
                .filter(ClassContentSupport::isLessonLive)
                .map(Lesson::getId)
                .toList();
        Map<Long, List<LessonActivity>> activities = content.activitiesByLesson(lessonIds);
        Map<Long, List<Quiz>> quizzes = content.quizzesByLesson(lessonIds);
        List<ClassViews.UnitPreview> result = new ArrayList<>();
        for (LearningModule unit : units) {
            List<ClassViews.LessonPreview> lessons = lessonsByUnit.getOrDefault(unit.getId(), List.of()).stream()
                    .filter(ClassContentSupport::isLessonLive)
                    .map(lesson -> new ClassViews.LessonPreview(
                            lesson.getId(),
                            lesson.getTitle(),
                            (int) activities.getOrDefault(lesson.getId(), List.of()).stream().filter(ClassContentSupport::isLive).count(),
                            (int) quizzes.getOrDefault(lesson.getId(), List.of()).stream().filter(ClassContentSupport::isLive).count()))
                    .toList();
            if (!lessons.isEmpty()) {
                result.add(new ClassViews.UnitPreview(unit.getId(), unit.getTitle(), lessons));
            }
        }
        return result;
    }

    // ----- cards -----

    ClassViews.ClassCard card(CoursePackage coursePackage) {
        return cards(List.of(coursePackage)).get(0);
    }

    /** Cards for the classes, with their unit, lesson and upcoming live session counts read in batches. */
    List<ClassViews.ClassCard> cards(Collection<CoursePackage> classes) {
        if (classes.isEmpty()) {
            return List.of();
        }
        Map<Long, List<LearningModule>> units = content.unitsByClass(classes, false);
        Map<Long, List<Lesson>> lessonsByUnit = content.lessonsByUnit(units.values().stream().flatMap(List::stream).toList());
        Map<Long, List<LiveSessionViews.SessionBrief>> sessions = liveSessions.upcomingByClass(
                classes.stream().map(CoursePackage::getId).toList(), null);
        List<ClassViews.ClassCard> cards = new ArrayList<>();
        for (CoursePackage coursePackage : classes) {
            List<LearningModule> classUnits = units.getOrDefault(coursePackage.getId(), List.of());
            int lessonCount = 0;
            int unitCount = 0;
            for (LearningModule unit : classUnits) {
                long live = lessonsByUnit.getOrDefault(unit.getId(), List.of()).stream()
                        .filter(ClassContentSupport::isLessonLive)
                        .count();
                lessonCount += (int) live;
                unitCount += live > 0 ? 1 : 0;
            }
            cards.add(new ClassViews.ClassCard(
                    coursePackage.getId(),
                    coursePackage.getTitle(),
                    coursePackage.getDescription(),
                    TeacherClassService.levelName(coursePackage.getLevel()),
                    TeacherClassService.topicName(coursePackage.getCategory()),
                    coursePackage.getLearningType(),
                    teacher(coursePackage.getTeacher()),
                    unitCount,
                    lessonCount,
                    sessions.getOrDefault(coursePackage.getId(), List.of()).size()));
        }
        return cards;
    }

    static ClassViews.TeacherView teacher(TeacherProfile teacher) {
        return new ClassViews.TeacherView(
                teacher.getId(),
                teacher.getUser().getDisplayName(),
                teacher.getTeacherStatus() == TeacherStatus.APPROVED,
                teacher.getBio());
    }

    /** Published classes of approved, active teachers, newest first. */
    List<CoursePackage> catalogueClasses() {
        return coursePackageRepository.findByContentStatus(ContentStatus.PUBLISHED).stream()
                .filter(ClassAccessService::isInCatalogue)
                .sorted(Comparator.comparing(CoursePackage::getCreatedAt).reversed().thenComparing(CoursePackage::getId, Comparator.reverseOrder()))
                .toList();
    }

    private static List<CoursePackage> filter(List<CoursePackage> classes, Query query) {
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().strip().toLowerCase(Locale.ROOT);
        LearningType learningType = null;
        if (query.learningType() != null && !query.learningType().isBlank()) {
            learningType = LearningType.tryFrom(query.learningType().strip().toUpperCase(Locale.ROOT))
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Learning type must be RECORDED, LIVE or HYBRID."));
        }
        LearningType type = learningType;
        List<CoursePackage> filtered = classes.stream()
                .filter(coursePackage -> keyword == null
                        || coursePackage.getTitle().toLowerCase(Locale.ROOT).contains(keyword)
                        || coursePackage.getTeacher().getUser().getDisplayName().toLowerCase(Locale.ROOT).contains(keyword))
                .filter(coursePackage -> query.topicId() == null
                        || (coursePackage.getCategory() != null && query.topicId().equals(coursePackage.getCategory().getId())))
                .filter(coursePackage -> query.levelId() == null
                        || (coursePackage.getLevel() != null && query.levelId().equals(coursePackage.getLevel().getId())))
                .filter(coursePackage -> type == null || coursePackage.getLearningType() == type)
                .collect(Collectors.toCollection(ArrayList::new));
        String sort = query.sort() == null ? "RECENT" : query.sort().strip().toUpperCase(Locale.ROOT);
        if (sort.equals("TITLE")) {
            filtered.sort(Comparator.comparing(CoursePackage::getTitle, String.CASE_INSENSITIVE_ORDER));
        } else if (!sort.equals("RECENT")) {
            throw new ResponseStatusException(BAD_REQUEST, "Sort must be RECENT or TITLE.");
        }
        return filtered;
    }
}
