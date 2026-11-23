package com.example.aichinese.service.classes;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.VideoProgress;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.repository.ClassActivityCompletionRepository;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.VideoProgressRepository;
import com.example.aichinese.service.QuizScoring;
import com.example.aichinese.service.classes.ClassContentSupport.ActivityKey;

import lombok.RequiredArgsConstructor;

/**
 * Progress in class lessons. A lesson's steps are its published activities and quizzes: a step is done when the
 * activity is finished or the quiz has a submitted attempt, and a lesson is completed when every step is done.
 * Classes give no XP.
 */
@Service
@RequiredArgsConstructor
public class ClassProgressService {

    private final ClassContentSupport content;
    private final ClassActivityCompletionRepository completionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final VideoProgressRepository videoProgressRepository;
    private final LessonProgressRepository lessonProgressRepository;

    /**
     * One lesson's progress for one student: its steps are its live {@code activities} and {@code quizzes}.
     *
     * @param completedActivityKeys activities of this lesson that the student finished
     * @param submittedByQuiz the student's submitted attempts grouped by quiz, newest first in each group
     * @param videoProgress the student's progress on {@code video}; null until they start it
     */
    public record LessonState(
            List<LessonActivity> activities,
            List<Quiz> quizzes,
            Set<ActivityKey> completedActivityKeys,
            Map<Long, List<QuizAttempt>> submittedByQuiz,
            RecordedLessonVideo video,
            VideoProgress videoProgress) {

        public int activitiesDone() {
            return (int) activities.stream().filter(this::finished).count();
        }

        public List<QuizAttempt> attemptsFor(Quiz quiz) {
            return submittedByQuiz.getOrDefault(quiz.getId(), List.of());
        }

        /** The quiz's submitted attempt with the best score, the newest of equal ones; empty before the first. */
        public Optional<QuizAttempt> bestAttempt(Quiz quiz) {
            // Newest first: an older attempt replaces the best only with a higher score.
            return attemptsFor(quiz).stream()
                    .reduce((best, older) -> attemptPercent(older) > attemptPercent(best) ? older : best);
        }

        public int stepsTotal() {
            return activities.size() + quizzes.size();
        }

        public int stepsDone() {
            return activitiesDone() + (int) quizzes.stream().filter(quiz -> !attemptsFor(quiz).isEmpty()).count();
        }

        public boolean completed() {
            return stepsTotal() > 0 && stepsDone() >= stepsTotal();
        }

        /** An activity finished, a quiz attempt submitted, or the video begun (which is not a step). */
        public boolean started() {
            return videoProgress != null || !submittedByQuiz.isEmpty() || activities.stream().anyMatch(this::finished);
        }

        public boolean videoWatched() {
            return videoProgress != null && videoProgress.isCompleted();
        }

        /** NOT_STARTED, IN_PROGRESS or COMPLETED. */
        public String status() {
            if (completed()) {
                return "COMPLETED";
            }
            return started() ? "IN_PROGRESS" : "NOT_STARTED";
        }

        /** Whether the student finished the activity (one of the lesson's). */
        public boolean finished(LessonActivity activity) {
            return completedActivityKeys.contains(ActivityKey.of(activity));
        }
    }

    @Transactional(readOnly = true)
    public LessonState lessonState(StudentProfile student, Lesson lesson) {
        return lessonStates(student, List.of(lesson)).get(lesson.getId());
    }

    /** Lesson states for one student, with queries batched across the lessons. */
    @Transactional(readOnly = true)
    public Map<Long, LessonState> lessonStates(StudentProfile student, Collection<Lesson> lessons) {
        if (lessons.isEmpty()) {
            return Map.of();
        }
        Long studentId = student.getId();
        List<Long> lessonIds = lessons.stream().map(Lesson::getId).toList();
        Map<Long, List<LessonActivity>> activities = content.activitiesByLesson(lessonIds);
        Map<Long, List<Quiz>> quizzes = content.quizzesByLesson(lessonIds);
        Map<Long, RecordedLessonVideo> videos = content.videoByLesson(lessonIds);
        Map<Long, Set<ActivityKey>> completions = completionRepository.findByStudentIdAndLessonIdIn(studentId, lessonIds)
                .stream().collect(Collectors.groupingBy(completion -> completion.getLesson().getId(),
                        Collectors.mapping(completion -> new ActivityKey(completion.getActivityType(), completion.getActivityId()),
                                Collectors.toSet())));
        Map<Long, Map<Long, List<QuizAttempt>>> attempts = quizAttemptRepository
                .findByStudentIdAndQuizLessonIdInAndSubmittedTrueOrderByCompletedAtDescIdDesc(studentId, lessonIds)
                .stream().collect(Collectors.groupingBy(attempt -> attempt.getQuiz().getLesson().getId(),
                        Collectors.groupingBy(attempt -> attempt.getQuiz().getId())));
        List<Long> videoIds = videos.values().stream().map(RecordedLessonVideo::getId).toList();
        Map<Long, VideoProgress> videoProgress = videoIds.isEmpty() ? Map.of()
                : videoProgressRepository.findByStudentIdAndRecordedVideoIdIn(studentId, videoIds).stream()
                        .collect(Collectors.toMap(watched -> watched.getRecordedVideo().getId(), watched -> watched));
        Map<Long, LessonState> states = new HashMap<>();
        for (Long lessonId : lessonIds) {
            RecordedLessonVideo video = videos.get(lessonId);
            states.put(lessonId, new LessonState(
                    activities.getOrDefault(lessonId, List.of()).stream().filter(ClassContentSupport::isLive).toList(),
                    quizzes.getOrDefault(lessonId, List.of()).stream().filter(ClassContentSupport::isLive).toList(),
                    completions.getOrDefault(lessonId, Set.of()),
                    attempts.getOrDefault(lessonId, Map.of()),
                    video,
                    video == null ? null : videoProgress.get(video.getId())));
        }
        return states;
    }

    /**
     * Keeps the stored lesson progress row in step with the derived state (other features read it) and returns that
     * state.
     */
    @Transactional
    public LessonState recordProgress(StudentProfile student, Lesson lesson) {
        LessonProgress progress = lessonProgressRepository.findByStudentIdAndLessonId(student.getId(), lesson.getId())
                .orElseGet(() -> {
                    LessonProgress created = new LessonProgress();
                    created.setStudent(student);
                    created.setLesson(lesson);
                    return created;
                });
        LessonState state = lessonState(student, lesson);
        LocalDateTime now = LocalDateTime.now();
        boolean completed = state.completed();
        if (completed != progress.isCompleted()) {
            // Revisiting a completed lesson keeps the moment it was completed.
            progress.setCompletedAt(completed ? now : null);
            progress.setCompleted(completed);
        }
        progress.setLastAccessedAt(now);
        lessonProgressRepository.save(progress);
        return state;
    }

    /** The percentage of a submitted quiz attempt. */
    static int attemptPercent(QuizAttempt attempt) {
        return QuizScoring.percentage(attempt.getScore(), attempt.getMaxScore());
    }
}
