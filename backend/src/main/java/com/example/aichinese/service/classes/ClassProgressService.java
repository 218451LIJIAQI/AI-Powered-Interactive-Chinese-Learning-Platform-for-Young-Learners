package com.example.aichinese.service.classes;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.ClassActivityCompletion;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.VideoProgress;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.enums.LessonMastery;
import com.example.aichinese.repository.ClassActivityCompletionRepository;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.VideoProgressRepository;
import com.example.aichinese.service.LessonMasteryCalculator;

/**
 * Progress in class lessons. A lesson's steps are its published activities and quizzes: a step is done when the
 * activity is finished or the quiz has a submitted attempt. A lesson is completed when every step is done, and
 * mastered when it is completed and the average of the best quiz scores is at least 80 %. Classes give no XP.
 */
@Service
public class ClassProgressService {

    private final ClassContentSupport content;
    private final ClassActivityCompletionRepository completionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final VideoProgressRepository videoProgressRepository;
    private final LessonProgressRepository lessonProgressRepository;

    public ClassProgressService(
            ClassContentSupport content,
            ClassActivityCompletionRepository completionRepository,
            QuizAttemptRepository quizAttemptRepository,
            VideoProgressRepository videoProgressRepository,
            LessonProgressRepository lessonProgressRepository) {
        this.content = content;
        this.completionRepository = completionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.videoProgressRepository = videoProgressRepository;
        this.lessonProgressRepository = lessonProgressRepository;
    }

    /** One lesson's progress for one student. */
    public record LessonState(
            Long lessonId,
            int activityCount,
            int activitiesDone,
            int quizCount,
            int quizzesDone,
            Integer bestQuizPercent,
            boolean videoWatched,
            boolean hasVideo,
            boolean started,
            LocalDateTime lastActivityAt) {

        public int stepsTotal() {
            return activityCount + quizCount;
        }

        public int stepsDone() {
            return activitiesDone + quizzesDone;
        }

        public boolean completed() {
            return stepsTotal() > 0 && stepsDone() >= stepsTotal();
        }

        public int percent() {
            return stepsTotal() == 0 ? 0 : stepsDone() * 100 / stepsTotal();
        }

        public LessonMastery mastery() {
            return LessonMasteryCalculator.mastery(started, completed(), bestQuizPercent);
        }
    }

    /** The parts of the lessons that count, read once and shared by every student of a computation. */
    public record LessonParts(
            Map<Long, List<LessonActivity>> activities,
            Map<Long, List<Quiz>> quizzes,
            Map<Long, RecordedLessonVideo> videos) {
    }

    public LessonParts liveParts(Collection<Lesson> lessons) {
        List<Long> lessonIds = lessons.stream().map(Lesson::getId).toList();
        Map<Long, List<LessonActivity>> activities = new HashMap<>();
        content.activitiesByLesson(lessonIds).forEach((lessonId, list) ->
                activities.put(lessonId, list.stream().filter(ClassContentSupport::isLive).toList()));
        Map<Long, List<Quiz>> quizzes = new HashMap<>();
        content.quizzesByLesson(lessonIds).forEach((lessonId, list) ->
                quizzes.put(lessonId, list.stream().filter(ClassContentSupport::isLive).toList()));
        return new LessonParts(activities, quizzes, content.videoByLesson(lessonIds));
    }

    @Transactional(readOnly = true)
    public Map<Long, LessonState> lessonStates(StudentProfile student, Collection<Lesson> lessons) {
        return lessonStates(List.of(student.getId()), lessons, liveParts(lessons)).getOrDefault(student.getId(), Map.of());
    }

    /** Lesson states per student (outer key) and lesson (inner key), with every query batched. */
    @Transactional(readOnly = true)
    public Map<Long, Map<Long, LessonState>> lessonStates(
            Collection<Long> studentIds,
            Collection<Lesson> lessons,
            LessonParts parts) {
        Map<Long, Map<Long, LessonState>> result = new HashMap<>();
        if (studentIds.isEmpty() || lessons.isEmpty()) {
            return result;
        }
        List<Long> lessonIds = lessons.stream().map(Lesson::getId).toList();
        Map<Long, List<ClassActivityCompletion>> completions = completionRepository
                .findByStudentIdInAndLessonIdIn(studentIds, lessonIds).stream()
                .collect(Collectors.groupingBy(completion -> completion.getStudent().getId()));
        Map<Long, List<QuizAttempt>> attempts = quizAttemptRepository
                .findByStudentIdInAndQuizLessonIdInAndSubmittedTrueOrderByCompletedAtDescIdDesc(studentIds, lessonIds).stream()
                .collect(Collectors.groupingBy(attempt -> attempt.getStudent().getId()));
        List<Long> videoIds = parts.videos().values().stream().map(RecordedLessonVideo::getId).toList();
        Map<Long, List<VideoProgress>> videoProgress = videoIds.isEmpty()
                ? Map.of()
                : videoProgressRepository.findByRecordedVideoIdIn(videoIds).stream()
                        .filter(progress -> studentIds.contains(progress.getStudent().getId()))
                        .collect(Collectors.groupingBy(progress -> progress.getStudent().getId()));
        for (Long studentId : studentIds) {
            Map<Long, LessonState> states = new HashMap<>();
            for (Lesson lesson : lessons) {
                states.put(lesson.getId(), state(
                        lesson,
                        parts,
                        completions.getOrDefault(studentId, List.of()),
                        attempts.getOrDefault(studentId, List.of()),
                        videoProgress.getOrDefault(studentId, List.of())));
            }
            result.put(studentId, states);
        }
        return result;
    }

