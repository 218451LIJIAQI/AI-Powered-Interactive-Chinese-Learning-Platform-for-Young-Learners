package com.example.aichinese.service.learn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LearnUnit;
import com.example.aichinese.entity.enums.LearnLessonKind;

/**
 * One student's view of the published path: which stops are done, open or locked. Stops unlock in order: a lesson,
 * review or checkpoint opens when the required stop before it is done; a bonus chest opens with the stop after it
 * but is optional, so nothing waits for it. Level 2 therefore opens with the Level 1 checkpoint.
 */
public final class LearnPathModel {

    public enum StopState { LOCKED, AVAILABLE, DONE }

    public enum UnitState { LOCKED, ACTIVE, DONE }

    private final List<UnitNode> units;
    private final Map<Long, StopNode> stopsById = new LinkedHashMap<>();

    private LearnPathModel(List<UnitNode> units) {
        this.units = List.copyOf(units);
        units.forEach(unit -> unit.stops().forEach(stop -> stopsById.put(stop.lesson().getId(), stop)));
    }

    /**
     * @param publishedUnits units students can see, in path order
     * @param lessonsInOrder every stop of those units, in path order
     * @param completedLessonIds stops the student finished
     */
    public static LearnPathModel build(
            List<LearnUnit> publishedUnits,
            List<LearnLesson> lessonsInOrder,
            Set<Long> completedLessonIds) {
        Map<Long, List<LearnLesson>> byUnit = new LinkedHashMap<>();
        publishedUnits.forEach(unit -> byUnit.put(unit.getId(), new ArrayList<>()));
        lessonsInOrder.forEach(lesson -> {
            List<LearnLesson> list = byUnit.get(lesson.getUnit().getId());
            if (list != null) {
                list.add(lesson);
            }
        });

        List<UnitNode> units = new ArrayList<>();
        StopNode previousRequired = null;
        UnitNode previousUnit = null;
        for (LearnUnit unit : publishedUnits) {
            List<StopNode> stops = new ArrayList<>();
            List<LearnLesson> lessons = byUnit.get(unit.getId());
            int unitLessons = (int) lessons.stream()
                    .filter(lesson -> lesson.getLessonKind() != LearnLessonKind.CHECKPOINT)
                    .count();
            for (int index = 0; index < lessons.size(); index++) {
                LearnLesson lesson = lessons.get(index);
                boolean done = completedLessonIds.contains(lesson.getId());
                boolean open = previousRequired == null || previousRequired.state() == StopState.DONE;
                StopState state = done ? StopState.DONE : open ? StopState.AVAILABLE : StopState.LOCKED;
                StopNode stop = new StopNode(lesson, index + 1, unitLessons, state,
                        previousRequired == null ? null : previousRequired.lesson());
                stops.add(stop);
                if (lesson.getLessonKind() != LearnLessonKind.BONUS) {
                    previousRequired = stop;
                }
            }
            UnitNode node = new UnitNode(unit, stops, unitState(stops), previousUnit == null ? null : previousUnit.unit());
            units.add(node);
            previousUnit = node;
        }
        return new LearnPathModel(units);
    }

    private static UnitState unitState(List<StopNode> stops) {
        List<StopNode> required = stops.stream().filter(StopNode::required).toList();
        if (!required.isEmpty() && required.stream().allMatch(stop -> stop.state() == StopState.DONE)) {
            return UnitState.DONE;
        }
        boolean started = stops.stream().anyMatch(stop -> stop.state() != StopState.LOCKED);
        return started ? UnitState.ACTIVE : UnitState.LOCKED;
    }

    public List<UnitNode> units() {
        return units;
    }

    public Optional<StopNode> stop(Long lessonId) {
        return Optional.ofNullable(stopsById.get(lessonId));
    }

    public Optional<UnitNode> unitOf(Long lessonId) {
        return units.stream()
                .filter(unit -> unit.stops().stream().anyMatch(stop -> stop.lesson().getId().equals(lessonId)))
                .findFirst();
    }

    /** The next stop to do: the first open required stop, else the first open bonus chest. */
    public Optional<StopNode> next() {
        List<StopNode> open = stopsById.values().stream().filter(stop -> stop.state() == StopState.AVAILABLE).toList();
        return open.stream().filter(StopNode::required).findFirst().or(() -> open.stream().findFirst());
    }

    /** Lessons on the path (every stop except checkpoints) and how many of them are done. */
    public int lessonCount() {
        return (int) stopsById.values().stream().filter(StopNode::countsAsLesson).count();
    }

    public int completedLessonCount() {
        return (int) stopsById.values().stream()
                .filter(StopNode::countsAsLesson)
                .filter(stop -> stop.state() == StopState.DONE)
                .count();
    }

    /**
     * A stop on the path.
     *
     * @param position 1-based place in its unit
     * @param unitLessons how many lessons the unit has (its checkpoint aside)
     * @param unlockAfter the required stop that must be finished first, null for the first stop
     */
    public record StopNode(LearnLesson lesson, int position, int unitLessons, StopState state, LearnLesson unlockAfter) {

        public boolean required() {
            return lesson.getLessonKind() != LearnLessonKind.BONUS;
        }

        public boolean countsAsLesson() {
            return lesson.getLessonKind() != LearnLessonKind.CHECKPOINT;
        }
    }

    /** A unit with its stops; {@code previousUnit} must be finished before it opens. */
    public record UnitNode(LearnUnit unit, List<StopNode> stops, UnitState state, LearnUnit previousUnit) {

        public List<StopNode> lessons() {
            return stops.stream().filter(StopNode::countsAsLesson).toList();
        }

        public Optional<StopNode> checkpoint() {
            return stops.stream().filter(stop -> !stop.countsAsLesson()).findFirst();
        }
    }
}
