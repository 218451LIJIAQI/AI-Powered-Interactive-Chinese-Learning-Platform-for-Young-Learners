package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.dto.content.LearningCategoryResponse;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningCategory;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.util.PaginationUtils;

import lombok.RequiredArgsConstructor;

/**
 * The class catalogue (public Classes page and the student's Find classes) and the class preview before joining:
 * published classes with their units and lesson titles. Lesson content opens only after joining.
 */
@Service
@RequiredArgsConstructor
public class ClassCatalogueService {

    private final ClassContentSupport content;
    private final CoursePackageRepository coursePackageRepository;
    private final LearningCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<LearningCategoryResponse> topics() {
        return List.copyOf(categoryRepository.findAllByOrderBySortOrderAscNameAsc());
    }

    /** The public catalogue page for visitors (no joined classes to exclude). */
    @Transactional(readOnly = true)
    public PageResponse<ClassViews.ClassCard> publicClasses(int page, int size) {
        return search(null, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClassViews.ClassCard> search(Long studentId, int page, int size) {
        Page<ClassViews.ClassCard> classes = PaginationUtils.readPage(PaginationUtils.pageRequest(page, size),
                request -> coursePackageRepository.findCatalogue(studentId, request));
        return new PageResponse<>(classes.getContent(), classes.getNumber(), classes.getSize(),
                classes.getTotalElements());
    }

    /** Every class in the catalogue, newest first: what a learning plan can choose from. */
    @Transactional(readOnly = true)
    public List<ClassViews.ClassCard> allCards() {
        return coursePackageRepository.findCatalogue(null, Pageable.unpaged()).getContent();
    }

    /** Only the classes referenced by a stored plan that are still in the catalogue. */
    @Transactional(readOnly = true)
    public List<ClassViews.ClassCard> cardsFor(Set<Long> classIds) {
        return classIds.isEmpty() ? List.of() : coursePackageRepository.findCatalogueByIds(classIds);
    }

    /** The public class page, of a class in the catalogue. */
    @Transactional(readOnly = true)
    public ClassViews.ClassPreview preview(Long classId) {
        return preview(content.requireClass(classId));
    }

    /** The preview of a class in the catalogue (the public class page, a learner's before joining); else not found. */
    ClassViews.ClassPreview preview(CoursePackage coursePackage) {
        if (coursePackage.getContentStatus() != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(NOT_FOUND, ClassContentSupport.CLASS_NOT_FOUND);
        }
        List<LearningModule> units = content.units(coursePackage);
        Map<Long, List<Lesson>> lessonsByUnit = content.lessonsByUnit(units);
        List<ClassViews.UnitPreview> result = new ArrayList<>();
        for (LearningModule unit : units) {
            List<ClassViews.LessonPreview> lessons = lessonsByUnit.getOrDefault(unit.getId(), List.of()).stream()
                    .filter(ClassContentSupport::isLessonLive)
                    .map(lesson -> new ClassViews.LessonPreview(lesson.getId(), lesson.getTitle()))
                    .toList();
            if (!lessons.isEmpty()) {
                result.add(new ClassViews.UnitPreview(unit.getId(), unit.getTitle(), lessons));
            }
        }
        if (result.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, ClassContentSupport.CLASS_NOT_FOUND);
        }
        int lessonCount = result.stream().mapToInt(unit -> unit.lessons().size()).sum();
        return new ClassViews.ClassPreview(card(coursePackage, lessonCount), result);
    }

    static ClassViews.ClassCard card(CoursePackage coursePackage, int lessonCount) {
        LearningCategory topic = coursePackage.getCategory();
        return new ClassViews.ClassCard(coursePackage.getId(), coursePackage.getTitle(), coursePackage.getDescription(),
                topic == null ? null : topic.getName(), coursePackage.getBestAfterUnit(), lessonCount);
    }

    /** The number of classes a learner can still find, without building their catalogue cards. */
    long countFor(Long studentId) {
        return coursePackageRepository.countCatalogue(studentId);
    }
}