    private static LessonState state(
            Lesson lesson,
            LessonParts parts,
            List<ClassActivityCompletion> completions,
            List<QuizAttempt> attempts,
            List<VideoProgress> videoProgress) {
        List<LessonActivity> activities = parts.activities().getOrDefault(lesson.getId(), List.of());
        List<Quiz> quizzes = parts.quizzes().getOrDefault(lesson.getId(), List.of());
        RecordedLessonVideo video = parts.videos().get(lesson.getId());

        Set<String> done = new HashSet<>();
        LocalDateTime last = null;
        for (ClassActivityCompletion completion : completions) {
            if (completion.getLesson().getId().equals(lesson.getId())) {
                done.add(completion.getActivityType() + "#" + completion.getActivityId());
                last = ClassContentSupport.latest(last, completion.getCompletedAt());
            }
        }
        int activitiesDone = (int) activities.stream()
                .filter(activity -> done.contains(ClassContentSupport.typeOf(activity) + "#" + activity.getId()))
                .count();

        Set<Long> liveQuizIds = quizzes.stream().map(Quiz::getId).collect(Collectors.toSet());
        Map<Long, Integer> bestByQuiz = new HashMap<>();
        boolean anyAttempt = false;
        for (QuizAttempt attempt : attempts) {
            if (!attempt.getQuiz().getLesson().getId().equals(lesson.getId())) {
                continue;
            }
            anyAttempt = true;
            last = ClassContentSupport.latest(last, attempt.getCompletedAt());
            Integer percent = LessonMasteryCalculator.attemptPercentage(attempt);
            if (percent != null && liveQuizIds.contains(attempt.getQuiz().getId())) {
                bestByQuiz.merge(attempt.getQuiz().getId(), percent, Math::max);
            }
        }
        Integer bestQuizPercent = average(bestByQuiz.values());

        boolean videoWatched = false;
        boolean videoStarted = false;
        if (video != null) {
            for (VideoProgress progress : videoProgress) {
                if (progress.getRecordedVideo().getId().equals(video.getId())) {
                    videoStarted = true;
                    videoWatched = progress.isCompleted();
                    last = ClassContentSupport.latest(last, progress.getLastWatchedAt());
                }
            }
        }
        boolean started = activitiesDone > 0 || anyAttempt || videoStarted;
        return new LessonState(
                lesson.getId(),
                activities.size(),
                activitiesDone,
                quizzes.size(),
                bestByQuiz.size(),
                bestQuizPercent,
                videoWatched,
                video != null,
                started,
                last);
    }

    /** Keeps the stored lesson progress row in step with the derived state (other features read it). */
    @Transactional
    public void recordProgress(StudentProfile student, Lesson lesson) {
        LessonState state = lessonStates(student, List.of(lesson)).get(lesson.getId());
        LessonProgress progress = lessonProgressRepository.findByStudentIdAndLessonId(student.getId(), lesson.getId())
                .orElseGet(() -> {
                    LessonProgress created = new LessonProgress();
                    created.setStudent(student);
                    created.setLesson(lesson);
                    return created;
                });
        progress.setCompleted(state != null && state.completed());
        progress.setLastAccessedAt(LocalDateTime.now());
        lessonProgressRepository.save(progress);
    }

    /** Class progress: completed lessons out of the lessons students can open. */
    public static int classPercent(int completedLessons, int lessonCount) {
        return lessonCount == 0 ? 0 : completedLessons * 100 / lessonCount;
    }

    /** The percentage of a submitted quiz attempt (0 when it can't be scored). */
    static int attemptPercent(QuizAttempt attempt) {
        return Objects.requireNonNullElse(LessonMasteryCalculator.attemptPercentage(attempt), 0);
    }

    /** The average of percentages, rounded down like every quiz percentage; null when there are none. */
    static Integer average(Collection<Integer> percents) {
        return percents.isEmpty() ? null : (int) Math.floor(percents.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    public static String status(LessonState state) {
        return state == null ? LessonMastery.NOT_STARTED.name() : state.mastery().name();
    }

    /** "TYPE#id" of every activity of the lesson the student finished. */
    @Transactional(readOnly = true)
    public List<String> completedActivityKeys(StudentProfile student, Lesson lesson) {
        return completionRepository.findByStudentIdAndLessonIdIn(student.getId(), List.of(lesson.getId())).stream()
                .map(completion -> completion.getActivityType() + "#" + completion.getActivityId())
                .toList();
    }
}
