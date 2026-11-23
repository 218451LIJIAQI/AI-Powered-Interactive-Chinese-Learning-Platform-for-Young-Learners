package com.example.aichinese.service.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.dto.learn.LearnPathResponse;
import com.example.aichinese.dto.learn.StopState;
import com.example.aichinese.dto.learn.UnitState;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.service.classes.ClassCatalogueService;
import com.example.aichinese.service.learn.LearnPathService;

import lombok.RequiredArgsConstructor;

/**
 * What a plan can be built from: the published Learn units as this student sees them (done, active or locked, with the
 * minutes still to do) and the published classes, marking the ones the student joined. Nothing outside this catalogue
 * can become a plan step.
 */
@Component
@RequiredArgsConstructor
public class PlanCatalogue {

    private final LearnPathService learnPathService;
    private final ClassCatalogueService classCatalogueService;
    private final ClassEnrolmentRepository enrolmentRepository;

    /** A Learn unit; {@code text} is what goals are matched against (titles of the unit and its lessons). */
    public record Unit(
            Long id,
            int number,
            int level,
            String title,
            String titleZh,
            UnitState state,
            int lessonCount,
            int completedCount,
            int remainingMinutes,
            List<LearnPathResponse.Stop> lessons,
            String text) {
    }

    /**
     * A published class; {@code joined} when the student is already in it ("In My classes"); {@code bestAfterUnit} is
     * the Learn unit it is best taken after (the class's own setting, null when it has none).
     */
    public record ClassOption(
            Long id,
            String title,
            String description,
            String topicName,
            Integer bestAfterUnit,
            boolean joined,
            String text) {
    }

    public record Catalogue(List<Unit> units, List<ClassOption> classes, LearnPathResponse path) {

        public Optional<Unit> unit(Long id) {
            return units.stream().filter(unit -> unit.id().equals(id)).findFirst();
        }

        public Optional<ClassOption> classOption(Long id) {
            return classes.stream().filter(option -> option.id().equals(id)).findFirst();
        }
    }

    @Transactional(readOnly = true)
    public Catalogue forNewPlan(StudentProfile student) {
        var joined = Set.copyOf(enrolmentRepository.findClassIdsByStudentId(student.getId()));
        return catalogue(student, classCatalogueService.allCards(), joined);
    }

    /** Only optional (not yet joined) plan classes need catalogue cards; joined ones are already loaded from enrolments. */
    @Transactional(readOnly = true)
    public Catalogue forStoredPlan(StudentProfile student, Set<Long> optionalClassIds) {
        return catalogue(student, classCatalogueService.cardsFor(optionalClassIds), Set.of());
    }

    private Catalogue catalogue(StudentProfile student, List<ClassViews.ClassCard> cards, Set<Long> joined) {
        LearnPathResponse path = learnPathService.path(student);
        return new Catalogue(units(path), classes(cards, joined), path);
    }

    private static List<Unit> units(LearnPathResponse path) {
        List<Unit> units = new ArrayList<>();
        for (LearnPathResponse.Level level : path.levels()) {
            for (LearnPathResponse.Unit unit : level.units()) {
                // Lessons only: a bonus chest is optional, so the plan's weeks and Home's minutes leave it out.
                int remaining = unit.lessons().stream()
                        .filter(stop -> stop.state() != StopState.DONE && stop.kind() != LearnLessonKind.BONUS)
                        .mapToInt(LearnPathResponse.Stop::minutes)
                        .sum();
                String text = Stream.concat(
                                Stream.of(unit.title(), unit.titleZh()),
                                unit.lessons().stream().map(LearnPathResponse.Stop::title))
                        .collect(Collectors.joining(" · "));
                units.add(new Unit(unit.id(), unit.number(), level.level(), unit.title(), unit.titleZh(), unit.state(),
                        unit.lessonCount(), unit.completedCount(), remaining, unit.lessons(), text));
            }
        }
        return units;
    }

    private static List<ClassOption> classes(List<ClassViews.ClassCard> cards, Set<Long> joined) {
        return cards.stream()
                .map(card -> new ClassOption(card.id(), card.title(), card.description(),
                        card.topicName(), card.bestAfterUnit(), joined.contains(card.id()),
                        Stream.of(card.title(), card.description(), card.topicName())
                                .filter(Objects::nonNull)
                                .collect(Collectors.joining(" · "))))
                .toList();
    }
}
