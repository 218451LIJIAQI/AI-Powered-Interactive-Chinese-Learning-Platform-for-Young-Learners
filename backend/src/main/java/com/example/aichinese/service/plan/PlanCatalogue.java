package com.example.aichinese.service.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.dto.learn.LearnPathResponse;
import com.example.aichinese.entity.ClassEnrolment;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.service.classes.ClassAccessService;
import com.example.aichinese.service.classes.ClassCatalogueService;
import com.example.aichinese.service.learn.LearnPathModel.StopState;
import com.example.aichinese.service.learn.LearnPathModel.UnitState;
import com.example.aichinese.service.learn.LearnPathService;

/**
 * What a plan can be built from: the published Learn units as this student sees them (done, active or locked, with the
 * minutes still to do) and the published classes, marking the ones the student joined. Nothing outside this catalogue
 * can become a plan step.
 */
@Component
public class PlanCatalogue {

    private static final int CATALOGUE_PAGE_SIZE = 100;
    private static final int MAX_CATALOGUE_PAGES = 10;

    private final LearnPathService learnPathService;
    private final ClassCatalogueService classCatalogueService;
    private final ClassEnrolmentRepository enrolmentRepository;

    public PlanCatalogue(
            LearnPathService learnPathService,
            ClassCatalogueService classCatalogueService,
            ClassEnrolmentRepository enrolmentRepository) {
        this.learnPathService = learnPathService;
        this.classCatalogueService = classCatalogueService;
        this.enrolmentRepository = enrolmentRepository;
    }

    /** A Learn unit; {@code text} is what goals are matched against (titles of the unit and its lessons). */
    public record Unit(
            Long id,
            int number,
            int level,
            String title,
            String titleZh,
            int hskLevel,
            String iconKey,
            UnitState state,
            int lessonCount,
            int completedCount,
            int remainingMinutes,
            List<LearnPathResponse.Stop> lessons,
            String text) {

        public Unit {
            lessons = lessons == null ? List.of() : List.copyOf(lessons);
        }
    }

    /** A published class; {@code joined} when the student is already in it ("In My classes"). */
    public record ClassOption(
            Long id,
            String title,
            String description,
            String levelName,
            String topicName,
            String teacherName,
            boolean joined,
            String text) {
    }

    public record Catalogue(List<Unit> units, List<ClassOption> classes, LearnPathResponse path) {

        public Catalogue {
            units = units == null ? List.of() : List.copyOf(units);
            classes = classes == null ? List.of() : List.copyOf(classes);
        }

        public Optional<Unit> unit(Long id) {
            return units.stream().filter(unit -> unit.id().equals(id)).findFirst();
        }

        public Optional<ClassOption> classOption(Long id) {
            return classes.stream().filter(option -> option.id().equals(id)).findFirst();
        }
    }

    @Transactional(readOnly = true)
    public Catalogue of(StudentProfile student) {
        LearnPathResponse path = learnPathService.path(student);
        return new Catalogue(units(path), classes(student), path);
    }

    static List<Unit> units(LearnPathResponse path) {
        List<Unit> units = new ArrayList<>();
        for (LearnPathResponse.Level level : path.levels()) {
            for (LearnPathResponse.Unit unit : level.units()) {
                int remaining = unit.lessons().stream()
                        .filter(stop -> stop.state() != StopState.DONE)
                        .mapToInt(LearnPathResponse.Stop::minutes)
                        .sum();
                String text = Stream.concat(
                                Stream.of(unit.title(), unit.titleZh()),
                                unit.lessons().stream().map(LearnPathResponse.Stop::title))
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(" · "));
                units.add(new Unit(unit.id(), unit.number(), level.level(), unit.title(), unit.titleZh(),
                        unit.hskLevel(), unit.iconKey(), unit.state(), unit.lessonCount(), unit.completedCount(),
                        remaining, unit.lessons(), normalize(text)));
            }
        }
        return units;
    }

    private List<ClassOption> classes(StudentProfile student) {
        Set<Long> joined = enrolmentRepository.findByStudentIdOrderByJoinedAtDesc(student.getId()).stream()
                .filter(enrolment -> ClassAccessService.isOpenToJoinedStudents(enrolment.getCoursePackage()))
                .map(ClassEnrolment::getCoursePackage)
                .map(coursePackage -> coursePackage.getId())
                .collect(Collectors.toSet());
        Map<Long, ClassOption> options = new LinkedHashMap<>();
        for (ClassViews.ClassCard card : publishedCards()) {
            String text = Stream.of(card.title(), card.description(), card.topicName(), card.levelName())
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" · "));
            options.put(card.id(), new ClassOption(card.id(), card.title(), card.description(), card.levelName(),
                    card.topicName(), card.teacher() == null ? null : card.teacher().name(), joined.contains(card.id()),
                    normalize(text)));
        }
        return List.copyOf(options.values());
    }

    /** Every class in the catalogue (published, by an approved teacher), newest first. */
    private List<ClassViews.ClassCard> publishedCards() {
        List<ClassViews.ClassCard> cards = new ArrayList<>();
        ClassCatalogueService.Query query = new ClassCatalogueService.Query(null, null, null, null, null);
        for (int page = 0; page < MAX_CATALOGUE_PAGES; page++) {
            PageResponse<ClassViews.ClassCard> result = classCatalogueService.search(query, Set.of(), page, CATALOGUE_PAGE_SIZE);
            cards.addAll(result.items());
            if (result.items().size() < CATALOGUE_PAGE_SIZE) {
                break;
            }
        }
        return cards;
    }

    static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
