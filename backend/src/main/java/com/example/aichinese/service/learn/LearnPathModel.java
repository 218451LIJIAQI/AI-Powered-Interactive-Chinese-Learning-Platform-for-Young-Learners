package com.example.aichinese.service.learn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.aichinese.dto.learn.StopState;
import com.example.aichinese.dto.learn.UnitState;
import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LearnUnit;
import com.example.aichinese.entity.enums.LearnLessonKind;

/**
 * One student's view of the published path: which stops are done, open or locked. Stops unlock in order: a lesson,
 * review or checkpoint opens when the required stop before it is done; a bonus chest opens with the stop after it
 * but is optional, so nothing waits for it. Level 2 therefore opens with the Level 1 checkpoint.
 *
 * <p>What counts as a lesson is the same everywhere a student (or the admin editor) sees a count: a unit's lessons and
 * its review. A bonus chest is an optional extra on the road, a checkpoint closes a level; neither is numbered or
 * counted as a lesson, so a unit whose every lesson is done reads "7 of 7" even with its chest left closed.
 *
 * <p>A unit is done when its lessons are. The level checkpoint is stored with the last unit of its level but is the
 * gate of the level, not part of that unit: it opens once every lesson of the level is done, the unit is done (and
 * earns Unit Champion) by then, and the checkpoint stays the path's next step until it is passed.
 */
public final class LearnPathModel {

    private final List<UnitNode> units;
    private final Map<Long, StopNode> stopsById = new LinkedHashMap<>();

    private LearnPathModel(List<UnitNode> units) {
        this.units = List.copyOf(units);
        units.forEach(unit -> unit.stops().forEach(stop -> stopsById.put(stop.lesson().getId(), stop)));
    }

    /**
     * @param publishedUnits units students can see, in path order
     * @param lessonsInOrder the stops of the whole path, in path order (those of other units are left out)
     * @param completedLessonIds stops the student finished
     */
    public static LearnPathModel build(
            List<LearnUnit> publishedUnits,
            List<LearnLesson> lessonsInOrder,
            Set<Long> completedLessonIds) {
        Map<Long, List<LearnLesson>> byUnit = lessonsInOrder.stream()
                .collect(Collectors.groupingBy(lesson -> lesson.getUnit().getId()));

        List<UnitNode> units = new ArrayList<>();
        StopNode previousRequired = null;
        LearnUnit previousUnit = null;
        for (LearnUnit unit : publishedUnits) {
            List<StopNode> stops = new ArrayList<>();
            List<LearnLesson> lessons = byUnit.getOrDefault(unit.getId(), List.of());
            int lessonCount = 0;
            int completedCount = 0;
            for (LearnLesson lesson : lessons) {
                boolean done = completedLessonIds.contains(lesson.getId());
                if (lesson.getLessonKind().countsAsLesson()) {
                    lessonCount++;
                    if (done) {
                        completedCount++;
                    }
                }
                boolean open = previousRequired == null || previousRequired.state() == StopState.DONE;
                StopState state = done ? StopState.DONE : open ? StopState.AVAILABLE : StopState.LOCKED;
                StopNode stop = new StopNode(lesson, lessonCount, state,
                        previousRequired == null ? null : previousRequired.lesson());
                stops.add(stop);
                if (stop.required()) {
                    previousRequired = stop;
                }
            }
            units.add(new UnitNode(unit, stops, lessonCount, completedCount, previousUnit));
            previousUnit = unit;
        }
        return new LearnPathModel(units);
    }

    public List<UnitNode> units() {
        return units;
    }

    public Optional<StopNode> stop(Long lessonId) {
        return Optional.ofNullable(stopsById.get(lessonId));
    }

    public Optional<UnitNode> unit(Long unitId) {
        return units.stream().filter(node -> node.unit().getId().equals(unitId)).findFirst();
    }

    /** The next stop to do: the first open required stop, else the first open bonus chest. */
    public Optional<StopNode> next() {
        StopNode firstBonus = null;
        for (StopNode stop : stopsById.values()) {
            if (stop.state() == StopState.AVAILABLE) {
                if (stop.required()) {
                    return Optional.of(stop);
                }
                if (firstBonus == null) {
                    firstBonus = stop;
                }
            }
        }
        return Optional.ofNullable(firstBonus);
    }

    /**
     * A stop on the path.
     *
     * @param position the lesson's number in its unit ("Lesson 3 of 7"); a bonus chest or a checkpoint, which are not
     *        numbered, carries the number of the lesson before it
     * @param unlockAfter the required stop that must be finished first, null for the first stop
     */
    public record StopNode(LearnLesson lesson, int position, StopState state, LearnLesson unlockAfter) {

        public boolean required() {
            return lesson.getLessonKind() != LearnLessonKind.BONUS;
        }

        /** A lesson or the unit review; not a bonus chest or a checkpoint. */
        public boolean countsAsLesson() {
            return lesson.getLessonKind().countsAsLesson();
        }

        /** Drawn on its unit's road: every stop but the level checkpoint. */
        public boolean onRoad() {
            return lesson.getLessonKind() != LearnLessonKind.CHECKPOINT;
        }
    }

    /**
     * A unit with its stops; {@code previousUnit} must be finished before it opens. The last unit of a level also holds
     * the level's checkpoint, which is not part of its state.
     */
    public record UnitNode(LearnUnit unit, List<StopNode> stops, int lessonCount, int completedCount, LearnUnit previousUnit) {

        public UnitState state() {
            if (lessonCount > 0 && completedCount == lessonCount) {
                return UnitState.DONE;
            }
            boolean started = stops.stream().anyMatch(stop -> stop.onRoad() && stop.state() != StopState.LOCKED);
            return started ? UnitState.ACTIVE : UnitState.LOCKED;
        }

        /** The stops on the unit's road, in order: its lessons, bonus chests and review. */
        public List<StopNode> roadStops() {
            return stops.stream().filter(StopNode::onRoad).toList();
        }

        /** The checkpoint of the unit's level, held by its last unit: the gate to the next level. */
        public Optional<StopNode> checkpoint() {
            return stops.stream().filter(stop -> !stop.onRoad()).findFirst();
        }
    }
}
